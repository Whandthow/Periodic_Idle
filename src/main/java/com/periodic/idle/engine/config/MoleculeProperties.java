package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Молекули (MoleculeService, розділ 7.9 CLAUDE.md): переведення еВ у ігрову шкалу енергії. */
@ConfigurationProperties(prefix = "balance.molecule")
public record MoleculeProperties(
        long bulkHardCap,
        double evPerMev,
        long energyScaleExponent
) {
}
