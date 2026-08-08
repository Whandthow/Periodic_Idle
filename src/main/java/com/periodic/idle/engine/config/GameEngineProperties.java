package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tick loop і кап енергії (GameEngine, розділ 7.2 CLAUDE.md).
 * Значення живуть у {@code application.yml} під {@code balance.game-engine.*}.
 */
@ConfigurationProperties(prefix = "balance.game-engine")
public record GameEngineProperties(
        long tickIntervalMs,
        long infiniteRateExponentJump,
        long energyCapExponent,
        double offlineMinSeconds,
        double offlineMaxSeconds
) {
    public double tickIntervalSec() {
        return tickIntervalMs / 1000.0;
    }
}
