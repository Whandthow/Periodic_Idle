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
     * {@link com.periodic.idle.engine.AutoUpgradeService#AUTO_UPGRADE_UNLOCK_COLLAPSES}
     * колапсів матерії. За замовчуванням вимкнено.
     */
    @Column(name = "auto_upgrade_enabled", nullable = false)
    private boolean autoUpgradeEnabled = false;

    /**
     * Стабільний UUID, який клієнт зберігає в localStorage. Дає мульти-юзер: кожен
     * браузер ідентифікує "свій" save без логіна. NULL = legacy/dev save.
     */
    @Column(name = "client_token", unique = true)
    private String clientToken;
}