package com.periodic.idle.web;

import com.periodic.idle.content.TierUnlockCondition;
import com.periodic.idle.content.TierUnlockConditionRepository;
import com.periodic.idle.engine.GameEngine;
import com.periodic.idle.player.PlayerResource;
import com.periodic.idle.player.PlayerResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Dev-інструменти для швидкого тестування гри.
 * Ці ендпоінти не для продакшну: вони напряму змінюють стан.
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevController {

    private final GameEngine gameEngine;
    private final PlayerResourceRepository playerResourceRepository;
    private final TierUnlockConditionRepository tierUnlockConditionRepository;

    @PostMapping("/tick-speed")
    public Map<String, Object> setTickSpeed(@RequestBody Map<String, Object> body) {
        double multiplier = ((Number) body.getOrDefault("multiplier", 1.0)).doubleValue();
        gameEngine.setTickSpeedMultiplier(multiplier);
        return Map.of("status", "ok", "tickSpeedMultiplier", gameEngine.getTickSpeedMultiplier());
    }

    @GetMapping("/tick-speed")
    public Map<String, Object> getTickSpeed() {
        return Map.of("tickSpeedMultiplier", gameEngine.getTickSpeedMultiplier());
    }

    /**
     * Додає приріст до експоненти ресурсу (body: {saveId, resourceCode, delta}).
     * resourceCode — наприклад "E" або "VC". delta — ціле число.
     */
    @PostMapping("/add-exp")
    @Transactional
    public Map<String, Object> addExponent(@RequestBody Map<String, Object> body) {
        Long saveId = ((Number) body.get("saveId")).longValue();
        String code = String.valueOf(body.get("resourceCode"));
        long delta = ((Number) body.getOrDefault("delta", 1)).longValue();

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource pr = resources.stream()
                .filter(r -> r.getResource() != null && code.equals(r.getResource().getCode()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Resource not found: " + code));

        // Якщо ресурсу зовсім нема — виставляємо базове значення 1 перед підйомом експоненти.
        if (pr.getNumber() <= 0) {
            pr.setNumber(1.0);
            pr.setExponent(0);
        }
        pr.setExponent(pr.getExponent() + delta);
        playerResourceRepository.save(pr);
        return Map.of("status", "ok",
                "resource", code,
                "number", pr.getNumber(),
                "exponent", pr.getExponent());
    }

    /**
     * Перестрибнути одразу на тір: видає МІНІМАЛЬНИЙ прогрес, що задовольняє
     * найлегшу з його OR-умов розблокування ({@code tier_unlock_conditions}),
     * а не довільну купу ресурсів — щоб дев-стрибок так само проходив через
     * той самий data-driven механізм, що й звичайна прогресія (розділ 10 CLAUDE.md).
     * Тір 0 не має умов — no-op. Body: {saveId, tier}.
     */
    @PostMapping("/jump-tier")
    @Transactional
    public Map<String, Object> jumpToTier(@RequestBody Map<String, Object> body) {
        Long saveId = ((Number) body.get("saveId")).longValue();
        int tier = ((Number) body.get("tier")).intValue();

        List<TierUnlockCondition> conditions = tierUnlockConditionRepository.findAll().stream()
                .filter(c -> c.getTier() == tier)
                .toList();
        if (conditions.isEmpty()) {
            return Map.of("status", "ok", "granted", false, "reason", "Тір не має умов розблокування");
        }

        // Найлегша OR-умова: найменший поріг — найдешевше видати.
        TierUnlockCondition easiest = conditions.stream()
                .min(Comparator.comparingDouble(TierUnlockCondition::getMinLog10))
                .orElseThrow();

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource pr = resources.stream()
                .filter(r -> r.getResource() != null
                        && r.getResource().getId().equals(easiest.getResource().getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Ресурс не ініціалізовано: " + easiest.getResource().getCode()));

        long exponent = (long) Math.ceil(Math.max(easiest.getMinLog10(), 0.0));
        pr.setNumber(1.0);
        pr.setExponent(exponent);
        playerResourceRepository.save(pr);

        return Map.of("status", "ok",
                "granted", true,
                "resource", pr.getResource().getCode(),
                "exponent", exponent);
    }
}
