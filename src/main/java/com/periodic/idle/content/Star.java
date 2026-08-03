package com.periodic.idle.content;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Тір 4: зоря головної послідовності. Read-only контент, посіяний Flyway.
 * Пасивний фузійний рецепт: {@code fuelHPerEvent} атомів Гідрогену (Тір 2) на
 * подію дають {@code outputHePerEvent} атомів Гелію, {@code eventsPerSecPerLevel}
 * подій за секунду за рівень зорі — StarService.tick() рахує це так само, як
 * GameEngine рахує rate генератора, але для елементів, не E.
 */
@Entity
@Table(name = "stars")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Star {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int tier;

    @Column(name = "base_cost_hydrogen", nullable = false)
    private long baseCostHydrogen;

    @Column(name = "cost_multiplier", nullable = false)
    private double costMultiplier;

    @Column(name = "fuel_h_per_event", nullable = false)
    private long fuelHPerEvent;

    @Column(name = "output_he_per_event", nullable = false)
    private long outputHePerEvent;

    @Column(name = "events_per_sec_per_level", nullable = false)
    private double eventsPerSecPerLevel;
}
