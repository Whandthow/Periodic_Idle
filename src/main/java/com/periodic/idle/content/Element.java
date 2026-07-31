package com.periodic.idle.content;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Тір 2: хімічний елемент періодичної таблиці. Read-only контент, посіяний Flyway.
 * Рецепт синтезу — costProtons/costNeutrons/costElectrons частинок Tier 1.
 */
@Entity
@Table(name = "elements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Element {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "atomic_number", nullable = false, unique = true)
    private int atomicNumber;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Column(name = "atomic_weight", nullable = false)
    private double atomicWeight;

    @Column(nullable = false)
    private int period;

    @Column(name = "group_number", nullable = false)
    private int groupNumber;

    /** Розподіл електронів по оболонках (K,L,M,...) як CSV, напр. "2,8,11,2". */
    @Column(name = "shell_config", nullable = false)
    private String shellConfig;

    @Column(name = "cost_protons", nullable = false)
    private long costProtons;

    @Column(name = "cost_neutrons", nullable = false)
    private long costNeutrons;

    @Column(name = "cost_electrons", nullable = false)
    private long costElectrons;
}
