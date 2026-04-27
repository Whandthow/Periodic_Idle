package com.periodic.idle.engine;

import com.periodic.idle.content.Generator;
import com.periodic.idle.content.GeneratorRepository;
import com.periodic.idle.content.Upgrade;
import com.periodic.idle.player.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoBuyServiceTest {

    @Mock private SaveRepository saveRepository;
    @Mock private PlayerUpgradeRepository playerUpgradeRepository;
    @Mock private PlayerGeneratorRepository playerGeneratorRepository;
    @Mock private GeneratorRepository generatorRepository;
    @Mock private GeneratorService generatorService;

    @InjectMocks
    private AutoBuyService autoBuyService;

    private PlayerGenerator gen(Long id) {
        Generator g = instantiate(Generator.class);
        ReflectionTestUtils.setField(g, "id", id);
        PlayerGenerator pg = instantiate(PlayerGenerator.class);
        ReflectionTestUtils.setField(pg, "id", id);
        pg.setGenerator(g);
        pg.setLevel(1);
        return pg;
    }

    private PlayerUpgrade autobuy(int level) {
        Upgrade u = instantiate(Upgrade.class);
        ReflectionTestUtils.setField(u, "effectType", "AUTOBUY");
        PlayerUpgrade pu = new PlayerUpgrade();
        pu.setUpgrade(u);
        pu.setLevel(level);
        return pu;
    }

    @Test
    @DisplayName("AUTOBUY level 0: не купує нічого")
    void autoBuy_disabled() {
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        autoBuyService.processSave(1L);

        verifyNoInteractions(generatorService);
    }

    @Test
    @DisplayName("AUTOBUY level 2: намагається купити перші 2 генератори у режимі Max")
    void autoBuy_buysFirstN() {
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(autobuy(2)));
        when(playerGeneratorRepository.findBySaveId(1L))
                .thenReturn(List.of(gen(1L), gen(2L), gen(3L)));

        autoBuyService.processSave(1L);

        verify(generatorService).buyBulk(eq(1L), eq(1L), eq(-1));
        verify(generatorService).buyBulk(eq(1L), eq(2L), eq(-1));
        verify(generatorService, never()).buyBulk(eq(1L), eq(3L), anyInt());
    }

    @Test
    @DisplayName("AUTOBUY: якщо buyBulk кидає — наступні все одно виконуються")
    void autoBuy_continuesOnError() {
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(autobuy(3)));
        when(playerGeneratorRepository.findBySaveId(1L))
                .thenReturn(List.of(gen(1L), gen(2L), gen(3L)));
        doThrow(new RuntimeException("not enough")).when(generatorService).buyBulk(1L, 1L, -1);

        autoBuyService.processSave(1L);

        verify(generatorService).buyBulk(1L, 1L, -1);
        verify(generatorService).buyBulk(1L, 2L, -1);
        verify(generatorService).buyBulk(1L, 3L, -1);
    }

    @Test
    @DisplayName("tickAutoBuy: пропускає save з autobuyEnabled=false")
    void tickAutoBuy_skipsDisabledSave() {
        Save disabled = instantiate(Save.class);
        ReflectionTestUtils.setField(disabled, "id", 99L);
        disabled.setAutobuyEnabled(false);

        when(saveRepository.findAll()).thenReturn(List.of(disabled));

        autoBuyService.tickAutoBuy();

        verifyNoInteractions(generatorService);
        // Repos для апгрейдів/генераторів навіть не запитуються.
        verifyNoInteractions(playerUpgradeRepository);
        verifyNoInteractions(playerGeneratorRepository);
    }

    @Test
    @DisplayName("tickAutoBuy: для save з autobuyEnabled=true викликає processSave")
    void tickAutoBuy_runsForEnabledSave() {
        Save enabled = instantiate(Save.class);
        ReflectionTestUtils.setField(enabled, "id", 7L);
        enabled.setAutobuyEnabled(true);

        when(saveRepository.findAll()).thenReturn(List.of(enabled));
        when(playerUpgradeRepository.findBySaveId(7L)).thenReturn(List.of(autobuy(1)));
        when(playerGeneratorRepository.findBySaveId(7L)).thenReturn(List.of(gen(1L)));

        autoBuyService.tickAutoBuy();

        verify(generatorService).buyBulk(eq(7L), eq(1L), eq(-1));
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
