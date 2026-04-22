package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.GeneratorOutput;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GameEngine {

    private static final long TICK_INTERVAL_MS = 100;
    private static final double TICK_INTERVAL_SEC = TICK_INTERVAL_MS / 1000.0;
    /** Поріг, після якого ENERGY_MULT переходить у softcap (sqrt-ріст). */
    private static final int ENERGY_MULT_SOFTCAP_THRESHOLD = 20;

    private final SaveRepository saveRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;

    /** Dev-швидкість: множник часу, який додається за один тік. */
    private double tickSpeedMultiplier = 1.0;

    public void setTickSpeedMultiplier(double multiplier) {
        if (multiplier <= 0) throw new IllegalArgumentException("tickSpeedMultiplier must be > 0");
        this.tickSpeedMultiplier = multiplier;
    }

    public double getTickSpeedMultiplier() {
        return tickSpeedMultiplier;
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    @Transactional
    public void tick() {
        List<Save> saves = saveRepository.findAll();
        for (Save save : saves) {
            processSave(save);
            save.setLastTick(LocalDateTime.now());
        }
    }

    private void processSave(Save save) {
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(save.getId());
        Map<Long, Double> productionPerSec = computeProduction(save.getId());

        for (Map.Entry<Long, Double> entry : productionPerSec.entrySet()) {
            PlayerResource pr = findResource(resources, entry.getKey());
            if (pr == null) continue;

            double addPerTick = entry.getValue() * TICK_INTERVAL_SEC * tickSpeedMultiplier;

            BigNum current = new BigNum(pr.getNumber(), pr.getExponent());
            BigNum addition = new BigNum(addPerTick, 0);
            BigNum result = current.add(addition);

            pr.setNumber(result.getNumber());
            pr.setExponent(result.getExponent());
        }
    }

    /** Виробництво за секунду, згруповане за resourceId. Використовується для UI. */
    public Map<Long, Double> calculateProductionPerSec(Long saveId) {
        return computeProduction(saveId);
    }

    private Map<Long, Double> computeProduction(Long saveId) {
        List<PlayerGenerator> generators = playerGeneratorRepository.findBySaveId(saveId);
        List<PlayerUpgrade> upgrades = playerUpgradeRepository.findBySaveId(saveId);
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);

        double energyMult = calcEnergyMult(upgrades);
        double genMult = calcMultiplier(upgrades, "GENERATOR_MULT");
        double coreBoost = calcCoreBoost(upgrades, resources);
        Map<Long, Double> genSpecific = calcGenSpecificMults(upgrades, generators);
        double energyPow = calcEnergyPow(upgrades);
        Map<Long, Double> genStack = calcGenStackMults(upgrades, generators);
        Map<Long, Double> phantomBonus = calcPhantomBonus(upgrades, generators);

        Map<Long, Double> production = new HashMap<>();
        for (PlayerGenerator pg : generators) {
            if (pg.getLevel() <= 0) continue;

            for (GeneratorOutput output : pg.getGenerator().getOutputs()) {
                double perGen = genSpecific.getOrDefault(pg.getGenerator().getId(), 1.0);
                double stack = genStack.getOrDefault(pg.getGenerator().getId(), 1.0);
                double ratePerSec = output.getRatePerLevel() * pg.getLevel()
                        * genMult * energyMult * coreBoost * perGen * stack;
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

    private double calcMultiplier(List<PlayerUpgrade> upgrades, String effectType) {
        double mult = 1.0;
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if (pu.getUpgrade().getEffectType().equals(effectType)) {
                mult += pu.getUpgrade().getEffectValue() * pu.getLevel();
            }
        }
        return mult;
    }

    /** ENERGY_MULT із softcap: до порогу — лінійно, після — sqrt від надлишку. */
    private double calcEnergyMult(List<PlayerUpgrade> upgrades) {
        double mult = 1.0;
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if (!"ENERGY_MULT".equals(pu.getUpgrade().getEffectType())) continue;
            int level = pu.getLevel();
            double effective = level;
            if (level > ENERGY_MULT_SOFTCAP_THRESHOLD) {
                effective = ENERGY_MULT_SOFTCAP_THRESHOLD
                        + Math.sqrt(level - ENERGY_MULT_SOFTCAP_THRESHOLD);
            }
            mult += pu.getUpgrade().getEffectValue() * effective;
        }
        return mult;
    }

    /**
     * PHANTOM_GEN: тір T покриває перші 2*T генераторів. Починаючи з T3 швидкість зростає.
     * Фантоми дають бонус для ЕНЕРГІЇ: rate *= (1 + phantomBonus).
     * bonus = max(1, T - 2) — T1=1, T2=1, T3=2, T4=3.
     */
    private Map<Long, Double> calcPhantomBonus(List<PlayerUpgrade> upgrades,
                                                List<PlayerGenerator> generators) {
        Map<Long, Double> bonusByGen = new HashMap<>();
        int T = 0;
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if ("PHANTOM_GEN".equals(pu.getUpgrade().getEffectType())) {
                T = pu.getLevel();
                break;
            }
        }
        if (T <= 0) return bonusByGen;

        double bonus = Math.max(1, T - 2);
        int coverCount = 2 * T;
        List<PlayerGenerator> sorted = generators.stream()
                .sorted(Comparator.comparing(pg -> pg.getGenerator().getId()))
                .toList();
        for (int i = 0; i < sorted.size() && i < coverCount; i++) {
            bonusByGen.put(sorted.get(i).getGenerator().getId(), bonus);
        }
        return bonusByGen;
    }

    /**
     * GEN_STACK: кожен тір апдейта вмикає стек для наступного генератора (за id ASC).
     * Для активних генераторів множник = їх власний level (зі стелей пізніше).
     */
    private Map<Long, Double> calcGenStackMults(List<PlayerUpgrade> upgrades,
                                                 List<PlayerGenerator> generators) {
        Map<Long, Double> multByGen = new HashMap<>();
        for (PlayerGenerator pg : generators) {
            multByGen.put(pg.getGenerator().getId(), 1.0);
        }

        int upLevel = 0;
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if ("GEN_STACK".equals(pu.getUpgrade().getEffectType())) {
                upLevel = pu.getLevel();
                break;
            }
        }
        if (upLevel <= 0) return multByGen;

        List<PlayerGenerator> sorted = generators.stream()
                .sorted(Comparator.comparing(pg -> pg.getGenerator().getId()))
                .toList();

        for (int i = 0; i < sorted.size() && i < upLevel; i++) {
            PlayerGenerator pg = sorted.get(i);
            if (pg.getLevel() > 0) {
                multByGen.put(pg.getGenerator().getId(), (double) pg.getLevel());
            }
        }
        return multByGen;
    }

    /** ENERGY_POW: сума тірів * coeff дає степінь піднесення. */
    private double calcEnergyPow(List<PlayerUpgrade> upgrades) {
        double pow = 1.0;
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if ("ENERGY_POW".equals(pu.getUpgrade().getEffectType())) {
                pow += pu.getUpgrade().getEffectValue() * pu.getLevel();
            }
        }
        return pow;
    }

    /**
     * GEN_SPECIFIC_MULT: кожен тір T відкриває буст для генератора T (за порядком id ASC)
     * і додає +coeff до кожного попереднього. Для генератора на позиції pos з level >= pos:
     * mult приріст = (level - pos + 1) * coeff.
     */
    private Map<Long, Double> calcGenSpecificMults(List<PlayerUpgrade> upgrades,
                                                    List<PlayerGenerator> generators) {
        Map<Long, Double> multByGen = new HashMap<>();
        for (PlayerGenerator pg : generators) {
            multByGen.put(pg.getGenerator().getId(), 1.0);
        }

        int level = 0;
        double coeff = 0.0;
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if ("GEN_SPECIFIC_MULT".equals(pu.getUpgrade().getEffectType())) {
                level = pu.getLevel();
                coeff = pu.getUpgrade().getEffectValue();
                break;
            }
        }
        if (level <= 0 || coeff <= 0) return multByGen;

        List<PlayerGenerator> sorted = generators.stream()
                .sorted(Comparator.comparing(pg -> pg.getGenerator().getId()))
                .toList();

        for (int i = 0; i < sorted.size(); i++) {
            int pos = i + 1;
            if (level < pos) break;
            int tiersAffecting = level - pos + 1;
            Long genId = sorted.get(i).getGenerator().getId();
            multByGen.merge(genId, tiersAffecting * coeff, Double::sum);
        }
        return multByGen;
    }

    /** Ядро: буст від кількості кристалів пустоти. 0 кристалів -> 1.0. */
    private double calcCoreBoost(List<PlayerUpgrade> upgrades, List<PlayerResource> resources) {
        int coreLevel = 0;
        double coreCoeff = 0.0;
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if ("CORE".equals(pu.getUpgrade().getEffectType())) {
                coreLevel = pu.getLevel();
                coreCoeff = pu.getUpgrade().getEffectValue();
                break;
            }
        }
        if (coreLevel <= 0 || coreCoeff <= 0) return 1.0;
        double crystalsLog10 = 0.0;
        for (PlayerResource pr : resources) {
            if (pr.getResource() != null && "VC".equals(pr.getResource().getCode())) {
                double mantissa = pr.getNumber();
                long exp = pr.getExponent();
                if (mantissa <= 0) break;
                crystalsLog10 = Math.log10(mantissa) + exp;
                break;
            }
        }
        if (crystalsLog10 <= 0) return 1.0;
        return Math.pow(10, coreLevel * coreCoeff * crystalsLog10);
    }

    private PlayerResource findResource(List<PlayerResource> resources, Long resourceId) {
        return resources.stream()
                .filter(r -> r.getResource().getId().equals(resourceId))
                .findFirst()
                .orElse(null);
    }
}
