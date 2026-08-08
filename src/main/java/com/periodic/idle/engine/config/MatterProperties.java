package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MatterService: скільки колапсів матерії потрібно для Break Infinity. */
@ConfigurationProperties(prefix = "balance.matter")
public record MatterProperties(
        long breakInfinityRequired
) {
}
