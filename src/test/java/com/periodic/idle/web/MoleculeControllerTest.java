package com.periodic.idle.web;

import com.periodic.idle.content.MoleculeRepository;
import com.periodic.idle.engine.MoleculeService;
import com.periodic.idle.player.PlayerMoleculeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@link MoleculeController}: Тір 3 — молекули, перегляд рецептів і синтез з атомів. */
@SpringBootTest
@AutoConfigureMockMvc
class MoleculeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private MoleculeRepository moleculeRepository;
    @MockitoBean private PlayerMoleculeRepository playerMoleculeRepository;
    @MockitoBean private MoleculeService moleculeService;

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
}
