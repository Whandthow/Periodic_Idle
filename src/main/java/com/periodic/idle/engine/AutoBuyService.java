package com.periodic.idle.engine;

import com.periodic.idle.content.GeneratorRepository;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Автопокупка генераторів (AUTOBUY).
 * Запускається рідше за game tick: кожну секунду достатньо.
 */
@Service
@RequiredArgsConstructor
public class AutoBuyService {

    private final SaveRepository saveRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final GeneratorRepository generatorRepository;
    private final GeneratorService generatorService;

    /**
     * НЕ {@code @Transactional} тут навмисно — той самий структурний ризик, що й у
     * {@link AutoSynthesizeService#tickAutoSynthesize()} (живий баг, підтверджений
     * інтеграційним тестом): {@code generatorService.buyBulk} — окремий бін і сам
     * {@code @Transactional}. Тут воно на практиці майже ніколи не стріляє —
     * {@code buyBulk(saveId, id, -1)} у max-режимі свідомо повертає 0 замість винятку
     * при нестачі ресурсів (щоб не псувати саме цю транзакцію), тож єдиний реальний
     * шлях до винятку — зіпсований контент (генератор видалили з БД). Але якби колись
     * ця гарантія в GeneratorService змінилась, спільна зовнішня транзакція так само
     * позначилась би rollback-only від одного невдалого генератора ще до try/catch
     * нижче — тож прибираю анотацію про всяк випадок, а не лише реактивно.
     */
    @Scheduled(fixedRateString = "${balance.auto-buy.interval-ms}")
    public void tickAutoBuy() {
        for (Save save : saveRepository.findAll()) {
            if (!save.isAutobuyEnabled()) continue;
            processSave(save.getId());
        }
    }

    void processSave(Long saveId) {
        int autoBuyLevel = autoBuyLevel(saveId);
        if (autoBuyLevel <= 0) return;

        List<PlayerGenerator> sorted = playerGeneratorRepository.findBySaveId(saveId).stream()
                .sorted(Comparator.comparing(pg -> pg.getGenerator().getId()))
                .toList();

        // На кожен покритий генератор намагаємось купити максимум рівнів за раз.
        for (int i = 0; i < sorted.size() && i < autoBuyLevel; i++) {
            Long genId = sorted.get(i).getGenerator().getId();
            try {
                generatorService.buyBulk(saveId, genId, -1);
            } catch (RuntimeException ignored) {
                // недостатньо ресурсів або інша причина — пропускаємо
            }
        }
    }

    private int autoBuyLevel(Long saveId) {
        return playerUpgradeRepository.findBySaveId(saveId).stream()
                .filter(pu -> pu.getLevel() > 0)
                .filter(pu -> "AUTOBUY".equals(pu.getUpgrade().getEffectType()))
                .mapToInt(PlayerUpgrade::getLevel)
                .max()
                .orElse(0);
    }
}
