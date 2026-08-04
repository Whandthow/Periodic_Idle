package com.periodic.idle.web;

import com.periodic.idle.engine.PrestigeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@link PrestigeController}: реінкарнація Тіру 0 — potential gain, престиж, повний скид. */
@SpringBootTest
@AutoConfigureMockMvc
class PrestigeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private PrestigeService prestigeService;

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
