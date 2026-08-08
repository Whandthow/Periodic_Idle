package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** GeneratorService: нижня межа cost-multiplier і кап bulk-купівлі. */
@ConfigurationProperties(prefix = "balance.generator")
public record GeneratorProperties(
        double minCostMultiplier,
        int bulkHardCap
) {
}
