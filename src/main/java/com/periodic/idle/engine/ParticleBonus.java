package com.periodic.idle.engine;

import com.periodic.idle.engine.config.ParticleBonusProperties;
import com.periodic.idle.player.PlayerResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Пасивні бонуси Tier 1 (накопичені протони/нейтрони/електрони) до механік Tier 0.
 * Діють завжди, незалежно від апгрейдів: кожна частинка на руках дає невеликий буст.
 *
 * <p>Бонус росте не лінійно з кількістю частинок, а за кривою насичення (те саме
 * математичне сімейство, що й ізотерма Ленгмюра / кінетика Міхаеліса-Ментен, і
 * концептуально споріднене з виродженням Фермі-газу — тиск густого газу частинок
 * росте із концентрацією, але асимптотично, не необмежено). При малій кількості
 * частинок крива майже лінійна (той самий "відчуття" першого прогресу), але після
 * {@code saturationScale} додаткові частинки дають дедалі менше — і бонус ніколи
 * не перевищує {@code PER * saturationScale}, що заразом убезпечує множники
 * виробництва (GameEngine) від переповнення double при астрономічній кількості
 * частинок, яка стає досяжною після Break Infinity.
 *
 * <p>Коефіцієнти — {@link ParticleBonusProperties} ({@code balance.particle-bonus.*}
 * у application.yml), не Java-константи — щоб баланс можна було швидко міняти без
 * зміни коду (CLAUDE.md, розділ "Ключові рішення").
 */
@Component
@RequiredArgsConstructor
public class ParticleBonus {

    private final ParticleBonusProperties props;

    /** "Ефективна" кількість частинок після насичення: count / (1 + count/saturationScale). */
    private double saturating(long count) {
        double c = (double) count;
        return c / (1.0 + c / props.saturationScale());
    }

    /** Ціла кількість частинки заданого коду (p/n/e) як BigNum -> long. */
    public long count(List<PlayerResource> resources, String code) {
        for (PlayerResource pr : resources) {
            if (pr.getResource() == null || !code.equals(pr.getResource().getCode())) continue;
            double mantissa = pr.getNumber();
            long exponent = pr.getExponent();
            if (!Double.isFinite(mantissa) || mantissa <= 0) return 0L;
            if (exponent >= 18) return Long.MAX_VALUE;
            double raw = mantissa * Math.pow(10, exponent);
            if (!Double.isFinite(raw) || raw >= (double) Long.MAX_VALUE) return Long.MAX_VALUE;
            return (long) Math.floor(raw);
        }
        return 0L;
    }

    /** Множник енергії від протонів: 1 + saturating(count(p)) * protonEnergyPer. */
    public double protonEnergyMult(List<PlayerResource> resources) {
        return 1.0 + saturating(count(resources, "p")) * props.protonEnergyPer();
    }

    /** Знижка cost-multiplier від нейтронів (застосовується підлогою назовні). */
    public double neutronCostReduction(List<PlayerResource> resources) {
        return saturating(count(resources, "n")) * props.neutronCostPer();
    }

    /** Множник кристалів при престижі від електронів: 1 + saturating(count(e)) * electronVcPer. */
    public double electronCrystalMult(List<PlayerResource> resources) {
        return 1.0 + saturating(count(resources, "e")) * props.electronVcPer();
    }

    /** Сирий коефіцієнт (не множник) — для відображення формули в UI (GameStatsService). */
    public double protonEnergyPer() {
        return props.protonEnergyPer();
    }

    public double neutronCostPer() {
        return props.neutronCostPer();
    }

    public double electronVcPer() {
        return props.electronVcPer();
    }
}
