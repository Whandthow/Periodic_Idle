package com.periodic.idle.web;

import com.periodic.idle.content.GeneratorRepository;
import com.periodic.idle.content.UpgradeRepository;
import com.periodic.idle.engine.GameEngine;
import com.periodic.idle.engine.GeneratorService;
import com.periodic.idle.engine.UpgradeService;
import com.periodic.idle.player.PlayerGeneratorRepository;
import com.periodic.idle.player.PlayerResourceRepository;
import com.periodic.idle.player.PlayerUpgradeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@link GeneratorUpgradeController}: Тір 0 — генератори і апгрейди, перегляд і купівля. */
@SpringBootTest
@AutoConfigureMockMvc
class GeneratorUpgradeControllerTest {

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
}
