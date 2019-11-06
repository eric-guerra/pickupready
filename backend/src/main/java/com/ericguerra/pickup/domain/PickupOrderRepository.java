package com.ericguerra.pickup.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PickupOrderRepository extends JpaRepository<PickupOrder, String> {
    List<PickupOrder> findByStoreIdAndStatusInOrderByReadyByAsc(String storeId, Collection<PickupStatus> statuses);

    @Query("select o from PickupOrder o where o.status = com.ericguerra.pickup.domain.PickupStatus.READY and o.holdUntil < :now")
    List<PickupOrder> findExpiredHolds(@Param("now") Instant now);
}
