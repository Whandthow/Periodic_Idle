package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AutoUpgradeService: інтервал автопокупки апгрейдів Тіру 0 і поріг розблокування в UI. */
@ConfigurationProperties(prefix = "balance.auto-upgrade")
public record AutoUpgradeProperties(
        long intervalMs,
        long unlockCollapses
) {
}
