package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AutoBuyService: інтервал автокупівлі генераторів. */
@ConfigurationProperties(prefix = "balance.auto-buy")
public record AutoBuyProperties(
        long intervalMs
) {
}
