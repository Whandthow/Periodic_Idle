package com.periodic.idle.web;

import com.periodic.idle.engine.*;
import com.periodic.idle.content.*;
import com.periodic.idle.player.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private PlayerResourceRepository playerResourceRepository;
    @MockitoBean private PlayerUpgradeRepository playerUpgradeRepository;
    @MockitoBean private PlayerGeneratorRepository playerGeneratorRepository;
    @MockitoBean private UpgradeRepository upgradeRepository;
    @MockitoBean private GeneratorRepository generatorRepository;
    @MockitoBean private UpgradeService upgradeService;
    @MockitoBean private GeneratorService generatorService;
    @MockitoBean private GameEngine gameEngine;
    @MockitoBean private PrestigeService prestigeService;
    @MockitoBean private ExchangeService exchangeService;

    @Test
    @DisplayName("GET /api/state/1 повертає 200 і JSON масив")
    void getState_returnsJson() throws Exception {
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(gameEngine.calculateProductionPerSec(1L)).thenReturn(new HashMap<>());

        mockMvc.perform(get("/api/state/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/upgrades/1 повертає 200")
    void getUpgrades_returnsJson() throws Exception {
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(upgradeRepository.findAll()).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/upgrades/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/generators/1 повертає 200")
    void getGenerators_returnsJson() throws Exception {
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(generatorRepository.findAll()).thenReturn(new ArrayList<>());
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(gameEngine.calculateGeneratorBreakdown(1L)).thenReturn(new HashMap<>());

        mockMvc.perform(get("/api/generators/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /api/buy-generator — успішна купівля")
    void buyGenerator_success() throws Exception {
        when(generatorService.buyBulk(1L, 1L, 1)).thenReturn(1);

        mockMvc.perform(post("/api/buy-generator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"generatorId\":1,\"amount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.bought").value(1));
    }

    @Test
    @DisplayName("POST /api/buy-upgrade — успішна купівля")
    void buyUpgrade_success() throws Exception {
        when(upgradeService.buyBulk(1L, 1L, 1)).thenReturn(1);

        mockMvc.perform(post("/api/buy-upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"upgradeId\":1,\"amount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("POST /api/buy-upgrade — не вистачає ресурсів → 400")
    void buyUpgrade_notEnough_returns400() throws Exception {
        doThrow(new RuntimeException("Not enough resources"))
                .when(upgradeService).buyBulk(1L, 1L, 1);

        mockMvc.perform(post("/api/buy-upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"upgradeId\":1,\"amount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Not enough resources"));
    }

    @Test
    @DisplayName("POST /api/prestige — успішний престиж")
    void prestige_success() throws Exception {
        when(prestigeService.prestige(1L))
                .thenReturn(new com.periodic.idle.common.BigNum(1.0, 2));

        mockMvc.perform(post("/api/prestige")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.gainNumber").value(1.0))
                .andExpect(jsonPath("$.gainExponent").value(2));
    }

    @Test
    @DisplayName("POST /api/reset — повний скид")
    void reset_success() throws Exception {
        mockMvc.perform(post("/api/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(prestigeService).hardReset(1L);
    }

    @Test
    @DisplayName("POST /api/exchange/split — розщеплення кристалів")
    void splitCrystals_success() throws Exception {
        when(exchangeService.splitCrystals(1L, 10)).thenReturn(10L);

        mockMvc.perform(post("/api/exchange/split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"amount\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.split").value(10));
    }

    @Test
    @DisplayName("GET /api/prestige-info/1 — інфо про потенційний престиж")
    void prestigeInfo() throws Exception {
        when(prestigeService.calcPotentialGain(1L))
                .thenReturn(new com.periodic.idle.common.BigNum(5.0, 3));

        mockMvc.perform(get("/api/prestige-info/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(5.0))
                .andExpect(jsonPath("$.exponent").value(3));
    }
}