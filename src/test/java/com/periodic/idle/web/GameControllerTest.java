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
    @MockitoBean private MatterService matterService;
    @MockitoBean private SaveRepository saveRepository;
    @MockitoBean private SaveService saveService;
    @MockitoBean private ElementRepository elementRepository;
    @MockitoBean private PlayerElementRepository playerElementRepository;
    @MockitoBean private SynthesisService synthesisService;
    @MockitoBean private SaveTransferService saveTransferService;
    @MockitoBean private TierUnlockConditionRepository tierUnlockConditionRepository;
    @MockitoBean private AchievementService achievementService;
    @MockitoBean private MoleculeRepository moleculeRepository;
    @MockitoBean private PlayerMoleculeRepository playerMoleculeRepository;
    @MockitoBean private MoleculeService moleculeService;

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

    // === Тір 1: Колапс матерії, зламана нескінченність, статистика, автобай-тоггл ===

    @Test
    @DisplayName("GET /api/matter-info/1 — повертає прапори тіру і кількість частинок")
    void matterInfo_returnsState() throws Exception {
        Save save = newSave(1L);
        save.setBrokenInfinity(false);
        save.setMatterCollapses(3L);
        save.setAutobuyEnabled(true);

        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/matter-info/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brokenInfinity").value(false))
                .andExpect(jsonPath("$.matterCollapses").value(3))
                .andExpect(jsonPath("$.autobuyEnabled").value(true))
                .andExpect(jsonPath("$.energyCapLog10").value(308))
                .andExpect(jsonPath("$.particles.p").value(0))
                .andExpect(jsonPath("$.particles.n").value(0))
                .andExpect(jsonPath("$.particles.e").value(0));
    }

    @Test
    @DisplayName("POST /api/matter-collapse — успішний колапс із вибором частинки")
    void matterCollapse_success() throws Exception {
        mockMvc.perform(post("/api/matter-collapse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"particle\":\"p\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.particle").value("p"));

        verify(matterService).collapse(1L, "p");
    }

    @Test
    @DisplayName("POST /api/matter-collapse — недостатньо енергії → 400")
    void matterCollapse_notReady_returns400() throws Exception {
        doThrow(new RuntimeException("Потрібно 1e308 енергії"))
                .when(matterService).collapse(1L, "p");

        mockMvc.perform(post("/api/matter-collapse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"particle\":\"p\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Потрібно 1e308 енергії"));
    }

    @Test
    @DisplayName("POST /api/break-infinity — викликає MatterService")
    void breakInfinity_success() throws Exception {
        mockMvc.perform(post("/api/break-infinity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(matterService).breakInfinity(1L);
    }

    @Test
    @DisplayName("POST /api/autobuy-toggle — перемикає прапор і повертає новий стан")
    void autobuyToggle_setsFlagFromBody() throws Exception {
        Save save = newSave(1L);
        save.setAutobuyEnabled(true);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(saveRepository.save(save)).thenReturn(save);

        mockMvc.perform(post("/api/autobuy-toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autobuyEnabled").value(false));
    }

    @Test
    @DisplayName("POST /api/autobuy-toggle без enabled — інвертує поточне значення")
    void autobuyToggle_noBody_inverts() throws Exception {
        Save save = newSave(1L);
        save.setAutobuyEnabled(true);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        mockMvc.perform(post("/api/autobuy-toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autobuyEnabled").value(false));
    }

    @Test
    @DisplayName("POST /api/autosynthesize-toggle — перемикає прапор і повертає новий стан")
    void autoSynthesizeToggle_setsFlagFromBody() throws Exception {
        Save save = newSave(1L);
        save.setAutoSynthesizeEnabled(false);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(saveRepository.save(save)).thenReturn(save);

        mockMvc.perform(post("/api/autosynthesize-toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoSynthesizeEnabled").value(true));
    }

    @Test
    @DisplayName("POST /api/autosynthesize-toggle без enabled — інвертує поточне значення")
    void autoSynthesizeToggle_noBody_inverts() throws Exception {
        Save save = newSave(1L);
        save.setAutoSynthesizeEnabled(true);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        mockMvc.perform(post("/api/autosynthesize-toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoSynthesizeEnabled").value(false));
    }

    @Test
    @DisplayName("GET /api/matter-info/1 — включає autoSynthesizeEnabled")
    void matterInfo_includesAutoSynthesizeFlag() throws Exception {
        Save save = newSave(1L);
        save.setAutoSynthesizeEnabled(true);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/matter-info/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoSynthesizeEnabled").value(true));
    }

    @Test
    @DisplayName("GET /api/stats/1 — повертає JSON, делегує GameEngine.calculateStats")
    void stats_returnsBreakdown() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("multipliers", List.of());
        payload.put("generators", List.of());
        payload.put("totalEnergyPerSec", 0.0);
        when(gameEngine.calculateStats(1L)).thenReturn(payload);

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

    // === Тір 2: Періодична таблиця ===

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

    // === Тір 3: Молекули ===

    @Test
    @DisplayName("GET /api/molecules/1 — повертає JSON масив")
    void getMolecules_returnsJson() throws Exception {
        when(playerMoleculeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(moleculeRepository.findAll()).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/molecules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /api/synthesize-molecule — успішний синтез")
    void synthesizeMolecule_success() throws Exception {
        when(moleculeService.synthesizeBulk(1L, 1L, 1)).thenReturn(1L);

        mockMvc.perform(post("/api/synthesize-molecule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"moleculeId\":1,\"amount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.synthesized").value(1));
    }

    @Test
    @DisplayName("POST /api/synthesize-molecule — недостатньо атомів → 400")
    void synthesizeMolecule_notEnoughAtoms_returns400() throws Exception {
        doThrow(new RuntimeException("Not enough atoms"))
                .when(moleculeService).synthesizeBulk(1L, 1L, 1);

        mockMvc.perform(post("/api/synthesize-molecule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"moleculeId\":1,\"amount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Not enough atoms"));
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
}