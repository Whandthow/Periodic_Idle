package com.periodic.idle.web;

import com.periodic.idle.content.TierUnlockConditionRepository;
import com.periodic.idle.engine.AchievementService;
import com.periodic.idle.engine.GameEngine;
import com.periodic.idle.engine.GameStatsService;
import com.periodic.idle.engine.SaveService;
import com.periodic.idle.engine.SaveTransferService;
import com.periodic.idle.player.PlayerResource;
import com.periodic.idle.player.PlayerResourceRepository;
import com.periodic.idle.player.Save;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Стан збереження, наскрізний по всіх тірах: поточні ресурси, data-driven умови
 * розблокування тірів, статистика/множники, досягнення, і мануальне
 * збереження/відновлення save (init/export/import за client-token).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SaveStateController {

    private final PlayerResourceRepository playerResourceRepository;
    private final TierUnlockConditionRepository tierUnlockConditionRepository;
    private final GameEngine gameEngine;
    private final GameStatsService gameStatsService;
    private final SaveService saveService;
    private final SaveTransferService saveTransferService;
    private final AchievementService achievementService;

    @GetMapping("/state/{saveId}")
    public List<Map<String, Object>> getState(@PathVariable Long saveId) {
        // Перевіряємо досягнення на кожному опитуванні стану (~1.5с, незалежно від
        // активної вкладки) — щоб короткочасні піки (напр. енергія перед престижем) не губились.
        achievementService.checkAndUnlock(saveId);
        Map<Long, Double> production = gameEngine.calculateProductionPerSec(saveId);

        return playerResourceRepository.findBySaveId(saveId).stream()
                .peek(pr -> {
                    // Санітайз: якщо в БД NaN/Infinity — скидаємо до 0.
                    if (!Double.isFinite(pr.getNumber())) {
                        pr.setNumber(0);
                        pr.setExponent(0);
                        playerResourceRepository.save(pr);
                    }
                })
                .map(pr -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("resource", pr.getResource().getCode());
                    map.put("number", pr.getNumber());
                    map.put("exponent", pr.getExponent());
                    double rate = production.getOrDefault(pr.getResource().getId(), 0.0);
                    // JSON не дозволяє Infinity/NaN — обнуляємо для серіалізації;
                    // в логіці processSave такий rate уже скеймпив енергію до 1e308.
                    if (!Double.isFinite(rate)) rate = 0.0;
                    map.put("ratePerSec", rate);
                    return map;
                })
                .toList();
    }

    // Умови розблокування тірів (data-driven progressive disclosure) — контент, однаковий
    // для всіх saves, тому без saveId у шляху. Тір розблокований, якщо ХОЧА Б ОДНА умова з його групи виконана.
    @GetMapping("/tier-unlocks")
    public List<Map<String, Object>> getTierUnlocks() {
        return tierUnlockConditionRepository.findAllByOrderByTierAsc().stream()
                .map(c -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("tier", c.getTier());
                    map.put("resource", c.getResource().getCode());
                    map.put("minLog10", c.getMinLog10());
                    return map;
                })
                .toList();
    }

    @GetMapping("/stats/{saveId}")
    public Map<String, Object> stats(@PathVariable Long saveId) {
        return gameStatsService.calculateStats(saveId);
    }

    // === Досягнення ===

    @GetMapping("/achievements/{saveId}")
    public List<Map<String, Object>> getAchievements(@PathVariable Long saveId) {
        achievementService.checkAndUnlock(saveId);
        return achievementService.listWithStatus(saveId);
    }

    /**
     * Per-browser save resolution. Клієнт зберігає UUID у localStorage під ключем 'pidleToken'
     * і викликає цей endpoint при першому завантаженні. Сервер повертає saveId, який далі
     * використовується у всіх API. Це і є мульти-юзер без логіна.
     */
    @PostMapping("/save/init")
    public Map<String, Object> initSave(@RequestBody Map<String, String> request) {
        String token = request == null ? null : request.get("token");
        Save save = saveService.findOrCreateByToken(token);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("saveId", save.getId());
        return map;
    }

    // === Мануальне збереження (експорт/імпорт у текстовий файл) ===

    @GetMapping("/save-export/{saveId}")
    public Map<String, Object> exportSave(@PathVariable Long saveId) {
        return saveTransferService.exportSave(saveId);
    }

    @PostMapping("/save-import")
    public Map<String, String> importSave(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Object data = request.get("data");
        if (!(data instanceof Map)) {
            throw new RuntimeException("Invalid save data");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) data;
        saveTransferService.importSave(saveId, dataMap);
        return Map.of("status", "ok");
    }
}
