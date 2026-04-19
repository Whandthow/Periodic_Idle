package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.Resource;
import com.periodic.idle.content.Upgrade;
import com.periodic.idle.content.UpgradeRepository;
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
class UpgradeServiceTest {

    @Mock
    private UpgradeRepository upgradeRepository;
    @Mock
    private PlayerUpgradeRepository playerUpgradeRepository;
    @Mock
    private PlayerResourceRepository playerResourceRepository;

    @InjectMocks
    private UpgradeService upgradeService;

    private Resource energy;
    private Upgrade upgrade;
    private Save save;
    private PlayerResource playerResource;

    @BeforeEach
    void setUp() {
        energy = createResource(1L, "E", "Енергія", 0);
        save = createSave(1L, "dev");

        // Апгрейд: коштує 1.0e2 енергії, множник 2.5, макс рівень 10
        upgrade = createUpgrade(1L, "energy_boost_1", "ENERGY_MULT", 0.5,
                energy, 1.0, 2L, 2.5, 10);

        // Гравець має 1.0e5 = 100000 енергії
        playerResource = createPlayerResource(1L, save, energy, 1.0, 5);
    }

    // === Успішні сценарії ===

    @Test
    @DisplayName("Перша купівля — створює PlayerUpgrade з level=1, списує ресурси")
    void buyFirstTime_success() {
        when(upgradeRepository.findById(1L)).thenReturn(Optional.of(upgrade));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerResource));

        upgradeService.buy(1L, 1L);

        verify(playerUpgradeRepository).save(argThat(pu ->
                pu.getLevel() == 1 &&
                pu.getUpgrade().getId().equals(1L) &&
                pu.getSave().getId().equals(1L)
        ));

        // Вартість = 1.0 * 2.5^0 = 1.0 при exponent 2 → 100
        // Було 1.0e5, стало 1.0e5 - 1.0e2
        BigNum expected = new BigNum(1.0, 5).subtract(new BigNum(1.0, 2));
        assertEquals(expected.getNumber(), playerResource.getNumber(), 0.001);
        assertEquals(expected.getExponent(), playerResource.getExponent());
    }

    @Test
    @DisplayName("Повторна купівля — рівень збільшується з 3 до 4")
    void buyAgain_levelIncreases() {
        PlayerUpgrade existingPu = new PlayerUpgrade();
        existingPu.setSave(save);
        existingPu.setUpgrade(upgrade);
        existingPu.setLevel(3);

        when(upgradeRepository.findById(1L)).thenReturn(Optional.of(upgrade));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>(List.of(existingPu)));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerResource));

        upgradeService.buy(1L, 1L);

        assertEquals(4, existingPu.getLevel());
        verify(playerUpgradeRepository).save(existingPu);
    }

    @Test
    @DisplayName("Вартість зростає з рівнем через costMultiplier")
    void buy_costScalesWithLevel() {
        PlayerUpgrade existingPu = new PlayerUpgrade();
        existingPu.setSave(save);
        existingPu.setUpgrade(upgrade);
        existingPu.setLevel(2);

        when(upgradeRepository.findById(1L)).thenReturn(Optional.of(upgrade));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>(List.of(existingPu)));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerResource));

        double beforeNumber = playerResource.getNumber();
        long beforeExp = playerResource.getExponent();

        upgradeService.buy(1L, 1L);

        // Вартість level 2: 1.0 * 2.5^2 = 6.25 при exponent 2 → 625
        BigNum before = new BigNum(beforeNumber, beforeExp);
        BigNum cost = new BigNum(6.25, 2);
        BigNum expected = before.subtract(cost);

        assertEquals(expected.getNumber(), playerResource.getNumber(), 0.01);
        assertEquals(expected.getExponent(), playerResource.getExponent());
        assertEquals(3, existingPu.getLevel());
    }

    // === Помилкові сценарії ===

    @Test
    @DisplayName("Апгрейд не знайдено — RuntimeException")
    void buy_upgradeNotFound_throws() {
        when(upgradeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> upgradeService.buy(1L, 999L));
        verify(playerUpgradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Не вистачає ресурсів — RuntimeException, нічого не змінюється")
    void buy_notEnoughResources_throws() {
        // Гравець має 1.0e0 = 1 енергії, вартість 1.0e2 = 100
        playerResource.setNumber(1.0);
        playerResource.setExponent(0);

        when(upgradeRepository.findById(1L)).thenReturn(Optional.of(upgrade));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerResource));

        assertThrows(RuntimeException.class, () -> upgradeService.buy(1L, 1L));
        verify(playerUpgradeRepository, never()).save(any());

        // Ресурси не змінились
        assertEquals(1.0, playerResource.getNumber(), 0.001);
        assertEquals(0, playerResource.getExponent());
    }

    @Test
    @DisplayName("Вже максимальний рівень — RuntimeException")
    void buy_alreadyMaxLevel_throws() {
        PlayerUpgrade maxedPu = new PlayerUpgrade();
        maxedPu.setSave(save);
        maxedPu.setUpgrade(upgrade);
        maxedPu.setLevel(10); // maxLevel = 10

        when(upgradeRepository.findById(1L)).thenReturn(Optional.of(upgrade));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(maxedPu));

        assertThrows(RuntimeException.class, () -> upgradeService.buy(1L, 1L));
        verify(playerUpgradeRepository, never()).save(any());
        assertEquals(10, maxedPu.getLevel());
    }

    @Test
    @DisplayName("Ресурс гравця не знайдено — RuntimeException")
    void buy_playerResourceNotFound_throws() {
        // Апгрейд коштує енергію, але у гравця немає запису про енергію
        when(upgradeRepository.findById(1L)).thenReturn(Optional.of(upgrade));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        assertThrows(RuntimeException.class, () -> upgradeService.buy(1L, 1L));
        verify(playerUpgradeRepository, never()).save(any());
    }

    // === Helper методи для створення тестових entity ===

    private Resource createResource(Long id, String code, String name, int tier) {
        Resource r = instantiate(Resource.class);
        ReflectionTestUtils.setField(r, "id", id);
        ReflectionTestUtils.setField(r, "code", code);
        ReflectionTestUtils.setField(r, "name", name);
        ReflectionTestUtils.setField(r, "tier", tier);
        return r;
    }

    private Save createSave(Long id, String playerName) {
        Save s = instantiate(Save.class);
        ReflectionTestUtils.setField(s, "id", id);
        s.setPlayerName(playerName);
        return s;
    }

    private Upgrade createUpgrade(Long id, String code, String effectType, double effectValue,
                                  Resource costResource, double costNumber, long costExponent,
                                  double costMultiplier, int maxLevel) {
        Upgrade u = instantiate(Upgrade.class);
        ReflectionTestUtils.setField(u, "id", id);
        ReflectionTestUtils.setField(u, "code", code);
        ReflectionTestUtils.setField(u, "effectType", effectType);
        ReflectionTestUtils.setField(u, "effectValue", effectValue);
        ReflectionTestUtils.setField(u, "costResource", costResource);
        ReflectionTestUtils.setField(u, "costNumber", costNumber);
        ReflectionTestUtils.setField(u, "costExponent", costExponent);
        ReflectionTestUtils.setField(u, "costMultiplier", costMultiplier);
        ReflectionTestUtils.setField(u, "maxLevel", maxLevel);
        return u;
    }

    private PlayerResource createPlayerResource(Long id, Save save, Resource resource,
                                                double number, long exponent) {
        PlayerResource pr = instantiate(PlayerResource.class);
        ReflectionTestUtils.setField(pr, "id", id);
        pr.setSave(save);
        pr.setResource(resource);
        pr.setNumber(number);
        pr.setExponent(exponent);
        return pr;
    }

    @SuppressWarnings("unchecked")
    private <T> T instantiate(Class<T> clazz) {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + clazz.getName(), e);
        }
    }
}
