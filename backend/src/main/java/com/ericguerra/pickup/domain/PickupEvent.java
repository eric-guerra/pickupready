package com.ericguerra.pickup.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "pickup_events")
public class PickupEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_no")
    private PickupOrder order;

    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 24) private PickupStatus from;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 24) private PickupStatus to;
    @Column(nullable = false, length = 60) private String actor;
    @Column(length = 200) private String note;
    @Column(nullable = false) private Instant at;

    protected PickupEvent() {}

    PickupEvent(PickupOrder order, PickupStatus from, PickupStatus to, String actor, String note, Instant at) {
        this.order = order;
        this.from = from;
        this.to = to;
        this.actor = actor;
        this.note = note;
        this.at = at;
    }

    public PickupStatus getFrom() { return from; }
    public PickupStatus getTo() { return to; }
    public String getActor() { return actor; }
    public String getNote() { return note; }
    public Instant getAt() { return at; }
}
