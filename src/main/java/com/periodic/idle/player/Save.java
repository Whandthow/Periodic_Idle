package com.periodic.idle.player;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saves")
@Getter @Setter
@NoArgsConstructor
public class Save {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String playerName;
    private LocalDateTime lastTick;

    /** true після одноразового апгрейду "Зламати нескінченність" — знімає кап 1e308. */
    @Column(name = "broken_infinity", nullable = false)
    private boolean brokenInfinity;

    /** Скільки разів гравець виконав "Колапс матерії" (для статистики). */
    @Column(name = "matter_collapses", nullable = false)
    private long matterCollapses;

    /** Чи увімкнена автопокупка генераторів (UI-перемикач). За замовчуванням true. */
    @Column(name = "autobuy_enabled", nullable = false)
    private boolean autobuyEnabled = true;

    /** Чи увімкнений автосинтез елементів (Тір 2) і молекул (Тір 3). За замовчуванням вимкнено. */
    @Column(name = "auto_synthesize_enabled", nullable = false)
    private boolean autoSynthesizeEnabled = false;

    /**
     * Чи увімкнена автопокупка апгрейдів Тіру 0. Розблоковується у UI лише після
     * {@code balance.auto-upgrade.unlock-collapses} (application.yml)
     * колапсів матерії. За замовчуванням вимкнено.
     */
    @Column(name = "auto_upgrade_enabled", nullable = false)
    private boolean autoUpgradeEnabled = false;

    /**
     * Скільки разів зоря гравця (Тір 4) вибухнула гіпернового через нестачу палива
     * (Гідрогену) — StarService.tick() скидає всі player_elements/player_molecules/
     * player_stars цього save при кожному вибуху. Лічильник ніколи не скидається.
     */
    @Column(name = "hypernova_count", nullable = false)
    private long hypernovaCount;

    /**
     * Стабільний UUID, який клієнт зберігає в localStorage. Дає мульти-юзер: кожен
     * браузер ідентифікує "свій" save без логіна. NULL = legacy/dev save.
     */
    @Column(name = "client_token", unique = true)
    private String clientToken;

    /** Скільки разів гравець виконав "Реінкарнацію" (престиж, Тір 0). Ніколи не скидається. */
    @Column(name = "prestige_count", nullable = false)
    private long prestigeCount;

    /** Момент створення save — база для розрахунку сумарного часу гри (розділ 7 CLAUDE.md). */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}