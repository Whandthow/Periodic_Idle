package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Зорі головної послідовності (StarService, розділ 7.11 CLAUDE.md). */
@ConfigurationProperties(prefix = "balance.star")
public record StarProperties(
        long tickIntervalMs,
        long energyScaleExponent,
        int hydrogenAtomicNumber,
        int heliumAtomicNumber
) {
    public double tickIntervalSec() {
        return tickIntervalMs / 1000.0;
    }
}
