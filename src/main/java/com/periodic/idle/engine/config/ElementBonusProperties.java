package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Пасивні бонуси Тіру 2 -> Тір 0/1, включно з CNO-каталізом (ElementBonus, розділ 7.1 CLAUDE.md). */
@ConfigurationProperties(prefix = "balance.element-bonus")
public record ElementBonusProperties(
        double diversityPer,
        double diversitySaturationScale,
        double atomCountPer,
        double atomSoftcapLog10,
        double atomHardRangeLog10,
        double cnoCatalystMult
) {
}
