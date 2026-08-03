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
    @DisplayName("protonEnergyMult: 1 + saturating(count) * 0.25, count=5 << SATURATION_SCALE -> ≈лінійно")
    void protonEnergyMult_formula() {
        PlayerResource p = makePlayerResource("p", 5.0, 0);
        // saturating(5) = 5 / (1 + 5/1000) = 4.97512437...; mult = 1 + 0.25 * 4.97512437...
        assertEquals(2.2437810945273633, ParticleBonus.protonEnergyMult(List.of(p)), 1e-9);
    }

    @Test
    @DisplayName("protonEnergyMult: без протонів -> 1.0")
    void protonEnergyMult_noParticles_returnsOne() {
        assertEquals(1.0, ParticleBonus.protonEnergyMult(List.of()), 1e-9);
    }

    @Test
    @DisplayName("protonEnergyMult: насичення — стеля 1 + 0.25*1000 при астрономічній кількості протонів")
    void protonEnergyMult_saturatesAtHighCount() {
        PlayerResource p = makePlayerResource("p", 9.223372036854776, 18); // count -> Long.MAX_VALUE
        double mult = ParticleBonus.protonEnergyMult(List.of(p));
        assertTrue(Double.isFinite(mult));
        assertTrue(mult < 1.0 + 0.25 * 1_000.0 + 1e-6, "мультиплікатор має бути обмежений стелею насичення");
    }

    @Test
    @DisplayName("neutronCostReduction: saturating(count) * 0.02, count=20 << SATURATION_SCALE -> ≈лінійно")
    void neutronCostReduction_formula() {
        PlayerResource n = makePlayerResource("n", 2.0, 1); // 20 нейтронів
        // saturating(20) = 20 / (1 + 20/1000) = 19.60784314...; reduction = 0.02 * 19.60784314...
        assertEquals(0.39215686274509803, ParticleBonus.neutronCostReduction(List.of(n)), 1e-9);
    }

    @Test
    @DisplayName("electronCrystalMult: 1 + saturating(count) * 0.15, count=20 << SATURATION_SCALE -> ≈лінійно")
    void electronCrystalMult_formula() {
        PlayerResource e = makePlayerResource("e", 2.0, 1); // 20 електронів
        // saturating(20) = 19.60784314...; mult = 1 + 0.15 * 19.60784314...
        assertEquals(3.9411764705882355, ParticleBonus.electronCrystalMult(List.of(e)), 1e-9);
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
