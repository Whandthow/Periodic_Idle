package com.periodic.idle.engine;

import com.periodic.idle.content.Resource;
import com.periodic.idle.content.Upgrade;
import com.periodic.idle.engine.config.GameEngineProperties;
import com.periodic.idle.engine.config.MatterProperties;
import com.periodic.idle.engine.config.PrestigeProperties;
import com.periodic.idle.engine.config.CollapseCycleBonusProperties;
import com.periodic.idle.player.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatterServiceTest {

    private final MatterProperties props = new MatterProperties(10L);
    private final GameEngineProperties gameEngineProperties =
            new GameEngineProperties(100L, 50L, 308L, 2.0, 86400.0);
    private final PrestigeProperties prestigeProperties =
            new PrestigeProperties(18.0, 5.0, 2.5, 1.0, 1L);
    private final CollapseCycleBonus collapseCycleBonus =
            new CollapseCycleBonus(new CollapseCycleBonusProperties(2.5, 10.0, 10.0, 10_000L));

    @Mock private SaveRepository saveRepository;
    @Mock private PlayerResourceRepository playerResourceRepository;
    @Mock private PlayerGeneratorRepository playerGeneratorRepository;
    @Mock private PlayerUpgradeRepository playerUpgradeRepository;

    private MatterService matterService;

    private Save save;
    private PlayerResource energy;
    private PlayerResource p;

    @BeforeEach
    void setUp() {
        matterService = new MatterService(props, gameEngineProperties, prestigeProperties, collapseCycleBonus,
                saveRepository, playerResourceRepository, playerGeneratorRepository, playerUpgradeRepository);

        save = instantiate(Save.class);
        ReflectionTestUtils.setField(save, "id", 1L);
        energy = makePlayerResource("E", 1.0, gameEngineProperties.energyCapExponent());
        p = makePlayerResource("p", 0, 0);
    }

    @Test
    @DisplayName("collapse: енергія на капі (1e308) -> повний ресет Тіру 0 (енергія, генератори, " +
            "апгрейди, VC), +1 частинка, matterCollapses++")
    void collapse_atCap_success() {
        PlayerResource crystals = makePlayerResource("VC", 5.76, 95); // 5.76e95, як у гравця з відгуку
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy, p, crystals));
        PlayerGenerator gen = instantiate(PlayerGenerator.class);
        gen.setLevel(5);
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(gen));
        PlayerUpgrade upgrade = makePlayerUpgrade("ENERGY_MULT", 20);
        PlayerUpgrade autobuyUpgrade = makePlayerUpgrade("AUTOBUY", 3);
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(upgrade, autobuyUpgrade));

        matterService.collapse(1L, "p");

        assertEquals(prestigeProperties.starterEnergyNumber(), energy.getNumber(), 1e-9);
        assertEquals(prestigeProperties.starterEnergyExponent(), energy.getExponent());
        assertEquals(0, gen.getLevel());
        assertEquals(0, upgrade.getLevel());
        assertEquals(3, autobuyUpgrade.getLevel(), "AUTOBUY upgrade must survive collapse so generator auto-buy keeps working");
        assertEquals(0, crystals.getNumber(), 1e-9);
        assertEquals(0, crystals.getExponent());
        assertEquals(1, p.getNumber() * Math.pow(10, p.getExponent()), 1e-9);
        assertEquals(1L, save.getMatterCollapses());
    }

    @Test
    @DisplayName("collapse: після VC_PERSISTS_AFTER_COLLAPSES колапсів VC більше НЕ скидається")
    void collapse_afterVcPersistThreshold_keepsCrystals() {
        ReflectionTestUtils.setField(save, "matterCollapses", collapseCycleBonus.vcPersistsAfterCollapses());
        PlayerResource crystals = makePlayerResource("VC", 5.76, 95);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy, p, crystals));
        PlayerGenerator gen = instantiate(PlayerGenerator.class);
        gen.setLevel(5);
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(gen));
        PlayerUpgrade upgrade = makePlayerUpgrade("ENERGY_MULT", 20);
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(upgrade));

        matterService.collapse(1L, "p");

        // Енергія/генератори/апгрейди й далі скидаються — тільки VC переживає колапс.
        assertEquals(0, upgrade.getLevel());
        assertEquals(0, gen.getLevel());
        assertEquals(5.76, crystals.getNumber(), 1e-9);
        assertEquals(95, crystals.getExponent());
    }

    @Test
    @DisplayName("collapse: енергія нижче капу -> кидає 'Потрібно 1e308 енергії'")
    void collapse_belowCap_throws() {
        energy.setExponent(100);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy, p));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> matterService.collapse(1L, "p"));
        assertEquals("Потрібно 1e308 енергії", ex.getMessage());
    }

    @Test
    @DisplayName("collapse: невідома частинка -> кидає помилку")
    void collapse_unknownParticle_throws() {
        assertThrows(RuntimeException.class, () -> matterService.collapse(1L, "x"));
        verifyNoInteractions(saveRepository);
    }

    @Test
    @DisplayName("breakInfinity: достатньо колапсів -> brokenInfinity=true")
    void breakInfinity_enoughCollapses_success() {
        save.setMatterCollapses(props.breakInfinityRequired());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        matterService.breakInfinity(1L);

        assertTrue(save.isBrokenInfinity());
    }

    @Test
    @DisplayName("breakInfinity: недостатньо колапсів -> кидає помилку")
    void breakInfinity_notEnoughCollapses_throws() {
        save.setMatterCollapses(0L);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        assertThrows(RuntimeException.class, () -> matterService.breakInfinity(1L));
        assertFalse(save.isBrokenInfinity());
    }

    @Test
    @DisplayName("breakInfinity: вже зламано -> no-op, не кидає")
    void breakInfinity_alreadyBroken_noop() {
        save.setBrokenInfinity(true);
        save.setMatterCollapses(0L);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        assertDoesNotThrow(() -> matterService.breakInfinity(1L));
        verify(saveRepository, never()).save(any());
    }

    private PlayerUpgrade makePlayerUpgrade(String effectType, int level) {
        Upgrade upgrade = instantiate(Upgrade.class);
        ReflectionTestUtils.setField(upgrade, "effectType", effectType);
        PlayerUpgrade pu = instantiate(PlayerUpgrade.class);
        pu.setUpgrade(upgrade);
        pu.setLevel(level);
        return pu;
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
