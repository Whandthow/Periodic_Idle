package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Пасивні бонуси Тіру 1 -> Тір 0 (ParticleBonus, розділ 7.1 CLAUDE.md). */
@ConfigurationProperties(prefix = "balance.particle-bonus")
public record ParticleBonusProperties(
        double saturationScale,
        double protonEnergyPer,
        double neutronCostPer,
        double electronVcPer
) {
}
