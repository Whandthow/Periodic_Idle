package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Синтез атомів (SynthesisService, розділ 7.7 CLAUDE.md): фази нуклеосинтезу, гейти, шкала енергії. */
@ConfigurationProperties(prefix = "balance.synthesis")
public record SynthesisProperties(
        int primordialMaxAtomicNumber,
        long stellarIgnitionHeliumCount,
        int ironAtomicNumber,
        long heavyElementHypernovaRequired,
        long energyScaleExponent,
        long bulkHardCap
) {
}
