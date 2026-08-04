package com.periodic.idle.web;

import com.periodic.idle.engine.AutoUpgradeService;
import com.periodic.idle.engine.CollapseCycleBonus;
import com.periodic.idle.engine.ElementBonus;
import com.periodic.idle.engine.ExchangeService;
import com.periodic.idle.engine.GameEngine;
import com.periodic.idle.engine.MatterService;
import com.periodic.idle.engine.ParticleBonus;
import com.periodic.idle.player.PlayerElement;
import com.periodic.idle.player.PlayerElementRepository;
import com.periodic.idle.player.PlayerResource;
import com.periodic.idle.player.PlayerResourceRepository;
import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Тір 1: обмін VC → частинки, колапс матерії, Break Infinity, живі бонуси
 * частинок/циклу/елементів, і player-driven тогли автоматизації
 * (autobuy/autosynthesize/autoupgrade) — усі читаються з того самого
 * "загального бегу прапорів save", що й `matter-info`.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MatterController {

    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerElementRepository playerElementRepository;
    private final SaveRepository saveRepository;
    private final ExchangeService exchangeService;
    private final MatterService matterService;

    @PostMapping("/exchange/split")
    public Map<String, Object> splitCrystals(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Object amt = request.get("amount");
        long amount = amt == null ? 1 : ((Number) amt).longValue(); // -1 = max
        long split = exchangeService.splitCrystals(saveId, amount);
        return Map.of("status", "ok", "split", split);
    }

    @GetMapping("/matter-info/{saveId}")
    public Map<String, Object> matterInfo(@PathVariable Long saveId) {
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);

        Map<String, Long> particles = new LinkedHashMap<>();
        for (String code : new String[] { "p", "n", "e" }) {
            long count = 0L;
            for (PlayerResource pr : resources) {
                if (pr.getResource() != null && code.equals(pr.getResource().getCode())) {
                    double mantissa = pr.getNumber();
                    long exp = pr.getExponent();
                    if (Double.isFinite(mantissa) && mantissa > 0 && exp >= 0 && exp <= 18) {
                        count = (long) Math.floor(mantissa * Math.pow(10, exp));
                    }
                    break;
                }
            }
            particles.put(code, count);
        }

        double log10Energy = 0.0;
        for (PlayerResource pr : resources) {
            if (pr.getResource() != null && "E".equals(pr.getResource().getCode())) {
                if (pr.getNumber() > 0) {
                    log10Energy = Math.log10(pr.getNumber()) + pr.getExponent();
                }
                break;
            }
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("brokenInfinity", save.isBrokenInfinity());
        map.put("matterCollapses", save.getMatterCollapses());
        map.put("particles", particles);
        map.put("energyLog10", log10Energy);
        map.put("energyCapLog10", GameEngine.ENERGY_CAP_EXPONENT);
        map.put("breakInfinityRequired", MatterService.BREAK_INFINITY_REQUIRED);
        map.put("collapseReady", save.isBrokenInfinity()
                || log10Energy >= GameEngine.ENERGY_CAP_EXPONENT);
        map.put("autobuyEnabled", save.isAutobuyEnabled());
        map.put("autoSynthesizeEnabled", save.isAutoSynthesizeEnabled());
        map.put("autoUpgradeEnabled", save.isAutoUpgradeEnabled());
        map.put("autoUpgradeUnlockCollapses", AutoUpgradeService.AUTO_UPGRADE_UNLOCK_COLLAPSES);
        map.put("autoUpgradeUnlocked",
                save.getMatterCollapses() >= AutoUpgradeService.AUTO_UPGRADE_UNLOCK_COLLAPSES);
        // Реальні поточні бонуси від накопичених частинок (ParticleBonus, розділ 7.1 CLAUDE.md) —
        // щоб гравець бачив НАЖИВО, що саме йому дають протони/нейтрони/електрони, а не здогадувався.
        map.put("protonEnergyMult", ParticleBonus.protonEnergyMult(resources));
        map.put("neutronCostReduction", ParticleBonus.neutronCostReduction(resources));
        map.put("electronCrystalMult", ParticleBonus.electronCrystalMult(resources));
        // Цикл-буст від кількості колапсів (CollapseCycleBonus) — не залежить від VC,
        // єдиний місток до повторного колапсу матерії до VC_PERSISTS_AFTER_COLLAPSES.
        map.put("cycleBoost", CollapseCycleBonus.boost(save.getMatterCollapses()));
        map.put("vcPersistsAfterCollapses", CollapseCycleBonus.VC_PERSISTS_AFTER_COLLAPSES);
        // Реальні поточні бонуси від синтезованих елементів (ElementBonus, розділ 7.1 CLAUDE.md) —
        // той самий принцип, що й ParticleBonus вище, лише вхід — Тір 2, а не Тір 1.
        List<PlayerElement> elements = playerElementRepository.findBySaveId(saveId);
        map.put("elementDiversityMult", ElementBonus.diversityMult(elements));
        map.put("elementAtomCountMult", ElementBonus.atomCountMult(elements));
        map.put("distinctElementsSynthesized", ElementBonus.distinctCount(elements));
        map.put("totalAtomsSynthesized", ElementBonus.totalAtomCount(elements));
        return map;
    }

    @PostMapping("/autobuy-toggle")
    public Map<String, Object> autobuyToggle(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));
        Object enabled = request.get("enabled");
        boolean next = enabled == null ? !save.isAutobuyEnabled() : Boolean.TRUE.equals(enabled);
        save.setAutobuyEnabled(next);
        saveRepository.save(save);
        return Map.of("status", "ok", "autobuyEnabled", next);
    }

    @PostMapping("/autosynthesize-toggle")
    public Map<String, Object> autoSynthesizeToggle(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));
        Object enabled = request.get("enabled");
        boolean next = enabled == null ? !save.isAutoSynthesizeEnabled() : Boolean.TRUE.equals(enabled);
        save.setAutoSynthesizeEnabled(next);
        saveRepository.save(save);
        return Map.of("status", "ok", "autoSynthesizeEnabled", next);
    }

    @PostMapping("/autoupgrade-toggle")
    public Map<String, Object> autoUpgradeToggle(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));
        Object enabled = request.get("enabled");
        boolean next = enabled == null ? !save.isAutoUpgradeEnabled() : Boolean.TRUE.equals(enabled);
        save.setAutoUpgradeEnabled(next);
        saveRepository.save(save);
        return Map.of("status", "ok", "autoUpgradeEnabled", next);
    }

    @PostMapping("/matter-collapse")
    public Map<String, String> matterCollapse(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        String particle = String.valueOf(request.get("particle"));
        matterService.collapse(saveId, particle);
        return Map.of("status", "ok", "particle", particle);
    }

    @PostMapping("/break-infinity")
    public Map<String, String> breakInfinity(@RequestBody Map<String, Long> request) {
        matterService.breakInfinity(request.get("saveId"));
        return Map.of("status", "ok");
    }
}
