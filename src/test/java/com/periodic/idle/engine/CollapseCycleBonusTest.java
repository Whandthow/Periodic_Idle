package com.periodic.idle.engine;

import com.periodic.idle.engine.config.CollapseCycleBonusProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollapseCycleBonusTest {

    private final CollapseCycleBonus bonus = new CollapseCycleBonus(
            new CollapseCycleBonusProperties(2.5, 10.0, 10.0, 10_000L));

    @Test
    @DisplayName("boost: 0 колапсів -> 1.0 (без ефекту)")
    void boost_zeroCollapses_returnsOne() {
        assertEquals(1.0, bonus.boost(0), 1e-9);
    }

    @Test
    @DisplayName("boost: 9 колапсів (лінійна ділянка) -> 10^2.5")
    void boost_belowSoftcap_linear() {
        // rawExponent = 2.5 * log10(10) = 2.5, < softcap (10) -> без tanh.
        assertEquals(Math.pow(10, 2.5), bonus.boost(9), 1e-6);
    }

    @Test
    @DisplayName("boost: VC_PERSISTS_AFTER_COLLAPSES (10 000 колапсів) -> скінченний, обмежений буст")
    void boost_atVcPersistThreshold_finiteAndBounded() {
        double boost = bonus.boost(bonus.vcPersistsAfterCollapses());
        assertTrue(Double.isFinite(boost));
        // Стеля: 10^(10+10) = 1e20 (асимптота, ніколи не досягається точно).
        assertTrue(boost < 1e20, "буст має бути суворо обмежений softcap-стелею");
        assertTrue(boost > 1e9, "буст на порозі 10 000 колапсів має бути суттєво великим");
    }

    @Test
    @DisplayName("boost: монотонно зростає з кількістю колапсів")
    void boost_monotonicallyIncreasing() {
        double b1 = bonus.boost(1);
        double b10 = bonus.boost(10);
        double b1000 = bonus.boost(1000);
        assertTrue(b1 < b10);
        assertTrue(b10 < b1000);
    }

    @Test
    @DisplayName("boost: від'ємна кількість колапсів (захист) -> 1.0")
    void boost_negativeCollapses_safe() {
        assertEquals(1.0, bonus.boost(-5), 1e-9);
    }
}
