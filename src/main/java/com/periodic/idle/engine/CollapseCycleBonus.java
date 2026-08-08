package com.periodic.idle.engine;

import com.periodic.idle.engine.config.CollapseCycleBonusProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Тір 1: перманентний буст від кількості виконаних колапсів матерії
 * ({@code save.matterCollapses}, ніколи не скидається, на відміну від VC —
 * розділ 7.6 CLAUDE.md). Кожен колапс матерії — умовно новий цикл "народження
 * Всесвіту" (баріогенез з нуля), і накопичений досвід цих циклів сам по собі
 * дає перманентну перевагу — незалежно від того, що Кристали Пустоти (і
 * побудований на них буст Ядра) обнуляються щоразу.
 *
 * <p>Без цього другий і наступні колапси матерії практично недосяжні: живе
 * бот-тестування (docs/balance.md, V17/V18) показало, що без VC виробництво
 * природно впирається у стелю ~1e23-24 — CORE (єдиний по-справжньому
 * експоненційний рушій) дає нуль ефекту без Кристалів. CollapseCycleBonus —
 * другий, незалежний від VC, множник, що росте з кожним циклом.
 *
 * <p>Формула — той самий tanh-softcap патерн, що й {@code calcCoreBoost}
 * (UpgradeMultipliers), лише вхід інший (log10(matterCollapses+1) замість
 * log10(VC)): математично гарантована стеля, ніякого ризику переповнення
 * double незалежно від того, скільки триватиме "довга гра".
 *
 * <p>Коефіцієнти — {@link CollapseCycleBonusProperties} ({@code balance.collapse-cycle-bonus.*}
 * у application.yml), не Java-константи.
 */
@Component
@RequiredArgsConstructor
public class CollapseCycleBonus {

    private final CollapseCycleBonusProperties props;

    /** Після стількох колапсів VC більше не скидається колапсом (MatterService). */
    public long vcPersistsAfterCollapses() {
        return props.vcPersistsAfterCollapses();
    }

    /** Множник виробництва від кількості колапсів. 0 колапсів -> 1.0. */
    public double boost(long matterCollapses) {
        if (matterCollapses <= 0) return 1.0;
        double rawExponent = props.coeff() * Math.log10(matterCollapses + 1.0);
        if (!Double.isFinite(rawExponent) || rawExponent <= 0) return 1.0;

        double effectiveExponent = rawExponent;
        if (rawExponent > props.expSoftcap()) {
            double excess = rawExponent - props.expSoftcap();
            effectiveExponent = props.expSoftcap() + props.expRange() * Math.tanh(excess / props.expRange());
        }
        double result = Math.pow(10, effectiveExponent);
        return Double.isFinite(result) ? result : 1.0;
    }
}
