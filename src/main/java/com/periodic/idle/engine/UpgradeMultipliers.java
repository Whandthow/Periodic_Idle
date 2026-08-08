package com.periodic.idle.engine;

import com.periodic.idle.engine.config.UpgradeMultiplierProperties;
import com.periodic.idle.player.PlayerGenerator;
import com.periodic.idle.player.PlayerResource;
import com.periodic.idle.player.PlayerUpgrade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Чисті обчислення множників, які дають апгрейди Тіру 0, з поточного стану
 * (рівнів апгрейдів, генераторів, ресурсів) — винесено з {@link GameEngine},
 * щоб сам движок (tick/processSave/production) лишався зосередженим на
 * оркестрації, а не на формулах кожного окремого {@code effect_type}
 * (розділ 7.1 CLAUDE.md). Той самий "Spring-керований stateless бін" патерн,
 * що й {@link ParticleBonus}/{@link ElementBonus}/{@link CollapseCycleBonus}.
 *
 * <p>Softcap-константи — {@link UpgradeMultiplierProperties} ({@code balance.upgrade-multipliers.*}
 * у application.yml), не Java-константи, щоб баланс можна було міняти без recompile.
 */
@Component
@RequiredArgsConstructor
public class UpgradeMultipliers {

    private final UpgradeMultiplierProperties props;

    /** Загальний лінійний множник: 1 + value*level, сумований по всіх рівнях effectType. */
    public double calcMultiplier(List<PlayerUpgrade> upgrades, String effectType) {
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
    public double calcEnergyMult(List<PlayerUpgrade> upgrades) {
        double mult = 1.0;
        int softcapThreshold = props.energyMultSoftcapThreshold();
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if (!"ENERGY_MULT".equals(pu.getUpgrade().getEffectType())) continue;
            int level = pu.getLevel();
            double effective = level;
            if (level > softcapThreshold) {
                effective = softcapThreshold + Math.sqrt(level - softcapThreshold);
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
    public Map<Long, Double> calcPhantomBonus(List<PlayerUpgrade> upgrades,
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
    public Map<Long, Double> calcGenStackMults(List<PlayerUpgrade> upgrades,
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
    public double calcEnergyPow(List<PlayerUpgrade> upgrades) {
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
    public Map<Long, Double> calcGenSpecificMults(List<PlayerUpgrade> upgrades,
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
    public double calcCoreBoost(List<PlayerUpgrade> upgrades, List<PlayerResource> resources) {
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
                // Захист від NaN/Infinity у DB: трактуємо як 0 кристалів.
                if (!Double.isFinite(mantissa) || mantissa <= 0) break;
                crystalsLog10 = Math.log10(mantissa) + exp;
                break;
            }
        }
        if (!Double.isFinite(crystalsLog10) || crystalsLog10 <= 0) return 1.0;
        double rawExponent = coreLevel * coreCoeff * crystalsLog10;
        if (!Double.isFinite(rawExponent) || rawExponent <= 0) return 1.0;
        // Softcap на експоненту: до порогу — лінійно, після — tanh-асимптота, що
        // ніколи не перевищує coreExpSoftcap + coreExpRange (100 < 308).
        // Це не дає Math.pow(10, ...) overflow-нути в Infinity і прибирає cliff,
        // де раніше буст міг впасти з ~1e300 до 1.0 (sqrt-softcap сповільнював ріст,
        // але не мав стелі — рано чи пізно все одно впирався в 308).
        double effectiveExponent = rawExponent;
        double softcap = props.coreExpSoftcap();
        double range = props.coreExpRange();
        if (rawExponent > softcap) {
            double excess = rawExponent - softcap;
            effectiveExponent = softcap + range * Math.tanh(excess / range);
        }
        double result = Math.pow(10, effectiveExponent);
        return Double.isFinite(result) ? result : 1.0;
    }
}
