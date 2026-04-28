package com.periodic.idle.web;

import com.periodic.idle.content.Generator;
import com.periodic.idle.content.GeneratorRepository;
import com.periodic.idle.content.Upgrade;
import com.periodic.idle.content.UpgradeRepository;
import com.periodic.idle.engine.ExchangeService;
import com.periodic.idle.engine.GameEngine;
import com.periodic.idle.engine.GeneratorService;
import com.periodic.idle.engine.MatterService;
import com.periodic.idle.engine.PrestigeService;
import com.periodic.idle.engine.SaveService;
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
    private final PrestigeService prestigeService;
    private final ExchangeService exchangeService;
    private final MatterService matterService;
    private final SaveRepository saveRepository;
    private final SaveService saveService;

    @GetMapping("/state/{saveId}")
    public List<Map<String, Object>> getState(@PathVariable Long saveId) {
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
            map.put("unlockCoreTier", u.getUnlockCoreTier());
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
        List<Generator> allGenerators = generatorRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Generator::getId))
                .toList();
        List<PlayerUpgrade> playerUpgrades = playerUpgradeRepository.findBySaveId(saveId);
        List<PlayerResource> playerResources = playerResourceRepository.findBySaveId(saveId);
        Map<Long, GameEngine.GenBreakdown> breakdown = gameEngine.calculateGeneratorBreakdown(saveId);
        double totalEnergy = breakdown.values().stream()
                .mapToDouble(GameEngine.GenBreakdown::energyPerSec)
                .sum();

        int idx = 0;
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Generator g : allGenerators) {
            idx++;
            int level = playerGenerators.stream()
                    .filter(pg -> pg.getGenerator().getId().equals(g.getId()))
                    .findFirst()
                    .map(PlayerGenerator::getLevel)
                    .orElse(0);

            double ratePerLevel = g.getOutputs().stream()
                    .mapToDouble(o -> o.getRatePerLevel())
                    .sum();

            GameEngine.GenBreakdown br = breakdown.getOrDefault(g.getId(),
                    new GameEngine.GenBreakdown(0.0, 0.0));
            double share = (totalEnergy > 0) ? (br.energyPerSec() / totalEnergy) : 0.0;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", g.getId());
            map.put("code", g.getCode());
            map.put("name", g.getName());
            map.put("level", level);
            map.put("ratePerLevel", ratePerLevel);
            map.put("baseCostNumber", g.getBaseCostNumber());
            map.put("baseCostExponent", g.getBaseCostExponent());
            map.put("costMultiplier", g.getCostMultiplier());
            map.put("effectiveCostMultiplier",
                    generatorService.effectiveCostMultiplier(g.getCostMultiplier(), playerUpgrades, playerResources));
            // нові поля для UI
            map.put("iconIndex", idx);                 // 1..N — індекс іконки Generator{N}Tier1.png
            map.put("energyPerSec", br.energyPerSec()); // з усіма бустами
            map.put("phantomBonus", br.phantomBonus()); // скільки фантомних копій
            map.put("shareOfTotal", share);             // 0..1
            out.add(map);
        }
        return out;
    }

    @PostMapping("/buy-generator")
    public Map<String, Object> buyGenerator(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Long genId  = ((Number) request.get("generatorId")).longValue();
        Object amt  = request.get("amount");
        int amount = amt == null ? 1 : ((Number) amt).intValue(); // -1 = max
        int bought = generatorService.buyBulk(saveId, genId, amount);
        return Map.of("status", "ok", "bought", bought);
    }

    @PostMapping("/buy-generator-all")
    public Map<String, Object> buyAllGenerators(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        int bought = generatorService.buyAllMax(saveId);
        return Map.of("status", "ok", "bought", bought);
    }

    @GetMapping("/prestige-info/{saveId}")
    public Map<String, Object> prestigeInfo(@PathVariable Long saveId) {
        var gain = prestigeService.calcPotentialGain(saveId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("number", gain.getNumber());
        map.put("exponent", gain.getExponent());
        map.put("minLog10Energy", PrestigeService.PRESTIGE_MIN_LOG10_ENERGY);
        return map;
    }

    @PostMapping("/prestige")
    public Map<String, Object> prestige(@RequestBody Map<String, Long> request) {
        var gain = prestigeService.prestige(request.get("saveId"));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "ok");
        map.put("gainNumber", gain.getNumber());
        map.put("gainExponent", gain.getExponent());
        return map;
    }

    @PostMapping("/reset")
    public Map<String, String> resetSave(@RequestBody Map<String, Long> request) {
        prestigeService.hardReset(request.get("saveId"));
        return Map.of("status", "ok");
    }

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

    @GetMapping("/stats/{saveId}")
    public Map<String, Object> stats(@PathVariable Long saveId) {
        return gameEngine.calculateStats(saveId);
    }

    @PostMapping("/buy-upgrade")
    public Map<String, Object> buyUpgrade(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Long upgId  = ((Number) request.get("upgradeId")).longValue();
        Object amt  = request.get("amount");
        int amount = amt == null ? 1 : ((Number) amt).intValue(); // -1 = max
        int bought = upgradeService.buyBulk(saveId, upgId, amount);
        return Map.of("status", "ok", "bought", bought);
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

    // Обробка бізнес-помилок (недостатньо ресурсів, locked, max level тощо):
    // повертаємо 400 з message, який UI показує гравцеві.
    @ExceptionHandler(RuntimeException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Map<String, String> handleRuntime(RuntimeException ex) {
        return Map.of("error", ex.getMessage() == null ? "Помилка" : ex.getMessage());
    }
}
