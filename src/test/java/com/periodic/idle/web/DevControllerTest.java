package com.periodic.idle.web;

import com.periodic.idle.content.Element;
import com.periodic.idle.content.ElementRepository;
import com.periodic.idle.content.Molecule;
import com.periodic.idle.content.MoleculeRepository;
import com.periodic.idle.content.Resource;
import com.periodic.idle.content.TierUnlockCondition;
import com.periodic.idle.content.TierUnlockConditionRepository;
import com.periodic.idle.engine.GameEngine;
import com.periodic.idle.player.PlayerElement;
import com.periodic.idle.player.PlayerElementRepository;
import com.periodic.idle.player.PlayerMolecule;
import com.periodic.idle.player.PlayerMoleculeRepository;
import com.periodic.idle.player.PlayerResource;
import com.periodic.idle.player.PlayerResourceRepository;
import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DevControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private GameEngine gameEngine;
    @MockitoBean private PlayerResourceRepository playerResourceRepository;
    @MockitoBean private TierUnlockConditionRepository tierUnlockConditionRepository;
    @MockitoBean private SaveRepository saveRepository;
    @MockitoBean private ElementRepository elementRepository;
    @MockitoBean private MoleculeRepository moleculeRepository;
    @MockitoBean private PlayerElementRepository playerElementRepository;
    @MockitoBean private PlayerMoleculeRepository playerMoleculeRepository;

    @Test
    @DisplayName("POST /api/dev/jump-tier — видає мінімальний прогрес найлегшої OR-умови")
    void jumpToTier_grantsEasiestCondition() throws Exception {
        Resource pRes = newResource(3L, "p");
        Resource eRes = newResource(1L, "E");

        // Тір 1: E>=308 АБО p>=0 (найлегше — p, поріг 0).
        TierUnlockCondition condE = newCondition(eRes, 308.0, 1);
        TierUnlockCondition condP = newCondition(pRes, 0.0, 1);

        PlayerResource playerP = new PlayerResource();
        playerP.setResource(pRes);
        playerP.setNumber(0);
        playerP.setExponent(0);

        when(tierUnlockConditionRepository.findAll()).thenReturn(List.of(condE, condP));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerP));

        mockMvc.perform(post("/api/dev/jump-tier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"tier\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(true))
                .andExpect(jsonPath("$.resource").value("p"))
                .andExpect(jsonPath("$.exponent").value(0));

        org.junit.jupiter.api.Assertions.assertEquals(1.0, playerP.getNumber(), 1e-9);
    }

    @Test
    @DisplayName("POST /api/dev/jump-tier — тір без умов повертає granted=false")
    void jumpToTier_noConditions_returnsFalse() throws Exception {
        when(tierUnlockConditionRepository.findAll()).thenReturn(new ArrayList<>());

        mockMvc.perform(post("/api/dev/jump-tier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"tier\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(false));
    }

    @Test
    @DisplayName("POST /api/dev/jump-tier — поріг з дробовою частиною округлюється вгору")
    void jumpToTier_fractionalThreshold_roundsUp() throws Exception {
        Resource pRes = newResource(3L, "p");
        TierUnlockCondition cond = newCondition(pRes, 3.2, 2);
        PlayerResource playerP = new PlayerResource();
        playerP.setResource(pRes);

        when(tierUnlockConditionRepository.findAll()).thenReturn(List.of(cond));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerP));

        mockMvc.perform(post("/api/dev/jump-tier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"tier\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exponent").value(4));
    }

    @Test
    @DisplayName("POST /api/dev/grant-resource — додає точну кількість (не показник степеня) до ресурсу")
    void grantResource_addsLinearAmount() throws Exception {
        Resource pRes = newResource(3L, "p");
        PlayerResource playerP = new PlayerResource();
        playerP.setResource(pRes);
        playerP.setNumber(0);
        playerP.setExponent(0);

        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerP));

        mockMvc.perform(post("/api/dev/grant-resource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"resourceCode\":\"p\",\"amount\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("p"))
                .andExpect(jsonPath("$.number").value(5.0))
                .andExpect(jsonPath("$.exponent").value(2));
    }

    @Test
    @DisplayName("POST /api/dev/grant-resource — неположна кількість повертає 400")
    void grantResource_rejectsNonPositiveAmount() throws Exception {
        mockMvc.perform(post("/api/dev/grant-resource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"resourceCode\":\"p\",\"amount\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/dev/grant-element — створює новий player_elements рядок, якщо його ще нема")
    void grantElement_createsRowWhenMissing() throws Exception {
        Save save = new Save();
        Element helium = instantiate(Element.class);
        ReflectionTestUtils.setField(helium, "id", 2L);
        ReflectionTestUtils.setField(helium, "symbol", "He");

        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(elementRepository.findById(2L)).thenReturn(Optional.of(helium));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        mockMvc.perform(post("/api/dev/grant-element")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"elementId\":2,\"amount\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.element").value("He"))
                .andExpect(jsonPath("$.count").value(100));

        verify(playerElementRepository).save(any(PlayerElement.class));
    }

    @Test
    @DisplayName("POST /api/dev/grant-molecule — додає до вже існуючого player_molecules рядка")
    void grantMolecule_addsToExistingRow() throws Exception {
        Molecule water = instantiate(Molecule.class);
        ReflectionTestUtils.setField(water, "id", 4L);
        ReflectionTestUtils.setField(water, "formula", "H2O");

        PlayerMolecule pm = new PlayerMolecule();
        pm.setMolecule(water);
        pm.setCount(10);

        when(saveRepository.findById(1L)).thenReturn(Optional.of(new Save()));
        when(moleculeRepository.findById(4L)).thenReturn(Optional.of(water));
        when(playerMoleculeRepository.findBySaveId(1L)).thenReturn(List.of(pm));

        mockMvc.perform(post("/api/dev/grant-molecule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saveId\":1,\"moleculeId\":4,\"amount\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.molecule").value("H2O"))
                .andExpect(jsonPath("$.count").value(35));
    }

    private Resource newResource(Long id, String code) {
        Resource r = instantiate(Resource.class);
        ReflectionTestUtils.setField(r, "id", id);
        ReflectionTestUtils.setField(r, "code", code);
        return r;
    }

    private TierUnlockCondition newCondition(Resource resource, double minLog10, int tier) {
        TierUnlockCondition c = instantiate(TierUnlockCondition.class);
        ReflectionTestUtils.setField(c, "resource", resource);
        ReflectionTestUtils.setField(c, "minLog10", minLog10);
        ReflectionTestUtils.setField(c, "tier", tier);
        return c;
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
