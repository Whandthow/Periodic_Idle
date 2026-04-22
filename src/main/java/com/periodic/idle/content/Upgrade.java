package com.periodic.idle.content;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "upgrades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Upgrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    private int tier;

    @Column(nullable = false)
    private String effectType;
    // ENERGY_MULT, GENERATOR_MULT, COST_DISCOUNT,
    // COLLAPSE_BONUS, BREAK_INFINITY, UNLOCK_EXCHANGE

    private double effectValue;

    @ManyToOne
    @JoinColumn(name = "target_generator_id")
    private Generator targetGenerator;

    @ManyToOne
    @JoinColumn(name = "cost_resource_id")
    private Resource costResource;

    private double costNumber;
    private long costExponent;
    private double costMultiplier;

    private int maxLevel;

    @Column(name = "unlock_core_tier", nullable = false)
    private int unlockCoreTier;

    @ManyToOne
    @JoinColumn(name = "required_upgrade_id")
    private Upgrade requiredUpgrade;
}