package com.periodic.idle.engine;

import com.periodic.idle.player.PlayerResource;

import java.util.List;

/**
 * Пасивні бонуси Tier 1 (накопичені протони/нейтрони/електрони) до механік Tier 0.
 * Діють завжди, незалежно від апгрейдів: кожна частинка на руках дає невеликий буст.
 */
public final class ParticleBonus {

    /** +25% до ENERGY_MULT-подібного множника енергії за кожен протон. */
    public static final double PROTON_ENERGY_PER = 0.25;
    /** -0.02 до cost-multiplier генераторів за кожен нейтрон. */
    public static final double NEUTRON_COST_PER = 0.02;
    /** +15% до множника кристалів при престижі за кожен електрон. */
    public static final double ELECTRON_VC_PER = 0.15;

    private ParticleBonus() {
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

    /** Множник енергії від протонів: 1 + count(p) * PROTON_ENERGY_PER. */
    public static double protonEnergyMult(List<PlayerResource> resources) {
        return 1.0 + count(resources, "p") * PROTON_ENERGY_PER;
    }

    /** Знижка cost-multiplier від нейтронів (застосовується підлогою назовні). */
    public static double neutronCostReduction(List<PlayerResource> resources) {
        return count(resources, "n") * NEUTRON_COST_PER;
    }

    /** Множник кристалів при престижі від електронів: 1 + count(e) * ELECTRON_VC_PER. */
    public static double electronCrystalMult(List<PlayerResource> resources) {
        return 1.0 + count(resources, "e") * ELECTRON_VC_PER;
    }
}
