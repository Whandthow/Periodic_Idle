package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AutoSynthesizeService: інтервал автосинтезу елементів + молекул. */
@ConfigurationProperties(prefix = "balance.auto-synthesize")
public record AutoSynthesizeProperties(
        long intervalMs
) {
}
