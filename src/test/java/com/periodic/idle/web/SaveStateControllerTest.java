package com.periodic.idle.web;

import com.periodic.idle.content.Resource;
import com.periodic.idle.content.TierUnlockCondition;
import com.periodic.idle.content.TierUnlockConditionRepository;
import com.periodic.idle.engine.AchievementService;
import com.periodic.idle.engine.GameEngine;
import com.periodic.idle.engine.GameStatsService;
import com.periodic.idle.engine.SaveService;
import com.periodic.idle.engine.SaveTransferService;
import com.periodic.idle.player.PlayerResourceRepository;
import com.periodic.idle.player.Save;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@link SaveStateController}: стан ресурсів, tier-unlocks, статистика, досягнення, save init/export/import. */
@SpringBootTest
@AutoConfigureMockMvc
class SaveStateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private PlayerResourceRepository playerResourceRepository;
    @MockitoBean private TierUnlockConditionRepository tierUnlockConditionRepository;
    @MockitoBean private GameEngine gameEngine;
    @MockitoBean private GameStatsService gameStatsService;
    @MockitoBean private SaveService saveService;
    @MockitoBean private SaveTransferService saveTransferService;
    @MockitoBean private AchievementService achievementService;

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
    @DisplayName("GET /api/stats/1 — повертає JSON, делегує GameStatsService.calculateStats")
    void stats_returnsBreakdown() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("multipliers", List.of());
        payload.put("generators", List.of());
        payload.put("totalEnergyPerSec", 0.0);
        when(gameStatsService.calculateStats(1L)).thenReturn(payload);

        mockMvc.perform(get("/api/stats/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.multipliers").isArray())
                .andExpect(jsonPath("$.generators").isArray())
                .andExpect(jsonPath("$.totalEnergyPerSec").value(0.0));
    }

    @Test
    @DisplayName("POST /api/save/init — token у body → повертає saveId з SaveService")
    void initSave_returnsSaveId() throws Exception {
        Save save = newSave(42L);
        when(saveService.findOrCreateByToken("uuid-abc")).thenReturn(save);

        mockMvc.perform(post("/api/save/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"uuid-abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saveId").value(42));

        verify(saveService).findOrCreateByToken("uuid-abc");
    }

    @Test
    @DisplayName("POST /api/save/init — порожній token → 400")
    void initSave_blankToken_returns400() throws Exception {
        when(saveService.findOrCreateByToken(""))
                .thenThrow(new IllegalArgumentException("clientToken required"));

        mockMvc.perform(post("/api/save/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // === Умови розблокування тірів (data-driven) ===

    @Test
    @DisplayName("GET /api/tier-unlocks — повертає JSON масив із tier/resource/minLog10")
    void getTierUnlocks_returnsJson() throws Exception {
        Resource energy = newResource(1L, "E");
        TierUnlockCondition cond = newTierUnlockCondition(1, energy, 308.0);
        when(tierUnlockConditionRepository.findAllByOrderByTierAsc()).thenReturn(List.of(cond));

        mockMvc.perform(get("/api/tier-unlocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].tier").value(1))
                .andExpect(jsonPath("$[0].resource").value("E"))
                .andExpect(jsonPath("$[0].minLog10").value(308.0));
    }

    // === Досягнення ===

    @Test
    @DisplayName("GET /api/achievements/1 — перевіряє і повертає JSON масив")
    void getAchievements_returnsJson() throws Exception {
        when(achievementService.listWithStatus(1L)).thenReturn(List.of(
                Map.of("code", "first_steps", "name", "Перші кроки", "unlocked", true)));

        mockMvc.perform(get("/api/achievements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].code").value("first_steps"))
                .andExpect(jsonPath("$[0].unlocked").value(true));

        verify(achievementService).checkAndUnlock(1L);
    }

    // === Мануальне збереження ===

    @Test
    @DisplayName("GET /api/save-export/1 — повертає JSON із даними збереження")
    void exportSave_returnsJson() throws Exception {
        when(saveTransferService.exportSave(1L)).thenReturn(Map.of("version", 1));

        mockMvc.perform(get("/api/save-export/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("POST /api/save-import — успішне відновлення")
    void importSave_success() throws Exception {
        mockMvc.perform(post("/api/save-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"data\":{\"resources\":[]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(saveTransferService).importSave(eq(1L), anyMap());
    }

    @Test
    @DisplayName("POST /api/save-import — некоректні дані → 400")
    void importSave_invalidData_returns400() throws Exception {
        mockMvc.perform(post("/api/save-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"data\":\"not-an-object\"}"))
                .andExpect(status().isBadRequest());
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

    private Resource newResource(Long id, String code) {
        try {
            var c = Resource.class.getDeclaredConstructor();
            c.setAccessible(true);
            Resource r = c.newInstance();
            org.springframework.test.util.ReflectionTestUtils.setField(r, "id", id);
            org.springframework.test.util.ReflectionTestUtils.setField(r, "code", code);
            return r;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TierUnlockCondition newTierUnlockCondition(int tier, Resource resource, double minLog10) {
        try {
            var c = TierUnlockCondition.class.getDeclaredConstructor();
            c.setAccessible(true);
            TierUnlockCondition t = c.newInstance();
            org.springframework.test.util.ReflectionTestUtils.setField(t, "tier", tier);
            org.springframework.test.util.ReflectionTestUtils.setField(t, "resource", resource);
            org.springframework.test.util.ReflectionTestUtils.setField(t, "minLog10", minLog10);
            return t;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
