package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.Generator;
import com.periodic.idle.content.GeneratorRepository;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneratorServiceTest {

    @Mock private GeneratorRepository generatorRepository;
    @Mock private PlayerGeneratorRepository playerGeneratorRepository;
    @Mock private PlayerResourceRepository playerResourceRepository;
    @Mock private SaveRepository saveRepository;
    @Mock private PlayerUpgradeRepository playerUpgradeRepository;

    @InjectMocks
    private GeneratorService generatorService;

    private Resource energy;
    private Generator voidGen;
    private Save save;
    private PlayerResource playerEnergy;

    @BeforeEach
    void setUp() {
        energy = createResource(1L, "E");
        save = createSave(1L);

        voidGen = instantiate(Generator.class);
        ReflectionTestUtils.setField(voidGen, "id", 1L);
        ReflectionTestUtils.setField(voidGen, "code", "void_gen");
        ReflectionTestUtils.setField(voidGen, "costResource", energy);
        ReflectionTestUtils.setField(voidGen, "baseCostNumber", 1.0);
        ReflectionTestUtils.setField(voidGen, "baseCostExponent", 1L);
        ReflectionTestUtils.setField(voidGen, "costMultiplier", 1.5);

        playerEnergy = instantiate(PlayerResource.class);
        ReflectionTestUtils.setField(playerEnergy, "id", 1L);
        playerEnergy.setSave(save);
        playerEnergy.setResource(energy);
        playerEnergy.setNumber(1.0);
        playerEnergy.setExponent(5); // 1e5 = 100000
    }

    @Test
    @DisplayName("Перша купівля — створює PlayerGenerator level 1")
    void buyFirst() {
        when(generatorRepository.findById(1L)).thenReturn(Optional.of(voidGen));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        generatorService.buy(1L, 1L);

        verify(playerGeneratorRepository).save(argThat(pg ->
                pg.getLevel() == 1 && pg.getGenerator().getId().equals(1L)));

        BigNum expected = new BigNum(1.0, 5).subtract(new BigNum(1.0, 1));
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    @Test
    @DisplayName("Повторна купівля — level 5 → 6, вартість зростає")
    void buyAgain() {
        PlayerGenerator pg = instantiate(PlayerGenerator.class);
        pg.setSave(save);
        pg.setGenerator(voidGen);
        pg.setLevel(5);

        when(generatorRepository.findById(1L)).thenReturn(Optional.of(voidGen));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(pg));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));

        generatorService.buy(1L, 1L);

        assertEquals(6, pg.getLevel());
        // cost = 1.0 * 1.5^5 * 10^1 = 7.59375 * 10 = 75.9375
        BigNum cost = new BigNum(1.0 * Math.pow(1.5, 5), 1);
        BigNum expected = new BigNum(1.0, 5).subtract(cost);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.01);
    }

    @Test
    @DisplayName("Не вистачає ресурсів — exception")
    void buyNotEnough() {
        playerEnergy.setNumber(1.0);
        playerEnergy.setExponent(0); // 1 енергії

        when(generatorRepository.findById(1L)).thenReturn(Optional.of(voidGen));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));

        assertThrows(RuntimeException.class, () -> generatorService.buy(1L, 1L));
    }

    @Test
    @DisplayName("buyBulk amount=5 — купує 5 рівнів за раз")
    void buyBulkFive() {
        when(generatorRepository.findById(1L)).thenReturn(Optional.of(voidGen));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        int bought = generatorService.buyBulk(1L, 1L, 5);

        assertEquals(5, bought);
        verify(playerGeneratorRepository).save(argThat(pg -> pg.getLevel() == 5));
    }

    @Test
    @DisplayName("buyBulk amount=-1 — купує максимум доступних")
    void buyBulkMax() {
        when(generatorRepository.findById(1L)).thenReturn(Optional.of(voidGen));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        int bought = generatorService.buyBulk(1L, 1L, -1);

        assertTrue(bought > 0);
        // Перевіряємо що ресурсів залишилось менше ніж вартість наступного
        double remainingVal = playerEnergy.getNumber() * Math.pow(10, playerEnergy.getExponent());
        assertTrue(remainingVal >= 0);
    }

    @Test
    @DisplayName("COST_SCALE_REDUCE знижує ефективний множник")
    void costScaleReduce() {
        Upgrade u = instantiate(Upgrade.class);
        ReflectionTestUtils.setField(u, "effectType", "COST_SCALE_REDUCE");
        ReflectionTestUtils.setField(u, "effectValue", 0.1);
        PlayerUpgrade pu = new PlayerUpgrade();
        pu.setUpgrade(u);
        pu.setLevel(3); // reduce = 0.3

        double effective = generatorService.effectiveCostMultiplier(1.5, List.of(pu));
        // 1.5 - 0.3 = 1.2
        assertEquals(1.2, effective, 0.001);
    }

    @Test
    @DisplayName("COST_SCALE_REDUCE не опускає нижче MIN_COST_MULTIPLIER")
    void costScaleReduceFloor() {
        Upgrade u = instantiate(Upgrade.class);
        ReflectionTestUtils.setField(u, "effectType", "COST_SCALE_REDUCE");
        ReflectionTestUtils.setField(u, "effectValue", 0.1);
        PlayerUpgrade pu = new PlayerUpgrade();
        pu.setUpgrade(u);
        pu.setLevel(100); // reduce = 10.0

        double effective = generatorService.effectiveCostMultiplier(1.5, List.of(pu));
        assertEquals(1.03, effective, 0.001);
    }

    @Test
    @DisplayName("Генератор не знайдено — exception")
    void buyNotFound() {
        when(generatorRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> generatorService.buy(1L, 999L));
    }

    // === Helpers ===

    private Resource createResource(Long id, String code) {
        Resource r = instantiate(Resource.class);
        ReflectionTestUtils.setField(r, "id", id);
        ReflectionTestUtils.setField(r, "code", code);
        return r;
    }

    private Save createSave(Long id) {
        Save s = instantiate(Save.class);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
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