package com.periodic.idle.web;

import com.periodic.idle.content.Element;
import com.periodic.idle.content.Resource;
import com.periodic.idle.engine.ExchangeService;
import com.periodic.idle.engine.MatterService;
import com.periodic.idle.player.PlayerElement;
import com.periodic.idle.player.PlayerElementRepository;
import com.periodic.idle.player.PlayerResource;
import com.periodic.idle.player.PlayerResourceRepository;
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

/**
 * {@link MatterController}: Тір 1 — обмін VC→частинки, колапс матерії, Break Infinity,
 * живі бонуси (частинки/цикл/елементи), і player-driven тогли автоматизації.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MatterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private PlayerResourceRepository playerResourceRepository;
    @MockitoBean private PlayerElementRepository playerElementRepository;
    @MockitoBean private SaveRepository saveRepository;
    @MockitoBean private ExchangeService exchangeService;
    @MockitoBean private MatterService matterService;

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
                .andExpect(jsonPath("$.particles.e").value(0))
                .andExpect(jsonPath("$.protonEnergyMult").value(1.0))
                .andExpect(jsonPath("$.neutronCostReduction").value(0.0))
                .andExpect(jsonPath("$.electronCrystalMult").value(1.0))
                // matterCollapses=3 -> CollapseCycleBonus.boost(3) = 10^(2.5*log10(4)) ≈ 32.0
                .andExpect(jsonPath("$.cycleBoost", closeTo(32.0, 0.5)))
                .andExpect(jsonPath("$.vcPersistsAfterCollapses").value(10000));
    }

    @Test
    @DisplayName("GET /api/matter-info/1 — рахує реальні бонуси частинок (ParticleBonus)")
    void matterInfo_includesParticleBonusValues() throws Exception {
        Save save = newSave(1L);

        Resource pRes = newResource(3L, "p");
        PlayerResource p = new PlayerResource();
        p.setResource(pRes);
        p.setNumber(5.0);
        p.setExponent(0);

        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(p));

        mockMvc.perform(get("/api/matter-info/1"))
                .andExpect(status().isOk())
                // saturating(5) = 5/(1+5/1000) ≈ 4.9751; mult = 1 + 0.25 * 4.9751 ≈ 2.2438
                .andExpect(jsonPath("$.protonEnergyMult", closeTo(2.243781, 0.000001)));
    }

    @Test
    @DisplayName("GET /api/matter-info/1 — рахує реальні бонуси елементів (ElementBonus)")
    void matterInfo_includesElementBonusValues() throws Exception {
        Save save = newSave(1L);

        PlayerElement hydrogen = new PlayerElement();
        hydrogen.setElement(newElementWithAtomicNumber(10L, 1));
        hydrogen.setCount(999L);

        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of(hydrogen));

        mockMvc.perform(get("/api/matter-info/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distinctElementsSynthesized").value(1))
                .andExpect(jsonPath("$.totalAtomsSynthesized").value(999))
                // saturating(1,15) = 1/(1+1/15) ≈ 0.9375; mult = 1 + 0.06 * 0.9375 ≈ 1.05625
                .andExpect(jsonPath("$.elementDiversityMult", closeTo(1.05625, 0.00001)))
                // x = log10(999+1) = 3 (< softcap 6) -> mult = 1 + 0.15*3 = 1.45
                .andExpect(jsonPath("$.elementAtomCountMult", closeTo(1.45, 0.00001)));
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
    @DisplayName("POST /api/autoupgrade-toggle — перемикає прапор і повертає новий стан")
    void autoUpgradeToggle_setsFlagFromBody() throws Exception {
        Save save = newSave(1L);
        save.setAutoUpgradeEnabled(false);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(saveRepository.save(save)).thenReturn(save);

        mockMvc.perform(post("/api/autoupgrade-toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoUpgradeEnabled").value(true));
    }

    @Test
    @DisplayName("POST /api/autoupgrade-toggle без enabled — інвертує поточне значення")
    void autoUpgradeToggle_noBody_inverts() throws Exception {
        Save save = newSave(1L);
        save.setAutoUpgradeEnabled(true);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));

        mockMvc.perform(post("/api/autoupgrade-toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoUpgradeEnabled").value(false));
    }

    @Test
    @DisplayName("GET /api/matter-info/1 — включає autoUpgradeEnabled і поріг розблокування")
    void matterInfo_includesAutoUpgradeFlags() throws Exception {
        Save save = newSave(1L);
        save.setAutoUpgradeEnabled(true);
        save.setMatterCollapses(4L);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/matter-info/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoUpgradeEnabled").value(true))
                .andExpect(jsonPath("$.autoUpgradeUnlockCollapses").value(4))
                .andExpect(jsonPath("$.autoUpgradeUnlocked").value(true));
    }

    @Test
    @DisplayName("GET /api/matter-info/1 — autoUpgradeUnlocked=false до 4 колапсів")
    void matterInfo_autoUpgradeNotYetUnlocked() throws Exception {
        Save save = newSave(1L);
        save.setMatterCollapses(1L);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/matter-info/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoUpgradeUnlocked").value(false));
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
