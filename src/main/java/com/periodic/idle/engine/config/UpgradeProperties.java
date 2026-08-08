package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** UpgradeService: кап bulk-купівлі апгрейдів. */
@ConfigurationProperties(prefix = "balance.upgrade")
public record UpgradeProperties(
        int bulkHardCap
) {
}
