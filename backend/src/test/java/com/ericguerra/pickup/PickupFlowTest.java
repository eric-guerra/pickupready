package com.ericguerra.pickup;

import com.ericguerra.pickup.domain.PickupLine;
import com.ericguerra.pickup.domain.PickupOrder;
import com.ericguerra.pickup.domain.PickupStatus;
import com.ericguerra.pickup.service.PickupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PickupFlowTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired PickupService service;
    @Autowired TestConfig.TestClock clock;
    @Autowired TestConfig.RecordingNotifier notifier;

    String orderNo;
    String code;

    @BeforeEach
    void place() {
        orderNo = "W" + (100000 + SEQ.incrementAndGet());
        var placed = service.place(orderNo, "S042", "Dana Ruiz", "+15125550100", List.of(
                new PickupService.NewLine("SKU-MILK", "Whole milk 1gal", 2, "A12"),
                new PickupService.NewLine("SKU-BREAD", "Sourdough loaf", 1, "B03")));
        code = placed.pickupCode();
    }

    private Long line(PickupOrder o, String sku) {
        return o.getLines().stream().filter(l -> l.getSku().equals(sku)).findFirst().orElseThrow().getId();
    }

    @Test
    void happyPathFromPlacedToPickedUp() {
        PickupOrder o = service.claim(orderNo, "alex");
        assertThat(o.getStatus()).isEqualTo(PickupStatus.PICKING);
        service.recordPick(orderNo, line(o, "SKU-MILK"), 2, "alex");
        service.recordPick(orderNo, line(o, "SKU-BREAD"), 1, "alex");
        o = service.stage(orderNo, "COOLER-2", "alex");
        assertThat(o.getStatus()).isEqualTo(PickupStatus.READY);
        assertThat(o.getHoldUntil()).isEqualTo(o.getReadyAt().plus(Duration.ofHours(72)));

        service.checkIn(orderNo, "7");
        o = service.handOff(orderNo, code, "alex");
        assertThat(o.getStatus()).isEqualTo(PickupStatus.PICKED_UP);
        assertThat(o.getEvents()).extracting(e -> e.getTo()).containsExactly(
                PickupStatus.PLACED, PickupStatus.ACCEPTED, PickupStatus.PICKING, PickupStatus.READY,
                PickupStatus.CUSTOMER_ARRIVED, PickupStatus.PICKED_UP);
        assertThat(notifier.messages).anyMatch(m -> m.contains("is ready"));
    }

    @Test
    void cannotStageWithUnpickedLines() {
        PickupOrder o = service.claim(orderNo, "alex");
        service.recordPick(orderNo, line(o, "SKU-MILK"), 2, "alex");
        assertThatThrownBy(() -> service.stage(orderNo, "SHELF-1", "alex"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("every line");
    }

    @Test
    void shortPickWaitsForCustomerDecision() {
        PickupOrder o = service.claim(orderNo, "alex");
        Long bread = line(o, "SKU-BREAD");
        service.recordPick(orderNo, line(o, "SKU-MILK"), 2, "alex");
        service.recordPick(orderNo, bread, 0, "alex");
        o = service.proposeSubstitute(orderNo, bread, "SKU-RYE", "Rye loaf", "alex");
        assertThat(o.getStatus()).isEqualTo(PickupStatus.AWAITING_SUBSTITUTION);
        assertThatThrownBy(() -> service.stage(orderNo, "SHELF-1", "alex")).isInstanceOf(IllegalStateException.class);
        assertThat(notifier.messages).anyMatch(m -> m.contains("Rye loaf"));

        o = service.decideSubstitute(orderNo, bread, true);
        assertThat(o.getStatus()).isEqualTo(PickupStatus.PICKING);
        assertThat(o.line(bread).getSubstitutionState()).isEqualTo(PickupLine.SubstitutionState.APPROVED);
        assertThat(service.stage(orderNo, "SHELF-1", "alex").getStatus()).isEqualTo(PickupStatus.READY);
    }

    @Test
    void wrongCodesLockTheOrder() {
        PickupOrder o = service.claim(orderNo, "alex");
        service.recordPick(orderNo, line(o, "SKU-MILK"), 2, "alex");
        service.recordPick(orderNo, line(o, "SKU-BREAD"), 1, "alex");
        service.stage(orderNo, "SHELF-1", "alex");
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = PickupService.MAX_CODE_ATTEMPTS - 1; i >= 0; i--) {
            int remaining = i;
            assertThatThrownBy(() -> service.handOff(orderNo, wrong, "alex"))
                    .isInstanceOfSatisfying(PickupService.WrongCodeException.class, e -> assertThat(e.remaining).isEqualTo(remaining));
        }
        assertThatThrownBy(() -> service.handOff(orderNo, code, "alex")).isInstanceOf(PickupService.CodeLockedException.class);
        assertThat(service.get(orderNo).getStatus()).isEqualTo(PickupStatus.READY);
    }

    @Test
    void noShowExpiresAfterHoldWindow() {
        PickupOrder o = service.claim(orderNo, "alex");
        service.recordPick(orderNo, line(o, "SKU-MILK"), 2, "alex");
        service.recordPick(orderNo, line(o, "SKU-BREAD"), 1, "alex");
        service.stage(orderNo, "SHELF-1", "alex");
        clock.advance(Duration.ofHours(71));
        assertThat(service.expireHolds()).isZero();
        clock.advance(Duration.ofHours(2));
        assertThat(service.expireHolds()).isEqualTo(1);
        assertThat(service.get(orderNo).getStatus()).isEqualTo(PickupStatus.EXPIRED);
        assertThat(service.board("S042")).isEmpty();
    }

    @Test
    void boardIsOrderedByPromiseTimeAndOverdueIsFlagged() {
        clock.advance(Duration.ofMinutes(30));
        String later = orderNo + "B";
        service.place(later, "S042", "Kim", null, List.of(new PickupService.NewLine("SKU-EGG", "Eggs", 1, "A01")));
        assertThat(service.board("S042")).extracting(PickupOrder::getOrderNo).containsExactly(orderNo, later);
        clock.advance(Duration.ofMinutes(100));
        assertThat(service.get(orderNo).overdue(clock.instant())).isTrue();
        assertThat(service.get(later).overdue(clock.instant())).isFalse();
    }

    @Test
    void illegalTransitionsAreRejected() {
        assertThatThrownBy(() -> service.checkIn(orderNo, null)).isInstanceOf(IllegalStateException.class);
        service.cancel(orderNo, "manager", "Customer called");
        assertThatThrownBy(() -> service.claim(orderNo, "alex")).isInstanceOf(IllegalStateException.class);
    }
}
