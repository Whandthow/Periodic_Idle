package com.periodic.idle.engine;

import com.periodic.idle.content.Element;
import com.periodic.idle.content.Molecule;
import com.periodic.idle.content.MoleculeComponent;
import com.periodic.idle.content.MoleculeRepository;
import com.periodic.idle.content.Resource;
import com.periodic.idle.engine.config.GameEngineProperties;
import com.periodic.idle.engine.config.MoleculeProperties;
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
class MoleculeServiceTest {

    private final MoleculeProperties props = new MoleculeProperties(100_000L, 1_000_000.0, 298L);
    private final GameEngineProperties gameEngineProperties =
            new GameEngineProperties(100L, 50L, 308L, 2.0, 86400.0);

    @Mock private MoleculeRepository moleculeRepository;
    @Mock private PlayerMoleculeRepository playerMoleculeRepository;
    @Mock private PlayerElementRepository playerElementRepository;
    @Mock private PlayerResourceRepository playerResourceRepository;
    @Mock private SaveRepository saveRepository;

    private MoleculeService moleculeService;

    private Save save;
    private Element hydrogen;
    private Element oxygen;
    private Molecule water; // H2O = 2H + 1O
    private PlayerResource energy;

    @BeforeEach
    void setUp() {
        moleculeService = new MoleculeService(props, gameEngineProperties, moleculeRepository,
                playerMoleculeRepository, playerElementRepository, playerResourceRepository, saveRepository);

        save = instantiate(Save.class);
        ReflectionTestUtils.setField(save, "id", 1L);

        hydrogen = createElement(1L, 1, "H");
        oxygen = createElement(8L, 8, "O");

        water = createMolecule(1L, "H2O", "Вода", 9.51,
                component(hydrogen, 2), component(oxygen, 1));

        energy = makePlayerResource("E", 1.0, 250);
    }

    @Test
    @DisplayName("synthesizeBulk: H2O x1 — списує 2H+1O, +1 у player_molecules, енергія зростає")
    void synthesize_water_success() {
        PlayerElement ph = playerElement(hydrogen, 10);
        PlayerElement po = playerElement(oxygen, 10);

        when(moleculeRepository.findById(1L)).thenReturn(Optional.of(water));
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(ph, po));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy));
        when(playerMoleculeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        long got = moleculeService.synthesizeBulk(1L, 1L, 1);

        assertEquals(1, got);
        assertEquals(8, ph.getCount());
        assertEquals(9, po.getCount());
        double energyTotal = energy.getNumber() * Math.pow(10, energy.getExponent());
        assertTrue(energyTotal > 1.0e250, "енергія мала зрости після синтезу молекули, отримали " + energyTotal);
        verify(playerMoleculeRepository).save(argThat(pm -> pm.getCount() == 1));
    }

    @Test
    @DisplayName("synthesizeBulk(amount=-1): обмежується найдефіцитнішим атомом рецепту")
    void synthesize_max_limitedByScarcestComponent() {
        PlayerElement ph = playerElement(hydrogen, 10); // 10/2 = 5 можливих H2O
        PlayerElement po = playerElement(oxygen, 2);    // 2/1 = 2 можливих H2O -> лімітує

        when(moleculeRepository.findById(1L)).thenReturn(Optional.of(water));
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(ph, po));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy));
        when(playerMoleculeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        long got = moleculeService.synthesizeBulk(1L, 1L, -1);

        assertEquals(2, got);
    }

    @Test
    @DisplayName("synthesizeBulk: відсутній компонент (O не синтезовано) -> кидає помилку з назвою елемента")
    void synthesize_missingComponent_throws() {
        PlayerElement ph = playerElement(hydrogen, 10);

        when(moleculeRepository.findById(1L)).thenReturn(Optional.of(water));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(ph));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> moleculeService.synthesizeBulk(1L, 1L, 1));
        assertTrue(ex.getMessage().contains("O"), "повідомлення мало називати відсутній елемент: " + ex.getMessage());
        verifyNoInteractions(playerResourceRepository);
    }

    @Test
    @DisplayName("synthesizeBulk(amount=-1): відсутній компонент -> повертає 0, без винятку")
    void synthesize_max_missingComponent_returnsZero() {
        PlayerElement ph = playerElement(hydrogen, 10);

        when(moleculeRepository.findById(1L)).thenReturn(Optional.of(water));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(ph));

        long got = moleculeService.synthesizeBulk(1L, 1L, -1);

        assertEquals(0, got);
    }

    @Test
    @DisplayName("synthesizeBulk(amount=0): no-op")
    void synthesize_zeroAmount_noop() {
        long got = moleculeService.synthesizeBulk(1L, 1L, 0);
        assertEquals(0, got);
        verifyNoInteractions(moleculeRepository);
    }

    @Test
    @DisplayName("canAffordAtLeastOne: true, коли всіх компонентів вистачає на 1 молекулу")
    void canAffordAtLeastOne_true() {
        PlayerElement ph = playerElement(hydrogen, 2);
        PlayerElement po = playerElement(oxygen, 1);
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(ph, po));

        assertTrue(moleculeService.canAffordAtLeastOne(1L, water));
    }

    @Test
    @DisplayName("canAffordAtLeastOne: false, коли бракує хоча б одного компонента")
    void canAffordAtLeastOne_false() {
        PlayerElement ph = playerElement(hydrogen, 1); // потрібно 2
        PlayerElement po = playerElement(oxygen, 1);
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(ph, po));

        assertFalse(moleculeService.canAffordAtLeastOne(1L, water));
    }

    private MoleculeComponent component(Element element, int atomCount) {
        MoleculeComponent c = instantiate(MoleculeComponent.class);
        ReflectionTestUtils.setField(c, "element", element);
        ReflectionTestUtils.setField(c, "atomCount", atomCount);
        return c;
    }

    private Molecule createMolecule(Long id, String formula, String name, double bondEnergyEv,
                                     MoleculeComponent... components) {
        Molecule m = instantiate(Molecule.class);
        ReflectionTestUtils.setField(m, "id", id);
        ReflectionTestUtils.setField(m, "formula", formula);
        ReflectionTestUtils.setField(m, "name", name);
        ReflectionTestUtils.setField(m, "bondEnergyEv", bondEnergyEv);
        ReflectionTestUtils.setField(m, "components", List.of(components));
        return m;
    }

    private Element createElement(Long id, int atomicNumber, String symbol) {
        Element el = instantiate(Element.class);
        ReflectionTestUtils.setField(el, "id", id);
        ReflectionTestUtils.setField(el, "atomicNumber", atomicNumber);
        ReflectionTestUtils.setField(el, "symbol", symbol);
        return el;
    }

    private PlayerElement playerElement(Element element, long count) {
        PlayerElement pe = instantiate(PlayerElement.class);
        pe.setElement(element);
        pe.setCount(count);
        return pe;
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
