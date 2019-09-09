package com.ericguerra.pickup.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Store-side lifecycle of a buy-online-pickup-in-store order.
 *
 *   PLACED → ACCEPTED → PICKING ⇄ AWAITING_SUBSTITUTION → READY → CUSTOMER_ARRIVED → PICKED_UP
 *   READY → EXPIRED (no-show)        any pre-handoff state → CANCELLED
 */
public enum PickupStatus {
    PLACED, ACCEPTED, PICKING, AWAITING_SUBSTITUTION, READY, CUSTOMER_ARRIVED, PICKED_UP, EXPIRED, CANCELLED;

    private static final Map<PickupStatus, Set<PickupStatus>> NEXT = Map.of(
            PLACED, EnumSet.of(ACCEPTED, CANCELLED),
            ACCEPTED, EnumSet.of(PICKING, CANCELLED),
            PICKING, EnumSet.of(AWAITING_SUBSTITUTION, READY, CANCELLED),
            AWAITING_SUBSTITUTION, EnumSet.of(PICKING, READY, CANCELLED),
            READY, EnumSet.of(CUSTOMER_ARRIVED, PICKED_UP, EXPIRED, CANCELLED),
            CUSTOMER_ARRIVED, EnumSet.of(PICKED_UP, READY),
            PICKED_UP, EnumSet.noneOf(PickupStatus.class),
            EXPIRED, EnumSet.noneOf(PickupStatus.class),
            CANCELLED, EnumSet.noneOf(PickupStatus.class));

    public boolean canMoveTo(PickupStatus next) {
        return NEXT.get(this).contains(next);
    }

    public boolean isOpen() {
        return !NEXT.get(this).isEmpty();
    }
}
