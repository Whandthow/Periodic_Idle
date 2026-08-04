package com.periodic.idle.engine;

import com.periodic.idle.content.Generator;
import com.periodic.idle.content.GeneratorRepository;
import com.periodic.idle.content.Resource;
import com.periodic.idle.content.ResourceRepository;
import com.periodic.idle.player.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaveServiceTest {

    @Mock private SaveRepository saveRepository;
    @Mock private ResourceRepository resourceRepository;
    @Mock private GeneratorRepository generatorRepository;
    @Mock private PlayerResourceRepository playerResourceRepository;
    @Mock private PlayerGeneratorRepository playerGeneratorRepository;

    @InjectMocks
    private SaveService saveService;

    private Resource energyRes;
    private Resource vcRes;
    private Generator gen1;

    @BeforeEach
    void setUp() {
        energyRes = instantiate(Resource.class);
        ReflectionTestUtils.setField(energyRes, "id", 1L);
        ReflectionTestUtils.setField(energyRes, "code", "E");

        vcRes = instantiate(Resource.class);
        ReflectionTestUtils.setField(vcRes, "id", 2L);
        ReflectionTestUtils.setField(vcRes, "code", "VC");

        gen1 = instantiate(Generator.class);
        ReflectionTestUtils.setField(gen1, "id", 1L);
        ReflectionTestUtils.setField(gen1, "code", "void_gen");
    }

    @Test
    @DisplayName("findOrCreateByToken: токен вже прив'язаний -> повертає існуючий save, нічого не створює")
    void findOrCreateByToken_existing_returnsIt() {
        Save save = new Save();
        ReflectionTestUtils.setField(save, "id", 5L);
        save.setClientToken("uuid-abc");
        when(saveRepository.findByClientToken("uuid-abc")).thenReturn(Optional.of(save));

        Save result = saveService.findOrCreateByToken("uuid-abc");

        assertEquals(5L, result.getId());
        verify(saveRepository, never()).save(any());
        verifyNoInteractions(resourceRepository, generatorRepository, playerResourceRepository, playerGeneratorRepository);
    }

    @Test
    @DisplayName("findOrCreateByToken: новий токен -> створює окремий save з власним нульовим станом")
    void findOrCreateByToken_newToken_createsIsolatedSave() {
        when(saveRepository.findByClientToken("uuid-new")).thenReturn(Optional.empty());
        when(saveRepository.save(any(Save.class))).thenAnswer(inv -> inv.getArgument(0));
        when(resourceRepository.findAll()).thenReturn(List.of(energyRes, vcRes));
        when(generatorRepository.findAll()).thenReturn(List.of(gen1));

        Save result = saveService.findOrCreateByToken("uuid-new");

        assertEquals("uuid-new", result.getClientToken());
        assertNotNull(result.getCreatedAt(), "createdAt має виставлятись одразу для нового save");

        ArgumentCaptor<PlayerResource> prCaptor = ArgumentCaptor.forClass(PlayerResource.class);
        verify(playerResourceRepository, times(2)).save(prCaptor.capture());
        List<PlayerResource> savedResources = prCaptor.getAllValues();

        PlayerResource energyPr = savedResources.stream()
                .filter(pr -> pr.getResource() == energyRes).findFirst().orElseThrow();
        assertEquals(PrestigeService.STARTER_ENERGY_NUMBER, energyPr.getNumber(), 1e-9);
        assertEquals(PrestigeService.STARTER_ENERGY_EXPONENT, energyPr.getExponent());
        assertSame(result, energyPr.getSave());

        PlayerResource vcPr = savedResources.stream()
                .filter(pr -> pr.getResource() == vcRes).findFirst().orElseThrow();
        assertEquals(0, vcPr.getNumber(), 1e-9);
        assertEquals(0, vcPr.getExponent());

        ArgumentCaptor<PlayerGenerator> pgCaptor = ArgumentCaptor.forClass(PlayerGenerator.class);
        verify(playerGeneratorRepository).save(pgCaptor.capture());
        assertEquals(1, pgCaptor.getValue().getLevel());
        assertSame(gen1, pgCaptor.getValue().getGenerator());
        assertSame(result, pgCaptor.getValue().getSave());
    }

    @Test
    @DisplayName("findOrCreateByToken: два різні токени отримують окремі saves (ізоляція між браузерами)")
    void findOrCreateByToken_differentTokens_areIsolated() {
        when(saveRepository.findByClientToken(anyString())).thenReturn(Optional.empty());
        when(saveRepository.save(any(Save.class))).thenAnswer(inv -> {
            Save s = inv.getArgument(0);
            return s;
        });
        when(resourceRepository.findAll()).thenReturn(List.of(energyRes));
        when(generatorRepository.findAll()).thenReturn(List.of(gen1));

        Save first = saveService.findOrCreateByToken("token-A");
        Save second = saveService.findOrCreateByToken("token-B");

        assertNotSame(first, second);
        assertEquals("token-A", first.getClientToken());
        assertEquals("token-B", second.getClientToken());
    }

    @Test
    @DisplayName("findOrCreateByToken: порожній token -> IllegalArgumentException")
    void findOrCreateByToken_blankToken_throws() {
        assertThrows(IllegalArgumentException.class, () -> saveService.findOrCreateByToken(""));
        assertThrows(IllegalArgumentException.class, () -> saveService.findOrCreateByToken(null));
        verifyNoInteractions(saveRepository);
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
