package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.Generator;
import com.periodic.idle.content.Resource;
import com.periodic.idle.content.Upgrade;
import com.periodic.idle.player.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrestigeServiceTest {

    @Mock private PlayerResourceRepository playerResourceRepository;
    @Mock private PlayerGeneratorRepository playerGeneratorRepository;
    @Mock private PlayerUpgradeRepository playerUpgradeRepository;
    @Mock private SaveRepository saveRepository;

    @InjectMocks
    private PrestigeService prestigeService;

    private PlayerResource energy;
    private PlayerResource crystals;

    @BeforeEach
    void setUp() {
        Resource energyRes = instantiate(Resource.class);
        ReflectionTestUtils.setField(energyRes, "id", 1L);
        ReflectionTestUtils.setField(energyRes, "code", "E");

        Resource vcRes = instantiate(Resource.class);
        ReflectionTestUtils.setField(vcRes, "id", 2L);
        ReflectionTestUtils.setField(vcRes, "code", "VC");

        energy = instantiate(PlayerResource.class);
        energy.setResource(energyRes);
        energy.setNumber(1.0);
        energy.setExponent(0);

        crystals = instantiate(PlayerResource.class);
        crystals.setResource(vcRes);
        crystals.setNumber(0);
        crystals.setExponent(0);
    }

    @Test
    @DisplayName("Energy < 1e9: 0 кристалів")
    void calcPotentialGain_belowThreshold() {
        energy.setNumber(5.0);
        energy.setExponent(8); // 5e8
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy, crystals));

        BigNum gain = prestigeService.calcPotentialGain(1L);

        assertEquals(0, gain.getNumber(), 0.001);
        assertEquals(0, gain.getExponent());
    }

    @Test
    @DisplayName("Energy = 1e9: baseLog10 = 1 → 10 кристалів")
    void calcPotentialGain_atThreshold() {
        energy.setNumber(1.0);
        energy.setExponent(9);
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy, crystals));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        BigNum gain = prestigeService.calcPotentialGain(1L);

        // base_log10 = (9-9)/2 + 1 = 1 → 10^1 = 10
        assertEquals(1.0, gain.getNumber(), 0.01);
        assertEquals(1, gain.getExponent());
    }

    @Test
    @DisplayName("Energy = 1e13: base_log10 = 3 → 1000 кристалів")
    void calcPotentialGain_higherEnergy() {
        energy.setNumber(1.0);
        energy.setExponent(13);
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy, crystals));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        BigNum gain = prestigeService.calcPotentialGain(1L);

        // base_log10 = (13-9)/2 + 1 = 3 → 10^3 = 1000
        assertEquals(1.0, gain.getNumber(), 0.01);
        assertEquals(3, gain.getExponent());
    }

    @Test
    @DisplayName("CRYSTAL_GAIN: multiplier збільшує приріст")
    void calcPotentialGain_withCrystalGain() {
        energy.setNumber(1.0);
        energy.setExponent(9);
        Upgrade cg = instantiate(Upgrade.class);
        ReflectionTestUtils.setField(cg, "effectType", "CRYSTAL_GAIN");
        ReflectionTestUtils.setField(cg, "effectValue", 0.1);
        PlayerUpgrade pu = new PlayerUpgrade();
        pu.setUpgrade(cg);
        pu.setLevel(10); // mult = 1 + 0.1*10 = 2.0

        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy, crystals));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(pu));

        BigNum gain = prestigeService.calcPotentialGain(1L);

        // base=10, mult=2 → 20
        double total = gain.getNumber() * Math.pow(10, gain.getExponent());
        assertEquals(20.0, total, 0.5);
    }

    @Test
    @DisplayName("prestige(): скидає енергію та рівні генераторів, додає кристали")
    void prestige_resetsState() {
        energy.setNumber(1.0);
        energy.setExponent(11); // base_log10 = (11-9)/2+1 = 2 → 100 кристалів

        Generator g = instantiate(Generator.class);
        ReflectionTestUtils.setField(g, "id", 1L);
        PlayerGenerator pg = instantiate(PlayerGenerator.class);
        pg.setGenerator(g);
        pg.setLevel(42);

        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy, crystals));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(pg));

        BigNum gain = prestigeService.prestige(1L);

        // Після resetu — стартова енергія 10 (1.0 * 10^1)
        assertEquals(PrestigeService.STARTER_ENERGY_NUMBER, energy.getNumber(), 0.001);
        assertEquals(PrestigeService.STARTER_ENERGY_EXPONENT, energy.getExponent());
        assertEquals(0, pg.getLevel());
        // crystals ~= 100
        double crystalsTotal = crystals.getNumber() * Math.pow(10, crystals.getExponent());
        assertEquals(100.0, crystalsTotal, 1.0);
    }

    @Test
    @DisplayName("prestige() при недостатній енергії кидає помилку")
    void prestige_rejectsBelowThreshold() {
        energy.setNumber(1.0);
        energy.setExponent(5);
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy, crystals));

        assertThrows(RuntimeException.class, () -> prestigeService.prestige(1L));
    }

    // === hardReset ===

    @Test
    @DisplayName("hardReset: E -> стартова, VC -> 0, генератори та апгрейди -> 0")
    void hardReset_resetsEverything() {
        energy.setNumber(7.0);
        energy.setExponent(50);
        crystals.setNumber(5.0);
        crystals.setExponent(10);

        Generator g = instantiate(Generator.class);
        ReflectionTestUtils.setField(g, "id", 1L);
        PlayerGenerator pg = instantiate(PlayerGenerator.class);
        pg.setGenerator(g);
        pg.setLevel(123);

        Upgrade u = instantiate(Upgrade.class);
        ReflectionTestUtils.setField(u, "id", 9L);
        ReflectionTestUtils.setField(u, "effectType", "ENERGY_MULT");
        PlayerUpgrade pu = new PlayerUpgrade();
        pu.setUpgrade(u);
        pu.setLevel(7);

        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy, crystals));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(pg));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(pu));

        prestigeService.hardReset(1L);

        assertEquals(PrestigeService.STARTER_ENERGY_NUMBER, energy.getNumber(), 0.001);
        assertEquals(PrestigeService.STARTER_ENERGY_EXPONENT, energy.getExponent());
        assertEquals(0, crystals.getNumber(), 0.001);
        assertEquals(0, crystals.getExponent());
        assertEquals(0, pg.getLevel());
        assertEquals(0, pu.getLevel());
    }

    @Test
    @DisplayName("hardReset: без VC ресурсу в БД не падає (skip)")
    void hardReset_noCrystals_ok() {
        energy.setNumber(1.0);
        energy.setExponent(20);

        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        assertDoesNotThrow(() -> prestigeService.hardReset(1L));
        assertEquals(PrestigeService.STARTER_ENERGY_NUMBER, energy.getNumber(), 0.001);
        assertEquals(PrestigeService.STARTER_ENERGY_EXPONENT, energy.getExponent());
    }

    @Test
    @DisplayName("hardReset: без E — кидає помилку")
    void hardReset_noEnergy_throws() {
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(crystals));

        assertThrows(RuntimeException.class, () -> prestigeService.hardReset(1L));
    }

    @Test
    @DisplayName("hardReset: p/n/e обнуляються; brokenInfinity і matterCollapses скидаються на save")
    void hardReset_resetsMatterState() {
        Resource pRes = instantiate(Resource.class);
        ReflectionTestUtils.setField(pRes, "id", 3L);
        ReflectionTestUtils.setField(pRes, "code", "p");
        PlayerResource protons = instantiate(PlayerResource.class);
        protons.setResource(pRes);
        protons.setNumber(3.0);
        protons.setExponent(2); // 300 протонів

        Resource nRes = instantiate(Resource.class);
        ReflectionTestUtils.setField(nRes, "code", "n");
        PlayerResource neutrons = instantiate(PlayerResource.class);
        neutrons.setResource(nRes);
        neutrons.setNumber(1.0);
        neutrons.setExponent(0);

        Save save = instantiate(Save.class);
        ReflectionTestUtils.setField(save, "id", 1L);
        save.setBrokenInfinity(true);
        save.setMatterCollapses(7L);

        when(playerResourceRepository.findBySaveId(1L))
                .thenReturn(List.of(energy, crystals, protons, neutrons));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(saveRepository.findById(1L)).thenReturn(java.util.Optional.of(save));

        prestigeService.hardReset(1L);

        assertEquals(0.0, protons.getNumber(), 0.0001);
        assertEquals(0L, protons.getExponent());
        assertEquals(0.0, neutrons.getNumber(), 0.0001);
        assertEquals(0L, neutrons.getExponent());
        assertFalse(save.isBrokenInfinity());
        assertEquals(0L, save.getMatterCollapses());
        // Енергія повертається до стартової — старий тест уже це покриває, але швидка перевірка:
        assertEquals(PrestigeService.STARTER_ENERGY_NUMBER, energy.getNumber(), 0.001);
    }

    @Test
    @DisplayName("Електрони бустять crystalGain (electronCrystalMult)")
    void calcPotentialGain_electronBoost() {
        energy.setNumber(1.0);
        energy.setExponent(9); // base = 10 кристалів

        Resource eRes = instantiate(Resource.class);
        ReflectionTestUtils.setField(eRes, "code", "e");
        PlayerResource electrons = instantiate(PlayerResource.class);
        electrons.setResource(eRes);
        electrons.setNumber(2.0);
        electrons.setExponent(1); // 20 електронів → +100% (mult=2.0)

        when(playerResourceRepository.findBySaveId(1L))
                .thenReturn(List.of(energy, crystals, electrons));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        BigNum gain = prestigeService.calcPotentialGain(1L);

        // Очікуємо ~20 кристалів (10 base × 2.0 електрон-мульт).
        double total = gain.getNumber() * Math.pow(10, gain.getExponent());
        assertEquals(20.0, total, 0.5);
    }

    @SuppressWarnings("unchecked")
    private <T> T instantiate(Class<T> clazz) {
        try {
            var c = clazz.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
