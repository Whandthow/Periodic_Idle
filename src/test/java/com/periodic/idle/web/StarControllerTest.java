package com.periodic.idle.web;

import com.periodic.idle.content.Element;
import com.periodic.idle.content.Star;
import com.periodic.idle.content.StarRepository;
import com.periodic.idle.engine.StarService;
import com.periodic.idle.player.PlayerElement;
import com.periodic.idle.player.PlayerElementRepository;
import com.periodic.idle.player.PlayerStar;
import com.periodic.idle.player.PlayerStarRepository;
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
import java.util.Optional;

import static org.hamcrest.Matchers.closeTo;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@link StarController}: Тір 4 — зорі головної послідовності, перегляд стану і купівля рівнів. */
@SpringBootTest
@AutoConfigureMockMvc
class StarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private StarRepository starRepository;
    @MockitoBean private PlayerStarRepository playerStarRepository;
    @MockitoBean private PlayerElementRepository playerElementRepository;
    @MockitoBean private SaveRepository saveRepository;
    @MockitoBean private StarService starService;

    @Test
    @DisplayName("GET /api/stars/1 — повертає список зірок, наявний Гідроген, лічильник гіпернов")
    void getStars_returnsJson() throws Exception {
        Save save = newSave(1L);
        org.springframework.test.util.ReflectionTestUtils.setField(save, "hypernovaCount", 2L);

        Star star = newStar(1L, "main_sequence", "Зоря головної послідовності",
                1000000L, 2.0, 4L, 1L, 0.01);

        PlayerElement hydrogen = new PlayerElement();
        hydrogen.setElement(newElementWithAtomicNumber(10L, 1));
        hydrogen.setCount(5_000_000L);

        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(starRepository.findAll()).thenReturn(List.of(star));
        when(playerStarRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(hydrogen));

        mockMvc.perform(get("/api/stars/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hydrogenAvailable").value(5000000))
                .andExpect(jsonPath("$.hypernovaCount").value(2))
                .andExpect(jsonPath("$.stars[0].code").value("main_sequence"))
                .andExpect(jsonPath("$.stars[0].level").value(0))
                .andExpect(jsonPath("$.stars[0].nextLevelCostHydrogen").value(1000000))
                .andExpect(jsonPath("$.cnoCatalystActive").value(false))
                .andExpect(jsonPath("$.cnoCatalystMult").value(1.0));
    }

    @Test
    @DisplayName("GET /api/stars/1 — CNO-каталіз активний (C+N+O синтезовані) подвоює пропускну здатність")
    void getStars_cnoCatalystActive_doublesRates() throws Exception {
        Save save = newSave(1L);

        Star star = newStar(1L, "main_sequence", "Зоря головної послідовності",
                1000000L, 2.0, 4L, 1L, 0.01);
        PlayerStar playerStar = new PlayerStar();
        playerStar.setStar(star);
        playerStar.setLevel(1);

        PlayerElement hydrogen = new PlayerElement();
        hydrogen.setElement(newElementWithAtomicNumber(10L, 1));
        hydrogen.setCount(5_000_000L);
        PlayerElement carbon = new PlayerElement();
        carbon.setElement(newElementWithAtomicNumber(11L, 6));
        carbon.setCount(1L);
        PlayerElement nitrogen = new PlayerElement();
        nitrogen.setElement(newElementWithAtomicNumber(12L, 7));
        nitrogen.setCount(1L);
        PlayerElement oxygen = new PlayerElement();
        oxygen.setElement(newElementWithAtomicNumber(13L, 8));
        oxygen.setCount(1L);

        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(starRepository.findAll()).thenReturn(List.of(star));
        when(playerStarRepository.findBySaveId(1L)).thenReturn(List.of(playerStar));
        when(playerElementRepository.findBySaveId(1L))
                .thenReturn(List.of(hydrogen, carbon, nitrogen, oxygen));

        mockMvc.perform(get("/api/stars/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnoCatalystActive").value(true))
                .andExpect(jsonPath("$.cnoCatalystMult").value(2.0))
                // без каталізу: 0.01*1*4=0.04 H/с; з каталізом ×2 = 0.08
                .andExpect(jsonPath("$.stars[0].fuelHPerSec", closeTo(0.08, 0.0001)))
                .andExpect(jsonPath("$.stars[0].outputHePerSec", closeTo(0.02, 0.0001)));
    }

    @Test
    @DisplayName("POST /api/buy-star — успішна купівля рівня")
    void buyStar_success() throws Exception {
        when(starService.buyLevel(1L, 1L, 1)).thenReturn(1);

        mockMvc.perform(post("/api/buy-star")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"starId\":1,\"amount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.bought").value(1));
    }

    @Test
    @DisplayName("POST /api/buy-star — недостатньо Гідрогену -> 400")
    void buyStar_notEnoughHydrogen_returns400() throws Exception {
        doThrow(new RuntimeException("Not enough hydrogen"))
                .when(starService).buyLevel(1L, 1L, 1);

        mockMvc.perform(post("/api/buy-star")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"starId\":1,\"amount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Not enough hydrogen"));
    }

    private Save newSave(Long id) {
        try {
            var c = Save.class.getDeclaredConstructor();
            c.setAccessible(true);
            Save s = c.newInstance();
            org.springframework.test.util.ReflectionTestUtils.setField(s, "id", id);
            return s;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private Star newStar(Long id, String code, String name, long baseCostHydrogen,
                          double costMultiplier, long fuelHPerEvent, long outputHePerEvent,
                          double eventsPerSecPerLevel) {
        try {
            var c = Star.class.getDeclaredConstructor();
            c.setAccessible(true);
            Star star = c.newInstance();
            org.springframework.test.util.ReflectionTestUtils.setField(star, "id", id);
            org.springframework.test.util.ReflectionTestUtils.setField(star, "code", code);
            org.springframework.test.util.ReflectionTestUtils.setField(star, "name", name);
            org.springframework.test.util.ReflectionTestUtils.setField(star, "baseCostHydrogen", baseCostHydrogen);
            org.springframework.test.util.ReflectionTestUtils.setField(star, "costMultiplier", costMultiplier);
            org.springframework.test.util.ReflectionTestUtils.setField(star, "fuelHPerEvent", fuelHPerEvent);
            org.springframework.test.util.ReflectionTestUtils.setField(star, "outputHePerEvent", outputHePerEvent);
            org.springframework.test.util.ReflectionTestUtils.setField(star, "eventsPerSecPerLevel", eventsPerSecPerLevel);
            return star;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
