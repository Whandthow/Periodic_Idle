package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.GeneratorOutput;
import com.periodic.idle.engine.config.GameEngineProperties;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GameEngine {

    private final GameEngineProperties props;
    private final UpgradeMultipliers upgradeMultipliers;
    private final ParticleBonus particleBonus;
    private final CollapseCycleBonus collapseCycleBonus;
    private final ElementBonus elementBonus;

    private final SaveRepository saveRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final PlayerElementRepository playerElementRepository;

    /** Dev-швидкість: множник часу, який додається за один тік. */
    private double tickSpeedMultiplier = 1.0;

    public void setTickSpeedMultiplier(double multiplier) {
        if (multiplier <= 0) throw new IllegalArgumentException("tickSpeedMultiplier must be > 0");
        this.tickSpeedMultiplier = multiplier;
    }

    public double getTickSpeedMultiplier() {
        return tickSpeedMultiplier;
    }

    @Scheduled(fixedRateString = "${balance.game-engine.tick-interval-ms}")
    @Transactional
    public void tick() {
        List<Save> saves = saveRepository.findAll();
        for (Save save : saves) {
            processSave(save, props.tickIntervalSec() * tickSpeedMultiplier);
            save.setLastTick(LocalDateTime.now());
        }
    }

    /**
     * Одноразовий наздоганяючий прогрес за час, поки сервер був вимкнений (деплой, рестарт).
     * Викликається на старті застосунку для кожного save — рахує реальний dt від
     * {@code lastTick} до зараз і нараховує виробництво так, ніби генератори працювали весь цей час.
     * Не чіпає tickSpeedMultiplier (це dev-прискорення, не стосується реального офлайн-часу)
     * і обмежене {@code offlineMaxSeconds}, щоб збій годинника не подарував нескінченність одразу.
     */
    @Transactional
    public void applyOfflineProgress() {
        LocalDateTime now = LocalDateTime.now();
        for (Save save : saveRepository.findAll()) {
            if (save.getLastTick() == null) {
                save.setLastTick(now);
                continue;
            }
            double elapsedSeconds = java.time.Duration.between(save.getLastTick(), now).toMillis() / 1000.0;
            if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < props.offlineMinSeconds()) continue;

            double dtSeconds = Math.min(elapsedSeconds, props.offlineMaxSeconds());
            processSave(save, dtSeconds);
            save.setLastTick(now);
        }
    }

    private void processSave(Save save, double dtSeconds) {
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(save.getId());
        Map<Long, Double> productionPerSec = computeProduction(save.getId());
        boolean capEnergy = !save.isBrokenInfinity();
        long energyCapExponent = props.energyCapExponent();

        for (Map.Entry<Long, Double> entry : productionPerSec.entrySet()) {
            PlayerResource pr = findResource(resources, entry.getKey());
            if (pr == null) continue;

            boolean isEnergy = pr.getResource() != null && "E".equals(pr.getResource().getCode());

            // Cap-clamp ПЕРЕД перевіркою rate: інакше Infinity-rate (overflow в ENERGY_POW)
            // блокує оновлення енергії, і вона "застигає" нижче капу — Тір 1 не відкривається.
            if (capEnergy && isEnergy && pr.getExponent() >= energyCapExponent) {
                pr.setNumber(1.0);
                pr.setExponent(energyCapExponent);
                continue;
            }

            double rate = entry.getValue();
            // Energy + Infinity rate до капу → одразу clamp до капу (інакше нескінченно "застрягне").
            if (capEnergy && isEnergy && Double.isInfinite(rate) && rate > 0) {
                pr.setNumber(1.0);
                pr.setExponent(energyCapExponent);
                continue;
            }
            // Той самий overflow ПІСЛЯ Break Infinity: капу більше нема, куди "телепортувати"
            // енергію, а причина переповнення (величезні множники) сама по собі не зникає —
            // тому звичайний skip (нижче) назавжди заморозив би виробництво. Замість цього —
            // явний скінченний стрибок експоненти: гравець бачить прогрес, а не завислий 0/с.
            if (!capEnergy && isEnergy && Double.isInfinite(rate) && rate > 0) {
                pr.setNumber(1.0);
                pr.setExponent(pr.getExponent() + props.infiniteRateExponentJump());
                continue;
            }
            if (!Double.isFinite(rate) || rate <= 0) continue; // захист від NaN/Infinity

            double addPerTick = rate * dtSeconds;
            if (capEnergy && isEnergy && Double.isInfinite(addPerTick) && addPerTick > 0) {
                pr.setNumber(1.0);
                pr.setExponent(energyCapExponent);
                continue;
            }
            if (!capEnergy && isEnergy && Double.isInfinite(addPerTick) && addPerTick > 0) {
                pr.setNumber(1.0);
                pr.setExponent(pr.getExponent() + props.infiniteRateExponentJump());
                continue;
            }
            if (!Double.isFinite(addPerTick) || addPerTick <= 0) continue;

            BigNum current = new BigNum(pr.getNumber(), pr.getExponent());
            BigNum addition = new BigNum(addPerTick, 0);
            BigNum result = current.add(addition);

            // Кап енергії на 1e308 до зламу нескінченності.
            if (capEnergy && isEnergy && result.getExponent() >= energyCapExponent) {
                pr.setNumber(1.0);
                pr.setExponent(energyCapExponent);
            } else {
                pr.setNumber(result.getNumber());
                pr.setExponent(result.getExponent());
            }
        }
    }

    /** Виробництво за секунду, згруповане за resourceId. Використовується для UI. */
    public Map<Long, Double> calculateProductionPerSec(Long saveId) {
        return computeProduction(saveId);
    }

    /** Детальна інформація про внесок одного генератора (для UI-розбивки). */
    public record GenBreakdown(double energyPerSec, double phantomBonus) {}

    /**
     * Повертає посекундне виробництво енергії окремо для кожного генератора
     * з урахуванням усіх бустів, а також фантомний бонус (множник, на який
     * помножується цей ген). Ключ — generatorId.
     */
    public Map<Long, GenBreakdown> calculateGeneratorBreakdown(Long saveId) {
        List<PlayerGenerator> generators = playerGeneratorRepository.findBySaveId(saveId);
        List<PlayerUpgrade> upgrades = playerUpgradeRepository.findBySaveId(saveId);
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);

        double energyMult = upgradeMultipliers.calcEnergyMult(upgrades);
        double genMult = upgradeMultipliers.calcMultiplier(upgrades, "GENERATOR_MULT");
        double coreBoost = upgradeMultipliers.calcCoreBoost(upgrades, resources);
        double protonMult = particleBonus.protonEnergyMult(resources);
        double cycleBoost = calcCycleBoost(saveId);
        double elementMult = calcElementMult(saveId);
        Map<Long, Double> genSpecific = upgradeMultipliers.calcGenSpecificMults(upgrades, generators);
        double energyPow = upgradeMultipliers.calcEnergyPow(upgrades);
        Map<Long, Double> genStack = upgradeMultipliers.calcGenStackMults(upgrades, generators);
        Map<Long, Double> phantomBonus = upgradeMultipliers.calcPhantomBonus(upgrades, generators);

        Map<Long, GenBreakdown> result = new HashMap<>();
        for (PlayerGenerator pg : generators) {
            Long gid = pg.getGenerator().getId();
            double phantom = phantomBonus.getOrDefault(gid, 0.0);
            if (pg.getLevel() <= 0) {
                result.put(gid, new GenBreakdown(0.0, phantom));
                continue;
            }
            double energyRate = 0.0;
            for (GeneratorOutput output : pg.getGenerator().getOutputs()) {
                if (!"E".equals(output.getResource().getCode())) continue;
                double perGen = genSpecific.getOrDefault(gid, 1.0);
                double stack = genStack.getOrDefault(gid, 1.0);
                double rate = output.getRatePerLevel() * pg.getLevel()
                        * genMult * energyMult * coreBoost * protonMult * cycleBoost * elementMult * perGen * stack;
                if (phantom > 0) rate *= (1.0 + phantom);
                if (energyPow != 1.0 && rate > 1.0) {
                    rate = Math.pow(rate, energyPow);
                }
                energyRate += rate;
            }
            result.put(gid, new GenBreakdown(energyRate, phantom));
        }
        return result;
    }

    private Map<Long, Double> computeProduction(Long saveId) {
        List<PlayerGenerator> generators = playerGeneratorRepository.findBySaveId(saveId);
        List<PlayerUpgrade> upgrades = playerUpgradeRepository.findBySaveId(saveId);
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);

        double energyMult = upgradeMultipliers.calcEnergyMult(upgrades);
        double genMult = upgradeMultipliers.calcMultiplier(upgrades, "GENERATOR_MULT");
        double coreBoost = upgradeMultipliers.calcCoreBoost(upgrades, resources);
        double protonMult = particleBonus.protonEnergyMult(resources);
        double cycleBoost = calcCycleBoost(saveId);
        double elementMult = calcElementMult(saveId);
        Map<Long, Double> genSpecific = upgradeMultipliers.calcGenSpecificMults(upgrades, generators);
        double energyPow = upgradeMultipliers.calcEnergyPow(upgrades);
        Map<Long, Double> genStack = upgradeMultipliers.calcGenStackMults(upgrades, generators);
        Map<Long, Double> phantomBonus = upgradeMultipliers.calcPhantomBonus(upgrades, generators);

        Map<Long, Double> production = new HashMap<>();
        for (PlayerGenerator pg : generators) {
            if (pg.getLevel() <= 0) continue;

            for (GeneratorOutput output : pg.getGenerator().getOutputs()) {
                double perGen = genSpecific.getOrDefault(pg.getGenerator().getId(), 1.0);
                double stack = genStack.getOrDefault(pg.getGenerator().getId(), 1.0);
                double ratePerSec = output.getRatePerLevel() * pg.getLevel()
                        * genMult * energyMult * coreBoost * protonMult * cycleBoost * elementMult * perGen * stack;
                if ("E".equals(output.getResource().getCode())) {
                    double bonus = phantomBonus.getOrDefault(pg.getGenerator().getId(), 0.0);
                    if (bonus > 0) ratePerSec *= (1.0 + bonus);
                    if (energyPow != 1.0 && ratePerSec > 1.0) {
                        ratePerSec = Math.pow(ratePerSec, energyPow);
                    }
                }
                production.merge(output.getResource().getId(), ratePerSec, Double::sum);
            }
        }
        return production;
    }

    /** Цикл-буст від кількості колапсів матерії (CollapseCycleBonus) — 0 колапсів -> 1.0. */
    private double calcCycleBoost(Long saveId) {
        return saveRepository.findById(saveId)
                .map(save -> collapseCycleBonus.boost(save.getMatterCollapses()))
                .orElse(1.0);
    }

    /**
     * Бонус від синтезованих елементів (Тір 2) до виробництва Тіру 0/1 (ElementBonus) —
     * різноманіття РІЗНИХ елементів × сумарна кількість атомів (двоступенева softcap/hardcap-крива).
     */
    private double calcElementMult(Long saveId) {
        List<PlayerElement> elements = playerElementRepository.findBySaveId(saveId);
        return elementBonus.diversityMult(elements) * elementBonus.atomCountMult(elements);
    }

    private PlayerResource findResource(List<PlayerResource> resources, Long resourceId) {
        return resources.stream()
                .filter(r -> r.getResource().getId().equals(resourceId))
                .findFirst()
                .orElse(null);
    }

}
