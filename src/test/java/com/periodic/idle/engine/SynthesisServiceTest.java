package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.Element;
import com.periodic.idle.content.ElementRepository;
import com.periodic.idle.content.Resource;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SynthesisServiceTest {

    @Mock private ElementRepository elementRepository;
    @Mock private PlayerElementRepository playerElementRepository;
    @Mock private PlayerResourceRepository playerResourceRepository;
    @Mock private SaveRepository saveRepository;

    @InjectMocks
    private SynthesisService synthesisService;

    private Save save;
    private Element hydrogen;
    private Element helium;
    private Element zincLike; // синтетичний важкий елемент (Z=30) для тестів ендотермічного синтезу
    private PlayerResource p;
    private PlayerResource n;
    private PlayerResource e;
    private PlayerResource energy;

    @BeforeEach
    void setUp() {
        save = instantiate(Save.class);
        ReflectionTestUtils.setField(save, "id", 1L);

        hydrogen = createElement(1L, 1, "H", 1, 0, 1);
        helium = createElement(2L, 2, "He", 2, 2, 2);
        zincLike = createElement(30L, 30, "Zn*", 30, 35, 30); // Z=30 > 26 (залізо) -> ендотермічний

        p = makePlayerResource("p", 10.0, 0);
        n = makePlayerResource("n", 10.0, 0);
        e = makePlayerResource("e", 10.0, 0);
        // Далеко від капу 1e308, щоб екзотермічний приріст був легко перевірний без clamp.
        energy = makePlayerResource("E", 1.0, 250);
    }

    private List<PlayerResource> baseResources() {
        return List.of(p, n, e, energy);
    }

    @Test
    @DisplayName("synthesizeBulk: H (1p+0n+1e) x1 — списує рівно 1p, 1e, +1 у player_elements, енергія не змінюється (BE=0)")
    void synthesize_hydrogen_success() {
        when(elementRepository.findById(1L)).thenReturn(Optional.of(hydrogen));
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(baseResources());
        when(playerElementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        long got = synthesisService.synthesizeBulk(1L, 1L, 1);

        assertEquals(1, got);
        assertEquals(9, p.getNumber() * Math.pow(10, p.getExponent()), 1e-9);
        assertEquals(10, n.getNumber() * Math.pow(10, n.getExponent()), 1e-9);
        assertEquals(9, e.getNumber() * Math.pow(10, e.getExponent()), 1e-9);
        // H-1 — одинокий протон, енергія зв'язку 0 -> E не змінюється.
        assertEquals(1.0, energy.getNumber(), 1e-9);
        assertEquals(250, energy.getExponent());
        verify(playerElementRepository).save(argThat(pe -> pe.getCount() == 1));
    }

    @Test
    @DisplayName("synthesizeBulk: He потребує попереднього синтезу H — без нього кидає помилку")
    void synthesize_secondElement_withoutPrevious_throws() {
        when(elementRepository.findById(2L)).thenReturn(Optional.of(helium));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        assertThrows(RuntimeException.class, () -> synthesisService.synthesizeBulk(1L, 2L, 1));
        verifyNoInteractions(playerResourceRepository);
    }

    @Test
    @DisplayName("synthesizeBulk: He після синтезу H — успішно, списує 2p+2n+2e, енергія зростає (екзотермічно)")
    void synthesize_secondElement_withPrevious_success() {
        when(elementRepository.findById(2L)).thenReturn(Optional.of(helium));
        PlayerElement existingH = new PlayerElement();
        existingH.setElement(hydrogen);
        existingH.setCount(1);
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(existingH));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(baseResources());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        long got = synthesisService.synthesizeBulk(1L, 2L, 1);

        assertEquals(1, got);
        assertEquals(8, p.getNumber() * Math.pow(10, p.getExponent()), 1e-9);
        assertEquals(8, n.getNumber() * Math.pow(10, n.getExponent()), 1e-9);
        assertEquals(8, e.getNumber() * Math.pow(10, e.getExponent()), 1e-9);
        // He-4 має додатну енергію зв'язку -> E має зрости понад стартові 1.0e250.
        double energyTotal = energy.getNumber() * Math.pow(10, energy.getExponent());
        assertTrue(energyTotal > 1.0e250, "енергія мала зрости після екзотермічного синтезу He, отримали " + energyTotal);
    }

    @Test
    @DisplayName("synthesizeBulk(amount=-1): синтезує максимум за наявні частинки")
    void synthesize_max_limitsToAffordable() {
        when(elementRepository.findById(1L)).thenReturn(Optional.of(hydrogen));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(baseResources());
        when(playerElementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        // H costs 1p+0n+1e, маємо 10p/10e -> максимум 10.
        long got = synthesisService.synthesizeBulk(1L, 1L, -1);

        assertEquals(10, got);
    }

    @Test
    @DisplayName("synthesizeBulk: недостатньо частинок, amount фіксований -> кидає помилку")
    void synthesize_notEnough_throws() {
        p.setNumber(0);
        p.setExponent(0);
        when(elementRepository.findById(1L)).thenReturn(Optional.of(hydrogen));
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(baseResources());

        assertThrows(RuntimeException.class, () -> synthesisService.synthesizeBulk(1L, 1L, 1));
    }

    @Test
    @DisplayName("synthesizeBulk(amount=0): no-op")
    void synthesize_zeroAmount_noop() {
        long got = synthesisService.synthesizeBulk(1L, 1L, 0);
        assertEquals(0, got);
        verifyNoInteractions(elementRepository);
    }

    // === Наукова концепція: енергія зв'язку ядра (SEMF) ===

    @Test
    @DisplayName("synthesizeBulk: важкий елемент (Z=30 > заліза) — ендотермічний, списує E")
    void synthesize_heavyElement_endothermic_consumesEnergy() {
        // Достатньо частинок і достатньо енергії (близько до капу) для одного атома.
        p.setNumber(1.0); p.setExponent(2); // 100
        n.setNumber(1.0); n.setExponent(2);
        e.setNumber(1.0); e.setExponent(2);
        energy.setNumber(1.0); energy.setExponent(308); // на капі — багато енергії про запас

        PlayerElement existingPrev = new PlayerElement();
        Element prev = createElement(29L, 29, "Cu*", 29, 34, 29);
        existingPrev.setElement(prev);
        existingPrev.setCount(1);

        when(elementRepository.findById(30L)).thenReturn(Optional.of(zincLike));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(existingPrev));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(baseResources());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        long got = synthesisService.synthesizeBulk(1L, 30L, 1);

        assertEquals(1, got);
        double energyAfter = energy.getNumber() * Math.pow(10, energy.getExponent());
        double energyBefore = 1.0 * Math.pow(10, 308);
        assertTrue(energyAfter < energyBefore,
                "енергія мала зменшитись після ендотермічного синтезу, було " + energyBefore + ", стало " + energyAfter);
    }

    @Test
    @DisplayName("synthesizeBulk: важкий елемент без достатньої енергії -> кидає помилку, не списує частинки")
    void synthesize_heavyElement_notEnoughEnergy_throws() {
        p.setNumber(1.0); p.setExponent(2);
        n.setNumber(1.0); n.setExponent(2);
        e.setNumber(1.0); e.setExponent(2);
        energy.setNumber(0); energy.setExponent(0); // фактично 0 енергії

        PlayerElement existingPrev = new PlayerElement();
        Element prev = createElement(29L, 29, "Cu*", 29, 34, 29);
        existingPrev.setElement(prev);
        existingPrev.setCount(1);

        when(elementRepository.findById(30L)).thenReturn(Optional.of(zincLike));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(existingPrev));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(baseResources());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        assertThrows(RuntimeException.class, () -> synthesisService.synthesizeBulk(1L, 30L, 1));
        // Частинки не мали бути списані — target мав впасти до 0 ще до мутацій.
        assertEquals(100, p.getNumber() * Math.pow(10, p.getExponent()), 1e-6);
    }

    @Test
    @DisplayName("synthesizeBulk: екзотермічний синтез не перевищує кап 1e308 без brokenInfinity")
    void synthesize_exothermic_respectsEnergyCap() {
        energy.setNumber(1.0);
        energy.setExponent(GameEngine.ENERGY_CAP_EXPONENT); // вже на капі
        save.setBrokenInfinity(false);

        when(elementRepository.findById(2L)).thenReturn(Optional.of(helium));
        PlayerElement existingH = new PlayerElement();
        existingH.setElement(hydrogen);
        existingH.setCount(1);
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(existingH));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(baseResources());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        synthesisService.synthesizeBulk(1L, 2L, 1);

        assertEquals(GameEngine.ENERGY_CAP_EXPONENT, energy.getExponent());
        assertEquals(1.0, energy.getNumber(), 1e-9);
    }

    private Element createElement(Long id, int atomicNumber, String symbol,
                                   long costP, long costN, long costE) {
        Element el = instantiate(Element.class);
        ReflectionTestUtils.setField(el, "id", id);
        ReflectionTestUtils.setField(el, "atomicNumber", atomicNumber);
        ReflectionTestUtils.setField(el, "symbol", symbol);
        ReflectionTestUtils.setField(el, "costProtons", costP);
        ReflectionTestUtils.setField(el, "costNeutrons", costN);
        ReflectionTestUtils.setField(el, "costElectrons", costE);
        return el;
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
