package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Формули множників з апгрейдів Тіру 0 (UpgradeMultipliers, розділ 7.1 CLAUDE.md). */
@ConfigurationProperties(prefix = "balance.upgrade-multipliers")
public record UpgradeMultiplierProperties(
        int energyMultSoftcapThreshold,
        double coreExpSoftcap,
        double coreExpRange
) {
}
