package com.periodic.idle.engine;

import com.periodic.idle.content.Element;
import com.periodic.idle.content.Resource;
import com.periodic.idle.content.Star;
import com.periodic.idle.content.StarRepository;
import com.periodic.idle.engine.config.ElementBonusProperties;
import com.periodic.idle.engine.config.GameEngineProperties;
import com.periodic.idle.engine.config.StarProperties;
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
class StarServiceTest {

    private final StarProperties props = new StarProperties(1000L, 298L, 1, 2);
    private final GameEngineProperties gameEngineProperties =
            new GameEngineProperties(100L, 50L, 308L, 2.0, 86400.0);
    private final ElementBonus elementBonus =
            new ElementBonus(new ElementBonusProperties(0.06, 15.0, 0.15, 6.0, 6.0, 2.0));

    @Mock private StarRepository starRepository;
    @Mock private PlayerStarRepository playerStarRepository;
    @Mock private PlayerElementRepository playerElementRepository;
    @Mock private PlayerMoleculeRepository playerMoleculeRepository;
    @Mock private PlayerResourceRepository playerResourceRepository;
    @Mock private SaveRepository saveRepository;

    private StarService starService;

    private Save save;
    private Star star; // baseCost=100, mult=2.0, 4H->1He/event, 1.0 event/sec/level
    private Element hydrogenEl;
    private Element heliumEl;

    @BeforeEach
    void setUp() {
        starService = new StarService(props, gameEngineProperties, elementBonus, starRepository,
                playerStarRepository, playerElementRepository, playerMoleculeRepository,
                playerResourceRepository, saveRepository);

        save = instantiate(Save.class);
        ReflectionTestUtils.setField(save, "id", 1L);

        star = instantiate(Star.class);
        ReflectionTestUtils.setField(star, "id", 1L);
        ReflectionTestUtils.setField(star, "code", "main_sequence");
        ReflectionTestUtils.setField(star, "baseCostHydrogen", 100L);
        ReflectionTestUtils.setField(star, "costMultiplier", 2.0);
        ReflectionTestUtils.setField(star, "fuelHPerEvent", 4L);
        ReflectionTestUtils.setField(star, "outputHePerEvent", 1L);
        ReflectionTestUtils.setField(star, "eventsPerSecPerLevel", 1.0);

        hydrogenEl = createElement(1L, 1);
        heliumEl = createElement(2L, 2);
    }

    // === buyLevel ===

    @Test
    @DisplayName("buyLevel: достатньо Гідрогену на 2 рівні (100 + 200 = 300)")
    void buyLevel_success_twoLevels() {
        PlayerElement hydrogen = playerElement(hydrogenEl, 300);

        when(starRepository.findById(1L)).thenReturn(Optional.of(star));
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(hydrogen));
        when(playerStarRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        int bought = starService.buyLevel(1L, 1L, 2);

        assertEquals(2, bought);
        assertEquals(0, hydrogen.getCount());
        verify(playerStarRepository).save(argThat(ps -> ps.getLevel() == 2));
    }

    @Test
    @DisplayName("buyLevel(amount=-1): максимум за наявний Гідроген")
    void buyLevel_max_limitedByHydrogen() {
        // level1=100, level2=200, level3=400 -> з 350 вистачить лише на 2 рівні (300 витрачено)
        PlayerElement hydrogen = playerElement(hydrogenEl, 350);

        when(starRepository.findById(1L)).thenReturn(Optional.of(star));
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(hydrogen));
        when(playerStarRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        int bought = starService.buyLevel(1L, 1L, -1);

        assertEquals(2, bought);
        assertEquals(50, hydrogen.getCount());
    }

    @Test
    @DisplayName("buyLevel: недостатньо Гідрогену навіть на 1 рівень -> кидає помилку")
    void buyLevel_insufficientHydrogen_throws() {
        PlayerElement hydrogen = playerElement(hydrogenEl, 50);

        when(starRepository.findById(1L)).thenReturn(Optional.of(star));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(hydrogen));
        when(playerStarRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        assertThrows(RuntimeException.class, () -> starService.buyLevel(1L, 1L, 1));
    }

    @Test
    @DisplayName("buyLevel(amount=-1): недостатньо Гідрогену -> повертає 0, без винятку")
    void buyLevel_max_insufficientHydrogen_returnsZero() {
        PlayerElement hydrogen = playerElement(hydrogenEl, 50);

        when(starRepository.findById(1L)).thenReturn(Optional.of(star));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(hydrogen));
        when(playerStarRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        assertEquals(0, starService.buyLevel(1L, 1L, -1));
    }

    // === processStarTick ===

    @Test
    @DisplayName("processStarTick: достатньо H -> споживає паливо, виробляє He, додає енергію")
    void processStarTick_sufficientFuel_producesHeliumAndEnergy() {
        PlayerStar ps = playerStar(save, star, 1); // level=1 -> 1 подія/сек -> 4H за тік
        PlayerElement hydrogen = playerElement(hydrogenEl, 1000);
        PlayerElement helium = playerElement(heliumEl, 0);
        PlayerResource energy = makePlayerResource("E", 1.0, 250);

        when(playerStarRepository.findById(10L)).thenReturn(Optional.of(ps));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(hydrogen, helium));
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy));

        starService.processStarTick(1L, 10L);

        assertEquals(996, hydrogen.getCount()); // 1000 - 4
        assertEquals(1, helium.getCount());     // +1
        double energyTotal = energy.getNumber() * Math.pow(10, energy.getExponent());
        assertTrue(energyTotal > 1.0e250, "енергія мала зрости від фузії (екзотермічно для He), отримали " + energyTotal);
    }

    @Test
    @DisplayName("processStarTick: недостатньо H -> гіпернова (обнуляє атоми/молекули/зорі, лічильник++)")
    void processStarTick_insufficientFuel_triggersHypernova() {
        PlayerStar ps = playerStar(save, star, 1); // потрібно 4H, є лише 1
        PlayerElement hydrogen = playerElement(hydrogenEl, 1);
        PlayerElement helium = playerElement(heliumEl, 500);
        Element carbon = createElement(6L, 6);
        PlayerElement carbonOwned = playerElement(carbon, 42);
        PlayerMolecule water = instantiate(PlayerMolecule.class);
        water.setCount(7);
        PlayerStar otherStar = playerStar(save, star, 3);

        when(playerStarRepository.findById(10L)).thenReturn(Optional.of(ps));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(hydrogen, helium, carbonOwned));
        when(playerMoleculeRepository.findBySaveId(1L)).thenReturn(List.of(water));
        when(playerStarRepository.findBySaveId(1L)).thenReturn(List.of(ps, otherStar));
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        starService.processStarTick(1L, 10L);

        assertEquals(0, hydrogen.getCount());
        assertEquals(0, helium.getCount());
        assertEquals(0, carbonOwned.getCount());
        assertEquals(0, water.getCount());
        assertEquals(0, ps.getLevel());
        assertEquals(0, otherStar.getLevel());
        assertEquals(1L, save.getHypernovaCount());
        verifyNoInteractions(playerResourceRepository);
    }

    @Test
    @DisplayName("processStarTick: рівень 0 -> no-op")
    void processStarTick_zeroLevel_noop() {
        PlayerStar ps = playerStar(save, star, 0);
        when(playerStarRepository.findById(10L)).thenReturn(Optional.of(ps));

        assertDoesNotThrow(() -> starService.processStarTick(1L, 10L));

        verifyNoInteractions(playerElementRepository, playerResourceRepository);
    }

    private Element createElement(Long id, int atomicNumber) {
        Element el = instantiate(Element.class);
        ReflectionTestUtils.setField(el, "id", id);
        ReflectionTestUtils.setField(el, "atomicNumber", atomicNumber);
        return el;
    }

    private PlayerElement playerElement(Element element, long count) {
        PlayerElement pe = instantiate(PlayerElement.class);
        pe.setElement(element);
        pe.setCount(count);
        return pe;
    }

    private PlayerStar playerStar(Save save, Star star, int level) {
        PlayerStar ps = instantiate(PlayerStar.class);
        ps.setSave(save);
        ps.setStar(star);
        ps.setLevel(level);
        return ps;
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
