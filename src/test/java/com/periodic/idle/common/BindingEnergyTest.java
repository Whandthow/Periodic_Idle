package com.periodic.idle.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BindingEnergyTest {

    @Test
    @DisplayName("Одинокий протон (H, Z=1, A=1) не має енергії зв'язку")
    void hydrogen_noBindingEnergy() {
        assertEquals(0.0, BindingEnergy.totalMeV(1, 1));
    }

    @Test
    @DisplayName("Гелій (Z=2, A=4): орієнтовно ~28 МеВ (реальне значення 28.3 МеВ)")
    void helium_approximatelyRealValue() {
        double be = BindingEnergy.totalMeV(2, 4);
        assertTrue(be > 20 && be < 35, "очікували ~28 МеВ для He-4, отримали " + be);
    }

    @Test
    @DisplayName("Питома енергія зв'язку на нуклон зростає від легких елементів до заліза")
    void perNucleon_increasesTowardIron() {
        double heliumPerA = BindingEnergy.perNucleonMeV(2, 4);
        double carbonPerA = BindingEnergy.perNucleonMeV(6, 12);
        double ironPerA = BindingEnergy.perNucleonMeV(26, 56);

        assertTrue(carbonPerA > heliumPerA,
                "вуглець має вищу питому енергію зв'язку, ніж гелій: " + carbonPerA + " vs " + heliumPerA);
        assertTrue(ironPerA > carbonPerA,
                "залізо має вищу питому енергію зв'язку, ніж вуглець: " + ironPerA + " vs " + carbonPerA);
    }

    @Test
    @DisplayName("Пік питомої енергії зв'язку — біля заліза-56 (~8.5-9.0 МеВ/нуклон)")
    void ironPeak_matchesRealPhysics() {
        double ironPerA = BindingEnergy.perNucleonMeV(26, 56);
        assertTrue(ironPerA > 8.0 && ironPerA < 9.5,
                "очікували ~8.8 МеВ/нуклон для заліза-56, отримали " + ironPerA);
    }

    @Test
    @DisplayName("Питома енергія зв'язку спадає після заліза (криптон < залізо)")
    void perNucleon_declinesAfterIron() {
        double ironPerA = BindingEnergy.perNucleonMeV(26, 56);
        double kryptonPerA = BindingEnergy.perNucleonMeV(36, 84);

        assertTrue(kryptonPerA < ironPerA,
                "криптон повинен мати нижчу питому енергію зв'язку, ніж залізо (пік): "
                        + kryptonPerA + " vs " + ironPerA);
    }

    @Test
    @DisplayName("totalMeV ніколи не від'ємне")
    void totalMeV_neverNegative() {
        for (int z = 1; z <= 36; z++) {
            for (int a = z; a <= z * 3; a++) {
                double be = BindingEnergy.totalMeV(z, a);
                assertTrue(be >= 0, "Z=" + z + " A=" + a + " дав від'ємну енергію зв'язку: " + be);
            }
        }
    }
}
