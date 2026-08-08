package com.periodic.idle.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Буст від кількості колапсів матерії, незалежний від VC (CollapseCycleBonus, розділ 7.1 CLAUDE.md). */
@ConfigurationProperties(prefix = "balance.collapse-cycle-bonus")
public record CollapseCycleBonusProperties(
        double coeff,
        double expSoftcap,
        double expRange,
        long vcPersistsAfterCollapses
) {
}
