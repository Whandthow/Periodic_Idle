package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** ExchangeService: кап розщеплення кристалів за раз. */
@ConfigurationProperties(prefix = "balance.exchange")
public record ExchangeProperties(
        int bulkHardCap
) {
}
