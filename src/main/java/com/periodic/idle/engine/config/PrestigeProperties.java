package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Формула престижу (PrestigeService, розділ 7.4 CLAUDE.md). */
@ConfigurationProperties(prefix = "balance.prestige")
public record PrestigeProperties(
        double minLog10Energy,
        double minLog10Growth,
        double divisor,
        double starterEnergyNumber,
        long starterEnergyExponent
) {
}
