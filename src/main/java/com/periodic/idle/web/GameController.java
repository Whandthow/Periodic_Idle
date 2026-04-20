package com.periodic.idle.web;

import com.periodic.idle.content.Generator;
import com.periodic.idle.content.GeneratorRepository;
import com.periodic.idle.content.Upgrade;
import com.periodic.idle.content.UpgradeRepository;
import com.periodic.idle.engine.GameEngine;
import com.periodic.idle.engine.GeneratorService;
import com.periodic.idle.engine.UpgradeService;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GameController {

    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final UpgradeRepository upgradeRepository;
    private final GeneratorRepository generatorRepository;
    private final UpgradeService upgradeService;
    private final GeneratorService generatorService;
    private final GameEngine gameEngine;

    @GetMapping("/state/{saveId}")
    public List<Map<String, Object>> getState(@PathVariable Long saveId) {
        Map<Long, Double> production = gameEngine.calculateProductionPerSec(saveId);

        return playerResourceRepository.findBySaveId(saveId).stream()
                .map(pr -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("resource", pr.getResource().getCode());
                    map.put("number", pr.getNumber());
                    map.put("exponent", pr.getExponent());
                    map.put("ratePerSec", production.getOrDefault(pr.getResource().getId(), 0.0));
                    return map;
                })
                .toList();
    }

    @GetMapping("/upgrades/{saveId}")
    public List<Map<String, Object>> getUpgrades(@PathVariable Long saveId) {
        List<PlayerUpgrade> playerUpgrades = playerUpgradeRepository.findBySaveId(saveId);
        List<Upgrade> allUpgrades = upgradeRepository.findAll();

        return allUpgrades.stream().map(u -> {
            int level = playerUpgrades.stream()
                    .filter(pu -> pu.getUpgrade().getId().equals(u.getId()))
                    .findFirst()
                    .map(PlayerUpgrade::getLevel)
                    .orElse(0);

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("code", u.getCode());
            map.put("name", u.getName());
            map.put("description", u.getDescription());
            map.put("effectType", u.getEffectType());
            map.put("effectValue", u.getEffectValue());
            map.put("maxLevel", u.getMaxLevel());
            map.put("currentLevel", level);
            map.put("costNumber", u.getCostNumber());
            map.put("costExponent", u.getCostExponent());
            map.put("costMultiplier", u.getCostMultiplier());
            return map;
        }).toList();
    }

    @GetMapping("/generators/{saveId}")
    public List<Map<String, Object>> getGenerators(@PathVariable Long saveId) {
        List<PlayerGenerator> playerGenerators = playerGeneratorRepository.findBySaveId(saveId);
        List<Generator> allGenerators = generatorRepository.findAll();

        return allGenerators.stream().map(g -> {
            int level = playerGenerators.stream()
                    .filter(pg -> pg.getGenerator().getId().equals(g.getId()))
                    .findFirst()
                    .map(PlayerGenerator::getLevel)
                    .orElse(0);

            double ratePerLevel = g.getOutputs().stream()
                    .mapToDouble(o -> o.getRatePerLevel())
                    .sum();

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", g.getId());
            map.put("code", g.getCode());
            map.put("name", g.getName());
            map.put("level", level);
            map.put("ratePerLevel", ratePerLevel);
            map.put("baseCostNumber", g.getBaseCostNumber());
            map.put("baseCostExponent", g.getBaseCostExponent());
            map.put("costMultiplier", g.getCostMultiplier());
            return map;
        }).toList();
    }

    @PostMapping("/buy-generator")
    public Map<String, String> buyGenerator(@RequestBody Map<String, Long> request) {
        generatorService.buy(request.get("saveId"), request.get("generatorId"));
        return Map.of("status", "ok");
    }

    @PostMapping("/buy-upgrade")
    public Map<String, String> buyUpgrade(@RequestBody Map<String, Long> request) {
        upgradeService.buy(request.get("saveId"), request.get("upgradeId"));
        return Map.of("status", "ok");
    }
}
