package com.ericguerra.pickup.service;

import com.ericguerra.pickup.domain.*;
import com.ericguerra.pickup.notify.PickupNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
public class PickupService {
    public static final int MAX_CODE_ATTEMPTS = 5;

    public record NewLine(String sku, String name, int qty, String aisle) {}
    public record Placed(PickupOrder order, String pickupCode) {}

    private final PickupOrderRepository orders;
    private final PickupNotifier notifier;
    private final Clock clock;
    private final Duration promise;
    private final Duration hold;

    public PickupService(PickupOrderRepository orders, PickupNotifier notifier, Clock clock,
                         @Value("${pickup.promise-minutes:120}") long promiseMinutes,
                         @Value("${pickup.hold-hours:72}") long holdHours) {
        this.orders = orders;
        this.notifier = notifier;
        this.clock = clock;
        this.promise = Duration.ofMinutes(promiseMinutes);
        this.hold = Duration.ofHours(holdHours);
    }

    public Placed place(String orderNo, String storeId, String customerName, String phone, List<NewLine> lines) {
        if (lines.isEmpty()) throw new IllegalArgumentException("order needs at least one line");
        if (orders.existsById(orderNo)) throw new IllegalStateException("order " + orderNo + " already exists");
        Instant now = clock.instant();
        String code = PickupCodes.generate();
        PickupOrder o = new PickupOrder(orderNo, storeId, customerName, phone, PickupCodes.hash(orderNo, code), now, now.plus(promise));
        lines.forEach(l -> o.addLine(l.sku(), l.name(), l.qty(), l.aisle()));
        orders.save(o);
        notifier.orderChanged(o);
        return new Placed(o, code);
    }

    @Transactional(readOnly = true)
    public PickupOrder get(String orderNo) {
        return orders.findById(orderNo).orElseThrow(() -> new NoSuchElementException("order " + orderNo));
    }

    @Transactional(readOnly = true)
    public List<PickupOrder> board(String storeId) {
        return orders.findByStoreIdAndStatusInOrderByReadyByAsc(storeId,
                EnumSet.of(PickupStatus.PLACED, PickupStatus.ACCEPTED, PickupStatus.PICKING,
                        PickupStatus.AWAITING_SUBSTITUTION, PickupStatus.READY, PickupStatus.CUSTOMER_ARRIVED));
    }

    public PickupOrder claim(String orderNo, String associate) {
        PickupOrder o = get(orderNo);
        o.setAssignedTo(associate);
        o.transition(PickupStatus.ACCEPTED, associate, "Claimed", clock.instant());
        o.transition(PickupStatus.PICKING, associate, null, clock.instant());
        return changed(o);
    }

    public PickupOrder recordPick(String orderNo, Long lineId, int found, String associate) {
        PickupOrder o = get(orderNo);
        requireStatus(o, PickupStatus.PICKING, PickupStatus.AWAITING_SUBSTITUTION);
        o.line(lineId).recordPick(found);
        return changed(o);
    }

    public PickupOrder proposeSubstitute(String orderNo, Long lineId, String sku, String name, String associate) {
        PickupOrder o = get(orderNo);
        requireStatus(o, PickupStatus.PICKING, PickupStatus.AWAITING_SUBSTITUTION);
        PickupLine line = o.line(lineId);
        line.proposeSubstitute(sku, name);
        if (o.getStatus() == PickupStatus.PICKING)
            o.transition(PickupStatus.AWAITING_SUBSTITUTION, associate, "Substitute offered for " + line.getSku(), clock.instant());
        notifier.customerMessage(o, "We're out of " + line.getName() + ". Reply or tap the link to accept " + name + " instead.");
        return changed(o);
    }

    public PickupOrder decideSubstitute(String orderNo, Long lineId, boolean approved) {
        PickupOrder o = get(orderNo);
        requireStatus(o, PickupStatus.AWAITING_SUBSTITUTION);
        o.line(lineId).decide(approved);
        boolean stillWaiting = o.getLines().stream().anyMatch(l -> l.getSubstitutionState() == PickupLine.SubstitutionState.PROPOSED);
        if (!stillWaiting) o.transition(PickupStatus.PICKING, "customer", approved ? "Substitute approved" : "Substitute declined", clock.instant());
        return changed(o);
    }

    public PickupOrder stage(String orderNo, String location, String associate) {
        PickupOrder o = get(orderNo);
        requireStatus(o, PickupStatus.PICKING);
        if (!o.allLinesResolved()) throw new IllegalStateException("every line must be picked or resolved before staging");
        if (o.getLines().stream().allMatch(l -> l.getPickedQty() == 0 && l.getSubstitutionState() != PickupLine.SubstitutionState.APPROVED))
            throw new IllegalStateException("nothing was found; cancel the order instead");
        Instant now = clock.instant();
        o.setStagingLocation(location);
        o.setReadyAt(now);
        o.setHoldUntil(now.plus(hold));
        o.transition(PickupStatus.READY, associate, "Staged at " + location, now);
        notifier.customerMessage(o, "Your order " + orderNo + " is ready. Tap \"I'm here\" when you arrive.");
        return changed(o);
    }

    public PickupOrder checkIn(String orderNo, String spot) {
        PickupOrder o = get(orderNo);
        requireStatus(o, PickupStatus.READY);
        o.setArrivalSpot(spot);
        o.setArrivedAt(clock.instant());
        o.transition(PickupStatus.CUSTOMER_ARRIVED, "customer", spot == null ? "At the counter" : "Parking spot " + spot, clock.instant());
        return changed(o);
    }

    /** Handoff requires the customer's code; too many bad tries locks the order for a manager. */
    @Transactional(noRollbackFor = WrongCodeException.class)
    public PickupOrder handOff(String orderNo, String code, String associate) {
        PickupOrder o = get(orderNo);
        requireStatus(o, PickupStatus.READY, PickupStatus.CUSTOMER_ARRIVED);
        if (o.getCodeAttempts() >= MAX_CODE_ATTEMPTS) throw new CodeLockedException(orderNo);
        if (!PickupCodes.matches(orderNo, code, o.getPickupCodeHash())) {
            o.incrementCodeAttempts();
            orders.save(o);
            throw new WrongCodeException(orderNo, MAX_CODE_ATTEMPTS - o.getCodeAttempts());
        }
        o.setPickedUpAt(clock.instant());
        o.transition(PickupStatus.PICKED_UP, associate, "Handed off", clock.instant());
        return changed(o);
    }

    public PickupOrder cancel(String orderNo, String actor, String reason) {
        PickupOrder o = get(orderNo);
        o.transition(PickupStatus.CANCELLED, actor, reason, clock.instant());
        notifier.customerMessage(o, "Your order " + orderNo + " was cancelled: " + reason);
        return changed(o);
    }

    /** No-shows past the hold window go back to the shelf. */
    @Scheduled(fixedDelayString = "${pickup.expiry-sweep-ms:300000}")
    public int expireHolds() {
        List<PickupOrder> stale = orders.findExpiredHolds(clock.instant());
        for (PickupOrder o : stale) {
            o.transition(PickupStatus.EXPIRED, "system", "Hold window passed", clock.instant());
            notifier.customerMessage(o, "We held order " + o.getOrderNo() + " as long as we could; it has been returned and refunded.");
            changed(o);
        }
        return stale.size();
    }

    private PickupOrder changed(PickupOrder o) {
        PickupOrder saved = orders.save(o);
        notifier.orderChanged(saved);
        return saved;
    }

    private static void requireStatus(PickupOrder o, PickupStatus... allowed) {
        for (PickupStatus s : allowed) if (o.getStatus() == s) return;
        throw new IllegalStateException("order " + o.getOrderNo() + " is " + o.getStatus());
    }

    public static class WrongCodeException extends RuntimeException {
        public final int remaining;
        WrongCodeException(String orderNo, int remaining) {
            super("wrong pickup code for " + orderNo + "; " + remaining + " attempts left");
            this.remaining = remaining;
        }
    }

    public static class CodeLockedException extends RuntimeException {
        CodeLockedException(String orderNo) {
            super("too many wrong codes for " + orderNo + "; a manager must verify ID");
        }
    }
}
