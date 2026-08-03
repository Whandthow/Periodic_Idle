package com.periodic.idle.web;

import com.periodic.idle.common.BindingEnergy;
import com.periodic.idle.content.Element;
import com.periodic.idle.content.ElementRepository;
import com.periodic.idle.content.Generator;
import com.periodic.idle.content.GeneratorRepository;
import com.periodic.idle.content.Molecule;
import com.periodic.idle.content.MoleculeRepository;
import com.periodic.idle.content.Star;
import com.periodic.idle.content.StarRepository;
import com.periodic.idle.content.TierUnlockConditionRepository;
import com.periodic.idle.content.Upgrade;
import com.periodic.idle.content.UpgradeRepository;
import com.periodic.idle.engine.AchievementService;
import com.periodic.idle.engine.AutoUpgradeService;
import com.periodic.idle.engine.CollapseCycleBonus;
import com.periodic.idle.engine.ElementBonus;
import com.periodic.idle.engine.ExchangeService;
import com.periodic.idle.engine.GameEngine;
import com.periodic.idle.engine.GeneratorService;
import com.periodic.idle.engine.MatterService;
import com.periodic.idle.engine.MoleculeService;
import com.periodic.idle.engine.ParticleBonus;
import com.periodic.idle.engine.PrestigeService;
import com.periodic.idle.engine.SaveService;
import com.periodic.idle.engine.SaveTransferService;
import com.periodic.idle.engine.StarService;
import com.periodic.idle.engine.SynthesisService;
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
    private final PlayerElementRepository playerElementRepository;
    private final UpgradeRepository upgradeRepository;
    private final GeneratorRepository generatorRepository;
    private final ElementRepository elementRepository;
    private final MoleculeRepository moleculeRepository;
    private final PlayerMoleculeRepository playerMoleculeRepository;
    private final StarRepository starRepository;
    private final PlayerStarRepository playerStarRepository;
    private final TierUnlockConditionRepository tierUnlockConditionRepository;
    private final UpgradeService upgradeService;
    private final GeneratorService generatorService;
    private final GameEngine gameEngine;
    private final PrestigeService prestigeService;
    private final ExchangeService exchangeService;
    private final MatterService matterService;
    private final SynthesisService synthesisService;
    private final MoleculeService moleculeService;
    private final StarService starService;
    private final SaveRepository saveRepository;
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

    @GetMapping("/stats/{saveId}")
    public Map<String, Object> stats(@PathVariable Long saveId) {
        return gameEngine.calculateStats(saveId);
    }

    // === Досягнення ===

    @GetMapping("/achievements/{saveId}")
    public List<Map<String, Object>> getAchievements(@PathVariable Long saveId) {
        achievementService.checkAndUnlock(saveId);
        return achievementService.listWithStatus(saveId);
    }

    // === Тір 2: Періодична таблиця ===

    @GetMapping("/elements/{saveId}")
    public List<Map<String, Object>> getElements(@PathVariable Long saveId) {
        List<PlayerElement> playerElements = playerElementRepository.findBySaveId(saveId);
        List<Element> allElements = elementRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Element::getAtomicNumber))
                .toList();

        int maxDiscovered = 0;
        for (PlayerElement pe : playerElements) {
            if (pe.getCount() > 0) {
                maxDiscovered = Math.max(maxDiscovered, pe.getElement().getAtomicNumber());
            }
        }

        // Наукова концепція (CLAUDE.md, розділ 1): зоряний нуклеосинтез (Z>3) вимагає
        // "запаленої зорі" — накопиченого гелієвого палива (SynthesisService).
        long heliumCount = synthesisService.heliumCount(saveId);
        boolean stellarIgnited = heliumCount >= SynthesisService.STELLAR_IGNITION_HELIUM_COUNT;

        List<Map<String, Object>> out = new ArrayList<>();
        for (Element el : allElements) {
            long count = playerElements.stream()
                    .filter(pe -> pe.getElement().getId().equals(el.getId()))
                    .findFirst()
                    .map(PlayerElement::getCount)
                    .orElse(0L);

            boolean requiresStar = el.getAtomicNumber() > SynthesisService.PRIMORDIAL_MAX_ATOMIC_NUMBER;
            // Розблоковано для спроби синтезу: перший елемент завжди, інші — коли попередній вже
            // відкритий, І (якщо це зоряний нуклеосинтез) зоря вже "запалена".
            boolean sequentiallyUnlocked = el.getAtomicNumber() == 1 || el.getAtomicNumber() <= maxDiscovered + 1;
            boolean unlocked = sequentiallyUnlocked && (!requiresStar || stellarIgnited);

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", el.getId());
            map.put("atomicNumber", el.getAtomicNumber());
            map.put("symbol", el.getSymbol());
            map.put("name", el.getName());
            map.put("atomicWeight", el.getAtomicWeight());
            map.put("period", el.getPeriod());
            map.put("groupNumber", el.getGroupNumber());
            map.put("shellConfig", el.getShellConfig());
            map.put("costProtons", el.getCostProtons());
            map.put("costNeutrons", el.getCostNeutrons());
            map.put("costElectrons", el.getCostElectrons());
            map.put("count", count);
            map.put("unlocked", unlocked);
            map.put("requiresStar", requiresStar);
            map.put("stellarIgnited", stellarIgnited);
            if (requiresStar && !stellarIgnited) {
                map.put("lockedReason", "Потрібна зоря: " + heliumCount + " / "
                        + SynthesisService.STELLAR_IGNITION_HELIUM_COUNT + " He");
            } else if (!sequentiallyUnlocked) {
                map.put("lockedReason", "Спочатку синтезуйте попередній елемент");
            } else {
                map.put("lockedReason", null);
            }
            // Наукова концепція (CLAUDE.md, розділ 1): реальна енергія зв'язку ядра (SEMF),
            // видима гравцеві — синтез до заліза-56 повертає E, важче за залізо — коштує E.
            int massNumber = (int) (el.getCostProtons() + el.getCostNeutrons());
            double bindingEnergyMeV = BindingEnergy.totalMeV(el.getAtomicNumber(), massNumber);
            map.put("bindingEnergyMeV", bindingEnergyMeV);
            map.put("exothermic", el.getAtomicNumber() <= 26);
            out.add(map);
        }
        return out;
    }

    @PostMapping("/synthesize")
    public Map<String, Object> synthesize(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Long elementId = ((Number) request.get("elementId")).longValue();
        Object amt = request.get("amount");
        long amount = amt == null ? 1 : ((Number) amt).longValue(); // -1 = max
        long synthesized = synthesisService.synthesizeBulk(saveId, elementId, amount);
        return Map.of("status", "ok", "synthesized", synthesized);
    }

    // === Тір 3: Молекули ===

    @GetMapping("/molecules/{saveId}")
    public List<Map<String, Object>> getMolecules(@PathVariable Long saveId) {
        List<PlayerMolecule> playerMolecules = playerMoleculeRepository.findBySaveId(saveId);
        List<Molecule> allMolecules = moleculeRepository.findAll().stream()
                .sorted(Comparator.comparing(Molecule::getFormula))
                .toList();

        List<Map<String, Object>> out = new ArrayList<>();
        for (Molecule m : allMolecules) {
            long count = playerMolecules.stream()
                    .filter(pm -> pm.getMolecule().getId().equals(m.getId()))
                    .findFirst()
                    .map(PlayerMolecule::getCount)
                    .orElse(0L);

            List<Map<String, Object>> recipe = m.getComponents().stream()
                    .map(c -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("elementSymbol", c.getElement().getSymbol());
                        row.put("atomCount", c.getAtomCount());
                        return (Map<String, Object>) row;
                    })
                    .toList();

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("formula", m.getFormula());
            map.put("name", m.getName());
            map.put("bondEnergyEv", m.getBondEnergyEv());
            map.put("recipe", recipe);
            map.put("count", count);
            map.put("unlocked", moleculeService.canAffordAtLeastOne(saveId, m));
            out.add(map);
        }
        return out;
    }

    @PostMapping("/synthesize-molecule")
    public Map<String, Object> synthesizeMolecule(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Long moleculeId = ((Number) request.get("moleculeId")).longValue();
        Object amt = request.get("amount");
        long amount = amt == null ? 1 : ((Number) amt).longValue(); // -1 = max
        long synthesized = moleculeService.synthesizeBulk(saveId, moleculeId, amount);
        return Map.of("status", "ok", "synthesized", synthesized);
    }

    // === Тір 4: Зорі головної послідовності ===

    @GetMapping("/stars/{saveId}")
    public Map<String, Object> getStars(@PathVariable Long saveId) {
        List<PlayerStar> playerStars = playerStarRepository.findBySaveId(saveId);
        List<PlayerElement> elements = playerElementRepository.findBySaveId(saveId);
        long hydrogenAvailable = elements.stream()
                .filter(pe -> pe.getElement().getAtomicNumber() == 1)
                .findFirst()
                .map(PlayerElement::getCount)
                .orElse(0L);
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));
        // CNO-каталіз (ElementBonus, реальна астрофізика): C+N+O синтезовані -> зоря
        // пропускає пропорційно більше подій за секунду (StarService.processStarTick).
        double cnoMult = ElementBonus.cnoCatalystMult(elements);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Star star : starRepository.findAll()) {
            int level = playerStars.stream()
                    .filter(ps -> ps.getStar().getId().equals(star.getId()))
                    .findFirst()
                    .map(PlayerStar::getLevel)
                    .orElse(0);

            long nextLevelCost = (long) Math.ceil(
                    star.getBaseCostHydrogen() * Math.pow(star.getCostMultiplier(), level));
            double fuelPerSec = star.getEventsPerSecPerLevel() * level * star.getFuelHPerEvent() * cnoMult;
            double outputPerSec = star.getEventsPerSecPerLevel() * level * star.getOutputHePerEvent() * cnoMult;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", star.getId());
            map.put("code", star.getCode());
            map.put("name", star.getName());
            map.put("level", level);
            map.put("nextLevelCostHydrogen", nextLevelCost);
            map.put("fuelHPerSec", fuelPerSec);
            map.put("outputHePerSec", outputPerSec);
            map.put("fuelHPerEvent", star.getFuelHPerEvent());
            map.put("outputHePerEvent", star.getOutputHePerEvent());
            out.add(map);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stars", out);
        result.put("hydrogenAvailable", hydrogenAvailable);
        result.put("hypernovaCount", save.getHypernovaCount());
        result.put("cnoCatalystMult", cnoMult);
        result.put("cnoCatalystActive", cnoMult > 1.0);
        return result;
    }

    @PostMapping("/buy-star")
    public Map<String, Object> buyStar(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Long starId = ((Number) request.get("starId")).longValue();
        Object amt = request.get("amount");
        int amount = amt == null ? 1 : ((Number) amt).intValue(); // -1 = max
        int bought = starService.buyLevel(saveId, starId, amount);
        return Map.of("status", "ok", "bought", bought);
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

    // Обробка бізнес-помилок (недостатньо ресурсів, locked, max level тощо):
    // повертаємо 400 з message, який UI показує гравцеві.
    @ExceptionHandler(RuntimeException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Map<String, String> handleRuntime(RuntimeException ex) {
        return Map.of("error", ex.getMessage() == null ? "Помилка" : ex.getMessage());
    }
}
