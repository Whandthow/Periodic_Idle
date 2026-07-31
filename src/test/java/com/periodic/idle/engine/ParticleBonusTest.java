package com.periodic.idle.engine;

import com.periodic.idle.content.Resource;
import com.periodic.idle.player.PlayerResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParticleBonusTest {

    @Test
    @DisplayName("count: рахує кількість частинки за кодом ресурсу")
    void count_readsWholeAmount() {
        PlayerResource p = makePlayerResource("p", 5.0, 0);
        assertEquals(5L, ParticleBonus.count(List.of(p), "p"));
    }

    @Test
    @DisplayName("count: відсутня частинка -> 0")
    void count_missing_returnsZero() {
        PlayerResource p = makePlayerResource("p", 5.0, 0);
        assertEquals(0L, ParticleBonus.count(List.of(p), "n"));
    }

    @Test
    @DisplayName("protonEnergyMult: 1 + count * 0.25")
    void protonEnergyMult_formula() {
        PlayerResource p = makePlayerResource("p", 5.0, 0);
        assertEquals(2.25, ParticleBonus.protonEnergyMult(List.of(p)), 1e-9);
    }

    @Test
    @DisplayName("protonEnergyMult: без протонів -> 1.0")
    void protonEnergyMult_noParticles_returnsOne() {
        assertEquals(1.0, ParticleBonus.protonEnergyMult(List.of()), 1e-9);
    }

    @Test
    @DisplayName("neutronCostReduction: count * 0.02")
    void neutronCostReduction_formula() {
        PlayerResource n = makePlayerResource("n", 2.0, 1); // 20 нейтронів
        assertEquals(0.4, ParticleBonus.neutronCostReduction(List.of(n)), 1e-9);
    }

    @Test
    @DisplayName("electronCrystalMult: 1 + count * 0.15")
    void electronCrystalMult_formula() {
        PlayerResource e = makePlayerResource("e", 2.0, 1); // 20 електронів
        assertEquals(4.0, ParticleBonus.electronCrystalMult(List.of(e)), 1e-9);
    }

    private PlayerResource makePlayerResource(String code, double number, long exponent) {
        Resource r = instantiate(Resource.class);
        ReflectionTestUtils.setField(r, "code", code);
        PlayerResource pr = instantiate(PlayerResource.class);
        pr.setResource(r);
        pr.setNumber(number);
        pr.setExponent(exponent);
        return pr;
    }

    @SuppressWarnings("unchecked")
    private <T> T instantiate(Class<T> clazz) {
        try {
            var c = clazz.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
