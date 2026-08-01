package com.periodic.idle.engine;

import com.periodic.idle.content.Element;
import com.periodic.idle.content.ElementRepository;
import com.periodic.idle.content.Molecule;
import com.periodic.idle.content.MoleculeRepository;
import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoSynthesizeServiceTest {

    @Mock private SaveRepository saveRepository;
    @Mock private ElementRepository elementRepository;
    @Mock private MoleculeRepository moleculeRepository;
    @Mock private SynthesisService synthesisService;
    @Mock private MoleculeService moleculeService;

    @InjectMocks
    private AutoSynthesizeService autoSynthesizeService;

    private Element element(Long id, int atomicNumber) {
        Element el = instantiate(Element.class);
        ReflectionTestUtils.setField(el, "id", id);
        ReflectionTestUtils.setField(el, "atomicNumber", atomicNumber);
        return el;
    }

    private Molecule molecule(Long id) {
        Molecule m = instantiate(Molecule.class);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    @DisplayName("processSave: намагається синтезувати max кожного елемента і молекули")
    void processSave_triesAllElementsAndMolecules() {
        when(elementRepository.findAll()).thenReturn(List.of(element(1L, 1), element(2L, 2)));
        when(moleculeRepository.findAll()).thenReturn(List.of(molecule(10L), molecule(11L)));

        autoSynthesizeService.processSave(1L);

        verify(synthesisService).synthesizeBulk(eq(1L), eq(1L), eq(-1L));
        verify(synthesisService).synthesizeBulk(eq(1L), eq(2L), eq(-1L));
        verify(moleculeService).synthesizeBulk(eq(1L), eq(10L), eq(-1L));
        verify(moleculeService).synthesizeBulk(eq(1L), eq(11L), eq(-1L));
    }

    @Test
    @DisplayName("processSave: елементи в порядку atomicNumber ASC")
    void processSave_elementsInAtomicNumberOrder() {
        when(elementRepository.findAll()).thenReturn(List.of(element(2L, 3), element(1L, 1)));
        when(moleculeRepository.findAll()).thenReturn(new ArrayList<>());

        autoSynthesizeService.processSave(1L);

        var inOrder = inOrder(synthesisService);
        inOrder.verify(synthesisService).synthesizeBulk(1L, 1L, -1L);
        inOrder.verify(synthesisService).synthesizeBulk(1L, 2L, -1L);
    }

    @Test
    @DisplayName("processSave: помилка на одному елементі не блокує решту")
    void processSave_continuesOnError() {
        when(elementRepository.findAll()).thenReturn(List.of(element(1L, 1), element(2L, 2)));
        when(moleculeRepository.findAll()).thenReturn(List.of(molecule(10L)));
        doThrow(new RuntimeException("Not enough particles"))
                .when(synthesisService).synthesizeBulk(1L, 1L, -1L);
        doThrow(new RuntimeException("Not enough atoms"))
                .when(moleculeService).synthesizeBulk(1L, 10L, -1L);

        autoSynthesizeService.processSave(1L);

        verify(synthesisService).synthesizeBulk(1L, 1L, -1L);
        verify(synthesisService).synthesizeBulk(1L, 2L, -1L);
        verify(moleculeService).synthesizeBulk(1L, 10L, -1L);
    }

    @Test
    @DisplayName("tickAutoSynthesize: пропускає save з autoSynthesizeEnabled=false")
    void tickAutoSynthesize_skipsDisabledSave() {
        Save disabled = instantiate(Save.class);
        ReflectionTestUtils.setField(disabled, "id", 99L);
        disabled.setAutoSynthesizeEnabled(false);

        when(saveRepository.findAll()).thenReturn(List.of(disabled));

        autoSynthesizeService.tickAutoSynthesize();

        verifyNoInteractions(elementRepository, moleculeRepository, synthesisService, moleculeService);
    }

    @Test
    @DisplayName("tickAutoSynthesize: для save з autoSynthesizeEnabled=true викликає processSave")
    void tickAutoSynthesize_runsForEnabledSave() {
        Save enabled = instantiate(Save.class);
        ReflectionTestUtils.setField(enabled, "id", 7L);
        enabled.setAutoSynthesizeEnabled(true);

        when(saveRepository.findAll()).thenReturn(List.of(enabled));
        when(elementRepository.findAll()).thenReturn(List.of(element(1L, 1)));
        when(moleculeRepository.findAll()).thenReturn(new ArrayList<>());

        autoSynthesizeService.tickAutoSynthesize();

        verify(synthesisService).synthesizeBulk(eq(7L), eq(1L), eq(-1L));
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
