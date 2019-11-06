package com.ericguerra.pickup.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * One ordered item. Picking records how many were found; a short pick can propose a substitute,
 * which the customer approves or declines from a link on their phone.
 */
@Entity
@Table(name = "pickup_lines")
public class PickupLine {
    public enum SubstitutionState { NONE, PROPOSED, APPROVED, DECLINED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_no")
    private PickupOrder order;

    @Column(nullable = false, length = 40) private String sku;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false) private int qty;
    @Column(length = 10) private String aisle;
    @Column(name = "picked_qty") private Integer pickedQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "substitution_state", nullable = false, length = 10)
    private SubstitutionState substitutionState = SubstitutionState.NONE;

    @Column(name = "substitute_sku", length = 40) private String substituteSku;
    @Column(name = "substitute_name", length = 120) private String substituteName;

    protected PickupLine() {}

    PickupLine(PickupOrder order, String sku, String name, int qty, String aisle) {
        this.order = order;
        this.sku = sku;
        this.name = name;
        this.qty = qty;
        this.aisle = aisle;
    }

    public void recordPick(int found) {
        if (found < 0 || found > qty) throw new IllegalArgumentException("picked " + found + " of " + qty);
        this.pickedQty = found;
    }

    public void proposeSubstitute(String sku, String name) {
        if (pickedQty == null || pickedQty >= qty) throw new IllegalStateException("only short lines can be substituted");
        this.substituteSku = sku;
        this.substituteName = name;
        this.substitutionState = SubstitutionState.PROPOSED;
    }

    public void decide(boolean approved) {
        if (substitutionState != SubstitutionState.PROPOSED) throw new IllegalStateException("no substitution pending");
        substitutionState = approved ? SubstitutionState.APPROVED : SubstitutionState.DECLINED;
    }

    /** Picked in full, or short with the substitution question answered (or never asked). */
    public boolean isResolved() {
        if (pickedQty == null) return false;
        return pickedQty == qty || substitutionState != SubstitutionState.PROPOSED;
    }

    public boolean isShort() {
        return pickedQty != null && pickedQty < qty;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public int getQty() { return qty; }
    public String getAisle() { return aisle; }
    public Integer getPickedQty() { return pickedQty; }
    public SubstitutionState getSubstitutionState() { return substitutionState; }
    public String getSubstituteSku() { return substituteSku; }
    public String getSubstituteName() { return substituteName; }
}
