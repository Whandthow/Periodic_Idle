package com.periodic.idle.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollapseCycleBonusTest {

    @Test
    @DisplayName("boost: 0 колапсів -> 1.0 (без ефекту)")
    void boost_zeroCollapses_returnsOne() {
        assertEquals(1.0, CollapseCycleBonus.boost(0), 1e-9);
    }

    @Test
    @DisplayName("boost: 9 колапсів (лінійна ділянка) -> 10^2.5")
    void boost_belowSoftcap_linear() {
        // rawExponent = 2.5 * log10(10) = 2.5, < softcap (10) -> без tanh.
        assertEquals(Math.pow(10, 2.5), CollapseCycleBonus.boost(9), 1e-6);
    }

    @Test
    @DisplayName("boost: VC_PERSISTS_AFTER_COLLAPSES (10 000 колапсів) -> скінченний, обмежений буст")
    void boost_atVcPersistThreshold_finiteAndBounded() {
        double boost = CollapseCycleBonus.boost(CollapseCycleBonus.VC_PERSISTS_AFTER_COLLAPSES);
        assertTrue(Double.isFinite(boost));
        // Стеля: 10^(10+10) = 1e20 (асимптота, ніколи не досягається точно).
        assertTrue(boost < 1e20, "буст має бути суворо обмежений softcap-стелею");
        assertTrue(boost > 1e9, "буст на порозі 10 000 колапсів має бути суттєво великим");
    }

    @Test
    @DisplayName("boost: монотонно зростає з кількістю колапсів")
    void boost_monotonicallyIncreasing() {
        double b1 = CollapseCycleBonus.boost(1);
        double b10 = CollapseCycleBonus.boost(10);
        double b1000 = CollapseCycleBonus.boost(1000);
        assertTrue(b1 < b10);
        assertTrue(b10 < b1000);
    }

    @Test
    @DisplayName("boost: від'ємна кількість колапсів (захист) -> 1.0")
    void boost_negativeCollapses_safe() {
        assertEquals(1.0, CollapseCycleBonus.boost(-5), 1e-9);
    }
}
