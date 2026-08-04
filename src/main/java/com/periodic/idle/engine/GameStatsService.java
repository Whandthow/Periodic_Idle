package com.periodic.idle.engine;

import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Звітність про множники і lifetime-прогрес гравця для вкладки "Статистика"
 * (`GET /api/stats/{saveId}`) — винесено з {@link GameEngine} (розділ 7.12
 * CLAUDE.md), оскільки це читання/агрегація вже порахованого стану, а не
 * частина самого tick-двигуна, що мутує ресурси.
 */
@Service
@RequiredArgsConstructor
public class GameStatsService {

    private final SaveRepository saveRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final PlayerElementRepository playerElementRepository;
    private final PlayerMoleculeRepository playerMoleculeRepository;
    private final PlayerStarRepository playerStarRepository;
    private final PlayerAchievementRepository playerAchievementRepository;
    private final com.periodic.idle.content.ElementRepository elementRepository;
    private final com.periodic.idle.content.MoleculeRepository moleculeRepository;
    private final com.periodic.idle.content.StarRepository starRepository;
    private final com.periodic.idle.content.AchievementRepository achievementRepository;
    private final GameEngine gameEngine;

    /**
     * Розгорнута статистика множників і їх джерел для UI вкладки "Статистика".
     * Сюди НЕ додаються прихильні до часу значення (поточна енергія) — лише множники.
     */
    public Map<String, Object> calculateStats(Long saveId) {
        List<PlayerGenerator> generators = playerGeneratorRepository.findBySaveId(saveId);
        List<PlayerUpgrade> upgrades = playerUpgradeRepository.findBySaveId(saveId);
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);

        double energyMult = UpgradeMultipliers.calcEnergyMult(upgrades);
        double genMult = UpgradeMultipliers.calcMultiplier(upgrades, "GENERATOR_MULT");
        double coreBoost = UpgradeMultipliers.calcCoreBoost(upgrades, resources);
        double protonMult = ParticleBonus.protonEnergyMult(resources);
        double cycleBoost = calcCycleBoost(saveId);
        double energyPow = UpgradeMultipliers.calcEnergyPow(upgrades);
        Map<Long, Double> genSpecific = UpgradeMultipliers.calcGenSpecificMults(upgrades, generators);
        Map<Long, Double> genStack = UpgradeMultipliers.calcGenStackMults(upgrades, generators);
        Map<Long, GameEngine.GenBreakdown> breakdown = gameEngine.calculateGeneratorBreakdown(saveId);
        double totalEnergy = breakdown.values().stream()
                .mapToDouble(GameEngine.GenBreakdown::energyPerSec).sum();

        long pCount = ParticleBonus.count(resources, "p");
        long nCount = ParticleBonus.count(resources, "n");
        long eCount = ParticleBonus.count(resources, "e");
        double neutronCostCut = ParticleBonus.neutronCostReduction(resources);
        double electronCrystalMult = ParticleBonus.electronCrystalMult(resources);
        long matterCollapses = saveRepository.findById(saveId).map(Save::getMatterCollapses).orElse(0L);

        List<PlayerElement> elements = playerElementRepository.findBySaveId(saveId);
        long distinctElements = ElementBonus.distinctCount(elements);
        long totalAtoms = ElementBonus.totalAtomCount(elements);
        double diversityMult = ElementBonus.diversityMult(elements);
        double atomCountMult = ElementBonus.atomCountMult(elements);

        // Множники з ярликами джерел.
        List<Map<String, Object>> mults = new ArrayList<>();
        mults.add(multEntry("Енергомножник", energyMult,
                "ENERGY_MULT × рівень (softcap після 20)", upgradeLevel(upgrades, "ENERGY_MULT")));
        mults.add(multEntry("Генератори ×", genMult,
                "GENERATOR_MULT × рівень", upgradeLevel(upgrades, "GENERATOR_MULT")));
        mults.add(multEntry("Ядро (Core)", coreBoost,
                "10^(Core × VC log10)", upgradeLevel(upgrades, "CORE")));
        mults.add(multEntry("Цикл колапсів", cycleBoost,
                "10^(2.5 × log10(колапсів+1)), не залежить від VC", (int) matterCollapses));
        mults.add(multEntry("Протони → енергія", protonMult,
                "+" + pct(ParticleBonus.PROTON_ENERGY_PER) + " за кожен p", (int) pCount));
        mults.add(multEntry("Нейтрони → ціна", 1.0 - neutronCostCut,
                "−" + fmt3(ParticleBonus.NEUTRON_COST_PER) + " до cost-mult за кожен n",
                (int) nCount));
        mults.add(multEntry("Електрони → VC", electronCrystalMult,
                "+" + pct(ParticleBonus.ELECTRON_VC_PER) + " за кожен e", (int) eCount));
        mults.add(multEntry("Степінь енергії", energyPow,
                "rate^pow при rate>1", upgradeLevel(upgrades, "ENERGY_POW")));
        mults.add(multEntry("Різноманіття елементів", diversityMult,
                "+" + pct(ElementBonus.DIVERSITY_PER) + " за кожен різний елемент (крива насичення)",
                (int) distinctElements));
        mults.add(multEntry("Кількість атомів", atomCountMult,
                "+" + pct(ElementBonus.ATOM_COUNT_PER) + " за log10(атомів), софт/хард кап",
                (int) Math.min(totalAtoms, Integer.MAX_VALUE)));

        // Per-generator розбивка.
        List<Map<String, Object>> perGen = new ArrayList<>();
        List<PlayerGenerator> sorted = generators.stream()
                .sorted(Comparator.comparing(pg -> pg.getGenerator().getId()))
                .toList();
        for (PlayerGenerator pg : sorted) {
            Long gid = pg.getGenerator().getId();
            GameEngine.GenBreakdown gb = breakdown.getOrDefault(gid, new GameEngine.GenBreakdown(0.0, 0.0));
            double share = totalEnergy > 0 ? gb.energyPerSec() / totalEnergy : 0.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", gid);
            row.put("name", pg.getGenerator().getName());
            row.put("level", pg.getLevel());
            row.put("energyPerSec", gb.energyPerSec());
            row.put("share", share);
            row.put("phantomBonus", gb.phantomBonus());
            row.put("genSpecificMult", genSpecific.getOrDefault(gid, 1.0));
            row.put("genStackMult", genStack.getOrDefault(gid, 1.0));
            perGen.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("multipliers", mults);
        result.put("generators", perGen);
        result.put("totalEnergyPerSec", totalEnergy);
        result.put("lifetime", calculateLifetimeStats(saveId));
        return result;
    }

    /**
     * Сумарна ("lifetime") статистика гравця — незалежна від поточного тіру/множників:
     * час гри, кількість реінкарнацій/колапсів/гіпернов, прогрес по вмісту (елементи,
     * молекули, зорі, досягнення). На відміну від {@code multipliers}/{@code generators}
     * (актуальний стан виробництва Тіру 0), ці числа монотонно ростуть (окрім
     * distinct-показників, які можуть впасти після Колапсу матерії чи Гіпернови).
     */
    private Map<String, Object> calculateLifetimeStats(Long saveId) {
        Save save = saveRepository.findById(saveId).orElse(null);

        double playtimeSeconds = 0.0;
        if (save != null && save.getCreatedAt() != null) {
            playtimeSeconds = java.time.Duration.between(save.getCreatedAt(), LocalDateTime.now()).toMillis() / 1000.0;
            if (playtimeSeconds < 0) playtimeSeconds = 0.0;
        }

        List<PlayerElement> elements = playerElementRepository.findBySaveId(saveId);
        long distinctElements = ElementBonus.distinctCount(elements);

        List<PlayerMolecule> molecules = playerMoleculeRepository.findBySaveId(saveId);
        long distinctMolecules = molecules.stream().filter(pm -> pm.getCount() > 0).count();

        List<PlayerStar> stars = playerStarRepository.findBySaveId(saveId);
        long starsIgnited = stars.stream().filter(ps -> ps.getLevel() > 0).count();

        long achievementsUnlocked = playerAchievementRepository.findBySaveId(saveId).size();

        Map<String, Object> lifetime = new LinkedHashMap<>();
        lifetime.put("playtimeSeconds", playtimeSeconds);
        lifetime.put("prestigeCount", save != null ? save.getPrestigeCount() : 0L);
        lifetime.put("matterCollapses", save != null ? save.getMatterCollapses() : 0L);
        lifetime.put("hypernovaCount", save != null ? save.getHypernovaCount() : 0L);
        lifetime.put("brokenInfinity", save != null && save.isBrokenInfinity());
        lifetime.put("distinctElements", distinctElements);
        lifetime.put("totalElements", elementRepository.count());
        lifetime.put("distinctMolecules", distinctMolecules);
        lifetime.put("totalMolecules", moleculeRepository.count());
        lifetime.put("starsIgnited", starsIgnited);
        lifetime.put("totalStars", starRepository.count());
        lifetime.put("achievementsUnlocked", achievementsUnlocked);
        lifetime.put("totalAchievements", achievementRepository.count());
        return lifetime;
    }

    /** Цикл-буст від кількості колапсів матерії (CollapseCycleBonus) — 0 колапсів -> 1.0. */
    private double calcCycleBoost(Long saveId) {
        return saveRepository.findById(saveId)
                .map(save -> CollapseCycleBonus.boost(save.getMatterCollapses()))
                .orElse(1.0);
    }

    private static Map<String, Object> multEntry(String name, double value, String formula, int level) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", Double.isFinite(value) ? value : 0.0);
        m.put("formula", formula);
        m.put("level", level);
        return m;
    }

    private static String pct(double v) { return Math.round(v * 100.0) + "%"; }
    private static String fmt3(double v) { return String.format(java.util.Locale.ROOT, "%.3f", v); }

    private static int upgradeLevel(List<PlayerUpgrade> upgrades, String effectType) {
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if (effectType.equals(pu.getUpgrade().getEffectType())) return pu.getLevel();
        }
        return 0;
    }
}
