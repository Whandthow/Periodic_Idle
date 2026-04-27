package com.periodic.idle.web;

import com.periodic.idle.engine.GameEngine;
import com.periodic.idle.player.PlayerResource;
import com.periodic.idle.player.PlayerResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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
}
