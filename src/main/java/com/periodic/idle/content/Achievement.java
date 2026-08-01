package com.periodic.idle.content;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Досягнення: одноразова умова над станом save. Read-only контент, посіяний Flyway.
 * conditionType визначає, як інтерпретувати resource/threshold (AchievementService.isSatisfied):
 * RESOURCE_LOG10 (resource обов'язковий), MATTER_COLLAPSES, ELEMENTS_SYNTHESIZED, BROKEN_INFINITY.
 */
@Entity
@Table(name = "achievements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "condition_type", nullable = false)
    private String conditionType;

    @ManyToOne
    @JoinColumn(name = "resource_id")
    private Resource resource;

    @Column(nullable = false)
    private double threshold;
}
