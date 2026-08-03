package com.periodic.idle.engine;

import com.periodic.idle.player.PlayerResource;

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
 * {@link #SATURATION_SCALE} додаткові частинки дають дедалі менше — і бонус ніколи
 * не перевищує {@code PER * SATURATION_SCALE}, що заразом убезпечує множники
 * виробництва (GameEngine) від переповнення double при астрономічній кількості
 * частинок, яка стає досяжною після Break Infinity.</p>
 */
public final class ParticleBonus {

    /** +25% до ENERGY_MULT-подібного множника енергії за кожен "ефективний" протон. */
    public static final double PROTON_ENERGY_PER = 0.25;
    /** -0.02 до cost-multiplier генераторів за кожен "ефективний" нейтрон. */
    public static final double NEUTRON_COST_PER = 0.02;
    /** +15% до множника кристалів при престижі за кожен "ефективний" електрон. */
    public static final double ELECTRON_VC_PER = 0.15;

    /**
     * Масштаб насичення: кількість частинок, після якої крива відчутно вигинається.
     * Для count &lt;&lt; SATURATION_SCALE ефект ≈ лінійний (як і раніше); для
     * count → ∞ ефект → SATURATION_SCALE (жорстка асимптота).
     */
    private static final double SATURATION_SCALE = 1_000.0;

    private ParticleBonus() {
    }

    /** "Ефективна" кількість частинок після насичення: count / (1 + count/SATURATION_SCALE). */
    private static double saturating(long count) {
        double c = (double) count;
        return c / (1.0 + c / SATURATION_SCALE);
    }

    /** Ціла кількість частинки заданого коду (p/n/e) як BigNum -> long. */
    public static long count(List<PlayerResource> resources, String code) {
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

    /** Множник енергії від протонів: 1 + saturating(count(p)) * PROTON_ENERGY_PER. */
    public static double protonEnergyMult(List<PlayerResource> resources) {
        return 1.0 + saturating(count(resources, "p")) * PROTON_ENERGY_PER;
    }

    /** Знижка cost-multiplier від нейтронів (застосовується підлогою назовні). */
    public static double neutronCostReduction(List<PlayerResource> resources) {
        return saturating(count(resources, "n")) * NEUTRON_COST_PER;
    }

    /** Множник кристалів при престижі від електронів: 1 + saturating(count(e)) * ELECTRON_VC_PER. */
    public static double electronCrystalMult(List<PlayerResource> resources) {
        return 1.0 + saturating(count(resources, "e")) * ELECTRON_VC_PER;
    }
}
