package com.periodic.idle.engine;

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
 * (GameEngine), лише вхід інший (log10(matterCollapses+1) замість
 * log10(VC)): математично гарантована стеля, ніякого ризику переповнення
 * double незалежно від того, скільки триватиме "довга гра".
 */
public final class CollapseCycleBonus {

    /** Коефіцієнт: сира експонента = COEFF * log10(matterCollapses + 1). */
    public static final double COEFF = 2.5;

    /** Softcap: до цього порогу експонента росте лінійно. */
    private static final double EXP_SOFTCAP = 10.0;

    /** Ширина асимптоти: ефективна експонента ніколи не перевищує SOFTCAP + RANGE (20). */
    private static final double EXP_RANGE = 10.0;

    /**
     * Після стількох колапсів VC більше не скидається колапсом (MatterService) —
     * гравець "довів" накопичений цикл-досвід і отримує право на постійний VC-снігова-ком.
     */
    public static final long VC_PERSISTS_AFTER_COLLAPSES = 10_000L;

    private CollapseCycleBonus() {
    }

    /** Множник виробництва від кількості колапсів. 0 колапсів -> 1.0. */
    public static double boost(long matterCollapses) {
        if (matterCollapses <= 0) return 1.0;
        double rawExponent = COEFF * Math.log10(matterCollapses + 1.0);
        if (!Double.isFinite(rawExponent) || rawExponent <= 0) return 1.0;

        double effectiveExponent = rawExponent;
        if (rawExponent > EXP_SOFTCAP) {
            double excess = rawExponent - EXP_SOFTCAP;
            effectiveExponent = EXP_SOFTCAP + EXP_RANGE * Math.tanh(excess / EXP_RANGE);
        }
        double result = Math.pow(10, effectiveExponent);
        return Double.isFinite(result) ? result : 1.0;
    }
}
