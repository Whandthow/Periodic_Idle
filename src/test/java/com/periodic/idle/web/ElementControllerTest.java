package com.periodic.idle.web;

import com.periodic.idle.content.Element;
import com.periodic.idle.content.ElementRepository;
import com.periodic.idle.engine.SynthesisService;
import com.periodic.idle.player.PlayerElement;
import com.periodic.idle.player.PlayerElementRepository;
import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@link ElementController}: Тір 2 — періодична таблиця (гейти зорі/гіпернови) і синтез атомів. */
@SpringBootTest
@AutoConfigureMockMvc
class ElementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private ElementRepository elementRepository;
    @MockitoBean private PlayerElementRepository playerElementRepository;
    @MockitoBean private SaveRepository saveRepository;
    @MockitoBean private SynthesisService synthesisService;

    @Test
    @DisplayName("GET /api/elements/1 — повертає JSON масив із прапором unlocked")
    void getElements_returnsJson() throws Exception {
        when(playerElementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(elementRepository.findAll()).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/elements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/elements/1 — елемент Z=4 (зоряний) locked без запаленої зорі, з поясненням")
    void getElements_starGate_lockedWithReason() throws Exception {
        Element beryllium = newElementWithAtomicNumber(10L, 4);
        when(playerElementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(elementRepository.findAll()).thenReturn(List.of(beryllium));
        when(synthesisService.heliumCount(1L)).thenReturn(5L);

        mockMvc.perform(get("/api/elements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requiresStar").value(true))
                .andExpect(jsonPath("$[0].unlocked").value(false))
                .andExpect(jsonPath("$[0].lockedReason").value(org.hamcrest.Matchers.containsString("зоря")));
    }

    @Test
    @DisplayName("GET /api/elements/1 — елемент Z=27 (важче за залізо) locked без Гіпернови, з поясненням")
    void getElements_hypernovaGate_lockedWithReason() throws Exception {
        Element cobalt = newElementWithAtomicNumber(11L, 27);
        when(playerElementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(elementRepository.findAll()).thenReturn(List.of(cobalt));
        when(synthesisService.heliumCount(1L)).thenReturn(5000L);

        mockMvc.perform(get("/api/elements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requiresHypernova").value(true))
                .andExpect(jsonPath("$[0].heavyElementsUnlocked").value(false))
                .andExpect(jsonPath("$[0].unlocked").value(false))
                .andExpect(jsonPath("$[0].lockedReason").value(org.hamcrest.Matchers.containsString("наднова")));
    }

    @Test
    @DisplayName("GET /api/elements/1 — елемент Z=27 розблокований, коли save.hypernovaCount >= 1")
    void getElements_hypernovaGate_unlockedAfterHypernova() throws Exception {
        Element cobalt = newElementWithAtomicNumber(11L, 27);
        Element prev = newElementWithAtomicNumber(12L, 26);
        PlayerElement discoveredPrev = new PlayerElement();
        discoveredPrev.setElement(prev);
        discoveredPrev.setCount(1);

        Save save = new Save();
        save.setHypernovaCount(1L);

        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(discoveredPrev));
        when(elementRepository.findAll()).thenReturn(List.of(cobalt));
        when(synthesisService.heliumCount(1L)).thenReturn(5000L);
        when(saveRepository.findById(1L)).thenReturn(java.util.Optional.of(save));

        mockMvc.perform(get("/api/elements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].heavyElementsUnlocked").value(true))
                .andExpect(jsonPath("$[0].unlocked").value(true));
    }

    @Test
    @DisplayName("POST /api/synthesize — успішний синтез")
    void synthesize_success() throws Exception {
        when(synthesisService.synthesizeBulk(1L, 1L, 1)).thenReturn(1L);

        mockMvc.perform(post("/api/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"elementId\":1,\"amount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.synthesized").value(1));
    }

    @Test
    @DisplayName("POST /api/synthesize — попередній елемент не відкрито → 400")
    void synthesize_previousNotUnlocked_returns400() throws Exception {
        doThrow(new RuntimeException("Спочатку синтезуйте попередній елемент у таблиці"))
                .when(synthesisService).synthesizeBulk(1L, 2L, 1);

        mockMvc.perform(post("/api/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"elementId\":2,\"amount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Спочатку синтезуйте попередній елемент у таблиці"));
    }

    private Element newElementWithAtomicNumber(Long id, int atomicNumber) {
        try {
            var c = Element.class.getDeclaredConstructor();
            c.setAccessible(true);
            Element el = c.newInstance();
            org.springframework.test.util.ReflectionTestUtils.setField(el, "id", id);
            org.springframework.test.util.ReflectionTestUtils.setField(el, "atomicNumber", atomicNumber);
            org.springframework.test.util.ReflectionTestUtils.setField(el, "symbol", "X");
            org.springframework.test.util.ReflectionTestUtils.setField(el, "name", "Test");
            org.springframework.test.util.ReflectionTestUtils.setField(el, "shellConfig", "2,2");
            return el;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
