package com.periodic.idle.engine;

import com.periodic.idle.content.Resource;
import com.periodic.idle.player.PlayerResource;
import com.periodic.idle.player.PlayerResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest {

    @Mock private PlayerResourceRepository playerResourceRepository;

    @InjectMocks
    private ExchangeService exchangeService;

    private PlayerResource vc;
    private PlayerResource p;
    private PlayerResource n;
    private PlayerResource e;

    @BeforeEach
    void setUp() {
        vc = makePlayerResource("VC", 1.0, 2); // 100 VC
        p  = makePlayerResource("p", 0, 0);
        n  = makePlayerResource("n", 0, 0);
        e  = makePlayerResource("e", 0, 0);
    }

    @Test
    @DisplayName("split amount=1: -1 VC, +1 p/n/e")
    void splitOne() {
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(vc, p, n, e));

        long got = exchangeService.splitCrystals(1L, 1);

        assertEquals(1, got);
        // 100 - 1 = 99
        assertEquals(9.9, vc.getNumber(), 0.001);
        assertEquals(1, vc.getExponent());
        assertEachParticle(1);
    }

    @Test
    @DisplayName("split amount=10: -10 VC, +10 p/n/e")
    void splitTen() {
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(vc, p, n, e));

        long got = exchangeService.splitCrystals(1L, 10);

        assertEquals(10, got);
        // 100 - 10 = 90
        assertEquals(9.0, vc.getNumber(), 0.001);
        assertEquals(1, vc.getExponent());
        assertEachParticle(10);
    }

    @Test
    @DisplayName("split amount=-1 (Max): витрачає усі кристали")
    void splitMax() {
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(vc, p, n, e));

        long got = exchangeService.splitCrystals(1L, -1);

        assertEquals(100, got);
        assertEquals(0, vc.getNumber(), 0.001);
        assertEquals(0, vc.getExponent());
        assertEachParticle(100);
    }

    @Test
    @DisplayName("split amount більше за наявне — обмежено кількістю кристалів")
    void splitMoreThanAvailable() {
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(vc, p, n, e));

        long got = exchangeService.splitCrystals(1L, 500);

        assertEquals(100, got);
        assertEachParticle(100);
    }

    @Test
    @DisplayName("split при 0 VC — кидає помилку")
    void splitNoCrystals() {
        vc.setNumber(0);
        vc.setExponent(0);
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(vc, p, n, e));

        assertThrows(RuntimeException.class, () -> exchangeService.splitCrystals(1L, 1));
        assertEquals(0, p.getNumber());
    }

    @Test
    @DisplayName("split amount=0 — no-op, повертає 0 без помилки")
    void splitZero() {
        long got = exchangeService.splitCrystals(1L, 0);
        assertEquals(0, got);
        verifyNoInteractions(playerResourceRepository);
    }

    @Test
    @DisplayName("split накопичує частинки при повторних викликах")
    void splitAccumulates() {
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(vc, p, n, e));

        exchangeService.splitCrystals(1L, 5);
        // Ресурси мутуються in-place; наступний виклик побачить 95 VC і 5 p.
        exchangeService.splitCrystals(1L, 7);

        assertEachParticle(12);
        // 100 - 12 = 88
        assertEquals(8.8, vc.getNumber(), 0.001);
        assertEquals(1, vc.getExponent());
    }

    @Test
    @DisplayName("split коли відсутній ресурс p — кидає помилку")
    void splitMissingParticleResource() {
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(vc, n, e)); // нема p

        assertThrows(RuntimeException.class, () -> exchangeService.splitCrystals(1L, 1));
    }

    @Test
    @DisplayName("split великих VC: 1e6 -> обмежено BULK_HARD_CAP")
    void splitHardCap() {
        vc.setNumber(1.0);
        vc.setExponent(7); // 1e7 VC
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(vc, p, n, e));

        long got = exchangeService.splitCrystals(1L, -1);

        // BULK_HARD_CAP = 1_000_000
        assertEquals(1_000_000, got);
    }

    // --- helpers ---

    private void assertEachParticle(long expected) {
        double pVal = p.getNumber() * Math.pow(10, p.getExponent());
        double nVal = n.getNumber() * Math.pow(10, n.getExponent());
        double eVal = e.getNumber() * Math.pow(10, e.getExponent());
        assertEquals(expected, pVal, 0.01, "p");
        assertEquals(expected, nVal, 0.01, "n");
        assertEquals(expected, eVal, 0.01, "e");
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
