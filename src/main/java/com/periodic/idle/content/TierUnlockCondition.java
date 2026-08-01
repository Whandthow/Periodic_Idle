package com.periodic.idle.content;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Data-driven progressive disclosure: одна OR-умова розблокування тіру.
 * Тір розблокований, якщо для будь-якого рядка з його tier ресурс досяг min_log10.
 */
@Entity
@Table(name = "tier_unlock_conditions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TierUnlockCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int tier;

    @ManyToOne
    @JoinColumn(name = "resource_id")
    private Resource resource;

    @Column(name = "min_log10", nullable = false)
    private double minLog10;
}
