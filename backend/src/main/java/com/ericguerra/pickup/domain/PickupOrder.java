package com.ericguerra.pickup.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pickup_orders")
public class PickupOrder {
    @Id
    @Column(name = "order_no", length = 20)
    private String orderNo;

    @Column(name = "store_id", nullable = false, length = 10)
    private String storeId;

    @Column(name = "customer_name", nullable = false, length = 80)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PickupStatus status = PickupStatus.PLACED;

    /** SHA-256 of the 6-digit code the customer shows at handoff; the code itself is never stored. */
    @JsonIgnore
    @Column(name = "pickup_code_hash", nullable = false, length = 64)
    private String pickupCodeHash;

    @JsonIgnore
    @Column(name = "code_attempts", nullable = false)
    private int codeAttempts;

    @Column(name = "assigned_to", length = 60)
    private String assignedTo;

    @Column(name = "staging_location", length = 20)
    private String stagingLocation;

    @Column(name = "arrival_spot", length = 20)
    private String arrivalSpot;

    @Column(name = "placed_at", nullable = false) private Instant placedAt;
    @Column(name = "ready_by", nullable = false) private Instant readyBy;
    @Column(name = "ready_at") private Instant readyAt;
    @Column(name = "arrived_at") private Instant arrivedAt;
    @Column(name = "picked_up_at") private Instant pickedUpAt;
    @Column(name = "hold_until") private Instant holdUntil;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @OrderBy("id")
    private List<PickupLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @OrderBy("id")
    private List<PickupEvent> events = new ArrayList<>();

    protected PickupOrder() {}

    public PickupOrder(String orderNo, String storeId, String customerName, String customerPhone, String pickupCodeHash, Instant placedAt, Instant readyBy) {
        this.orderNo = orderNo;
        this.storeId = storeId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.pickupCodeHash = pickupCodeHash;
        this.placedAt = placedAt;
        this.readyBy = readyBy;
        events.add(new PickupEvent(this, null, PickupStatus.PLACED, "system", "Order placed", placedAt));
    }

    public void addLine(String sku, String name, int qty, String aisle) {
        lines.add(new PickupLine(this, sku, name, qty, aisle));
    }

    public void transition(PickupStatus next, String actor, String note, Instant at) {
        if (!status.canMoveTo(next)) throw new IllegalStateException("order " + orderNo + " is " + status + "; cannot move to " + next);
        events.add(new PickupEvent(this, status, next, actor, note, at));
        status = next;
    }

    public PickupLine line(Long lineId) {
        return lines.stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no line " + lineId + " on " + orderNo));
    }

    public boolean allLinesResolved() {
        return lines.stream().allMatch(PickupLine::isResolved);
    }

    public boolean overdue(Instant now) {
        return status.ordinal() < PickupStatus.READY.ordinal() && status != PickupStatus.CANCELLED && now.isAfter(readyBy);
    }

    public String getOrderNo() { return orderNo; }
    public String getStoreId() { return storeId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public PickupStatus getStatus() { return status; }
    @JsonIgnore public String getPickupCodeHash() { return pickupCodeHash; }
    @JsonIgnore public int getCodeAttempts() { return codeAttempts; }
    public void incrementCodeAttempts() { codeAttempts++; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String a) { assignedTo = a; }
    public String getStagingLocation() { return stagingLocation; }
    public void setStagingLocation(String s) { stagingLocation = s; }
    public String getArrivalSpot() { return arrivalSpot; }
    public void setArrivalSpot(String s) { arrivalSpot = s; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getReadyBy() { return readyBy; }
    public Instant getReadyAt() { return readyAt; }
    public void setReadyAt(Instant t) { readyAt = t; }
    public Instant getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(Instant t) { arrivedAt = t; }
    public Instant getPickedUpAt() { return pickedUpAt; }
    public void setPickedUpAt(Instant t) { pickedUpAt = t; }
    public Instant getHoldUntil() { return holdUntil; }
    public void setHoldUntil(Instant t) { holdUntil = t; }
    public List<PickupLine> getLines() { return lines; }
    public List<PickupEvent> getEvents() { return events; }
    public long getVersion() { return version; }
}
