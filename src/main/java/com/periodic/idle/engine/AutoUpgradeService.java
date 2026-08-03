package com.periodic.idle.engine;

import com.periodic.idle.content.Upgrade;
import com.periodic.idle.content.UpgradeRepository;
import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Автопокупка апгрейдів Тіру 0 — дзеркалить {@link AutoBuyService} (генератори) і
 * {@link AutoSynthesizeService} (елементи/молекули), але для {@code upgrades}.
 * Player-driven toggle ({@link Save#isAutoUpgradeEnabled()}), розблокований у UI
 * лише після {@link #AUTO_UPGRADE_UNLOCK_COLLAPSES} колапсів матерії — до того часу
 * дерево апгрейдів Тіру 0 ще занадто мале й дороге, щоб автопокупка мала сенс.
 */
@Service
@RequiredArgsConstructor
public class AutoUpgradeService {

    /** Скільки колапсів матерії потрібно, щоб у UI відкрився перемикач автопокупки апгрейдів. */
    public static final long AUTO_UPGRADE_UNLOCK_COLLAPSES = 4L;

    /** Рідше за game tick — апгрейди не такі чутливі до затримки, як генератори. */
    private static final long AUTO_UPGRADE_INTERVAL_MS = 1000;

    private final SaveRepository saveRepository;
    private final UpgradeRepository upgradeRepository;
    private final UpgradeService upgradeService;

    /**
     * НЕ {@code @Transactional} тут навмисно — той самий структурний ризик, що й у
     * {@link AutoBuyService#tickAutoBuy()}/{@link AutoSynthesizeService#tickAutoSynthesize()}:
     * {@code upgradeService.buyBulk} — окремий бін, сам {@code @Transactional}. Якби цей
     * метод теж був {@code @Transactional}, усі виклики нижче ділили б одну спільну
     * транзакцію, і перший-ліпший RuntimeException (тут це норма: "Not enough resources",
     * "Already max level", гейт CORE-тіру тощо) позначив би її rollback-only ще до
     * try/catch, і жодна успішна покупка цього тіку не зберіглася б.
     */
    @Scheduled(fixedRate = AUTO_UPGRADE_INTERVAL_MS)
    public void tickAutoUpgrade() {
        for (Save save : saveRepository.findAll()) {
            if (!save.isAutoUpgradeEnabled()) continue;
            if (save.getMatterCollapses() < AUTO_UPGRADE_UNLOCK_COLLAPSES) continue;
            processSave(save.getId());
        }
    }

    void processSave(Long saveId) {
        List<Upgrade> upgrades = upgradeRepository.findAll();
        for (Upgrade upgrade : upgrades) {
            try {
                upgradeService.buyBulk(saveId, upgrade.getId(), -1);
            } catch (RuntimeException ignored) {
                // недостатньо ресурсів, вже макс. рівень, не відкрито CORE-тіром тощо
                // — просто пропускаємо цей апгрейд цього тіку
            }
        }
    }
}
