package com.periodic.idle.web;

import com.periodic.idle.content.Upgrade;
import com.periodic.idle.content.UpgradeRepository;
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
    private final UpgradeRepository upgradeRepository;
    private final UpgradeService upgradeService;

    @GetMapping("/state/{saveId}")
    public List<Map<String, Object>> getState(@PathVariable Long saveId) {
        return playerResourceRepository.findBySaveId(saveId).stream()
                .map(pr -> Map.<String, Object>of(
                        "resource", pr.getResource().getCode(),
                        "number", pr.getNumber(),
                        "exponent", pr.getExponent()
                ))
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

    @PostMapping("/buy-upgrade")
    public Map<String, String> buyUpgrade(@RequestBody Map<String, Long> request) {
        upgradeService.buy(request.get("saveId"), request.get("upgradeId"));
        return Map.of("status", "ok");
    }
}