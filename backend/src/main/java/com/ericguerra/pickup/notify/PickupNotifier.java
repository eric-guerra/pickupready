package com.ericguerra.pickup.notify;

import com.ericguerra.pickup.domain.PickupOrder;

/** Pushes order changes to the store board and the customer. Redis-backed in prod, recorded in tests. */
public interface PickupNotifier {
    void orderChanged(PickupOrder order);

    void customerMessage(PickupOrder order, String message);
}
