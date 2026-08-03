package com.periodic.idle.engine;

import com.periodic.idle.content.Element;
import com.periodic.idle.player.PlayerElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ElementBonusTest {

    @Test
    @DisplayName("distinctCount: рахує лише елементи з count > 0")
    void distinctCount_ignoresZeroCounts() {
        List<PlayerElement> elements = List.of(
                makePlayerElement(1, 10L),
                makePlayerElement(2, 0L),
                makePlayerElement(3, 5L));
        assertEquals(2L, ElementBonus.distinctCount(elements));
    }

    @Test
    @DisplayName("totalAtomCount: сумує count усіх елементів")
    void totalAtomCount_sumsAll() {
        List<PlayerElement> elements = List.of(
                makePlayerElement(1, 100L),
                makePlayerElement(2, 899L));
        assertEquals(999L, ElementBonus.totalAtomCount(elements));
    }

    @Test
    @DisplayName("totalAtomCount: захист від переповнення long -> Long.MAX_VALUE")
    void totalAtomCount_overflowGuard() {
        List<PlayerElement> elements = List.of(
                makePlayerElement(1, Long.MAX_VALUE - 1),
                makePlayerElement(2, Long.MAX_VALUE - 1));
        assertEquals(Long.MAX_VALUE, ElementBonus.totalAtomCount(elements));
    }

    @Test
    @DisplayName("diversityMult: без елементів -> 1.0")
    void diversityMult_noElements_returnsOne() {
        assertEquals(1.0, ElementBonus.diversityMult(new ArrayList<>()), 1e-9);
    }

    @Test
    @DisplayName("diversityMult: 5 різних елементів -> 1 + saturating(5,15) * 0.06")
    void diversityMult_formula() {
        List<PlayerElement> elements = List.of(
                makePlayerElement(1, 1L), makePlayerElement(2, 1L), makePlayerElement(3, 1L),
                makePlayerElement(4, 1L), makePlayerElement(5, 1L));
        // saturating(5,15) = 5 / (1 + 5/15) = 3.75; mult = 1 + 0.06 * 3.75 = 1.225
        assertEquals(1.225, ElementBonus.diversityMult(elements), 1e-9);
    }

    @Test
    @DisplayName("diversityMult: насичення — стеля 1 + 0.06*15 при усіх 36 елементах")
    void diversityMult_saturatesAtFullTable() {
        List<PlayerElement> elements = new ArrayList<>();
        for (int z = 1; z <= 36; z++) elements.add(makePlayerElement(z, 1L));
        double mult = ElementBonus.diversityMult(elements);
        assertTrue(Double.isFinite(mult));
        assertTrue(mult < 1.0 + 0.06 * 15.0 + 1e-6, "мультиплікатор має бути обмежений стелею насичення");
    }

    @Test
    @DisplayName("atomCountMult: без атомів -> 1.0")
    void atomCountMult_noAtoms_returnsOne() {
        assertEquals(1.0, ElementBonus.atomCountMult(new ArrayList<>()), 1e-9);
    }

    @Test
    @DisplayName("atomCountMult: 999 атомів (лінійна ділянка, < softcap) -> 1 + 0.15 * log10(1000)")
    void atomCountMult_belowSoftcap_linear() {
        List<PlayerElement> elements = List.of(makePlayerElement(1, 999L));
        // x = log10(999+1) = 3 (точно), effectiveX = 3 (< softcap 6) -> mult = 1 + 0.15*3 = 1.45
        assertEquals(1.45, ElementBonus.atomCountMult(elements), 1e-9);
    }

    @Test
    @DisplayName("atomCountMult: рівно на межі softcap (999 999 атомів) -> 1 + 0.15 * 6, ще без tanh")
    void atomCountMult_atSoftcapBoundary() {
        List<PlayerElement> elements = List.of(makePlayerElement(1, 999_999L));
        // x = log10(999999+1) = 6 (точно) -> ще на межі, effectiveX = 6 -> mult = 1.9
        assertEquals(1.9, ElementBonus.atomCountMult(elements), 1e-9);
    }

    @Test
    @DisplayName("atomCountMult: 'нереальна' кількість атомів -> суворо обмежений хард-кап")
    void atomCountMult_hardCapNeverExceeded() {
        List<PlayerElement> elements = List.of(makePlayerElement(1, Long.MAX_VALUE));
        double mult = ElementBonus.atomCountMult(elements);
        assertTrue(Double.isFinite(mult));
        // Теоретична стеля: 1 + 0.15 * (6 + 6) = 2.8 (асимптота, ніколи не досягається точно).
        assertTrue(mult < 2.8, "мультиплікатор має бути суворо обмежений хард-кап стелею");
        assertTrue(mult > 1.9, "при астрономічній кількості атомів множник має відчутно перевищувати софт-кап");
    }

    @Test
    @DisplayName("atomCountMult: монотонно зростає з кількістю атомів")
    void atomCountMult_monotonicallyIncreasing() {
        double small = ElementBonus.atomCountMult(List.of(makePlayerElement(1, 100L)));
        double medium = ElementBonus.atomCountMult(List.of(makePlayerElement(1, 1_000_000L)));
        double large = ElementBonus.atomCountMult(List.of(makePlayerElement(1, 1_000_000_000_000L)));
        assertTrue(small < medium);
        assertTrue(medium < large);
    }

    @Test
    @DisplayName("cnoCatalystMult: бракує хоча б одного з C/N/O -> 1.0")
    void cnoCatalystMult_missingOne_returnsOne() {
        List<PlayerElement> elements = List.of(makePlayerElement(6, 1L), makePlayerElement(7, 1L)); // без O (Z=8)
        assertEquals(1.0, ElementBonus.cnoCatalystMult(elements), 1e-9);
    }

    @Test
    @DisplayName("cnoCatalystMult: C+N+O усі синтезовані -> CNO_CATALYST_MULT")
    void cnoCatalystMult_allPresent_returnsCatalystMult() {
        List<PlayerElement> elements = List.of(
                makePlayerElement(6, 1L), makePlayerElement(7, 1L), makePlayerElement(8, 1L));
        assertEquals(ElementBonus.CNO_CATALYST_MULT, ElementBonus.cnoCatalystMult(elements), 1e-9);
    }

    @Test
    @DisplayName("cnoCatalystMult: C/N/O присутні, але count=0 (ще не синтезовано) -> 1.0")
    void cnoCatalystMult_zeroCounts_returnsOne() {
        List<PlayerElement> elements = List.of(
                makePlayerElement(6, 0L), makePlayerElement(7, 0L), makePlayerElement(8, 0L));
        assertEquals(1.0, ElementBonus.cnoCatalystMult(elements), 1e-9);
    }

    private PlayerElement makePlayerElement(int atomicNumber, long count) {
        Element el = instantiate(Element.class);
        ReflectionTestUtils.setField(el, "atomicNumber", atomicNumber);
        PlayerElement pe = instantiate(PlayerElement.class);
        pe.setElement(el);
        pe.setCount(count);
        return pe;
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
