package com.periodic.idle.engine;

import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaveServiceTest {

    @Mock private SaveRepository saveRepository;

    @InjectMocks
    private SaveService saveService;

    @Test
    @DisplayName("findOrCreateByToken: токен вже прив'язаний -> повертає існуючий save")
    void findOrCreateByToken_existing_returnsIt() {
        Save save = new Save();
        ReflectionTestUtils.setField(save, "id", 5L);
        save.setClientToken("uuid-abc");
        when(saveRepository.findByClientToken("uuid-abc")).thenReturn(Optional.of(save));

        Save result = saveService.findOrCreateByToken("uuid-abc");

        assertEquals(5L, result.getId());
        verify(saveRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("findOrCreateByToken: новий токен, save 1 ще без токена -> прив'язує його")
    void findOrCreateByToken_newToken_bindsToDefaultSave() {
        Save defaultSave = new Save();
        ReflectionTestUtils.setField(defaultSave, "id", 1L);
        when(saveRepository.findByClientToken("uuid-new")).thenReturn(Optional.empty());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(defaultSave));
        when(saveRepository.save(defaultSave)).thenReturn(defaultSave);

        Save result = saveService.findOrCreateByToken("uuid-new");

        assertEquals("uuid-new", result.getClientToken());
        verify(saveRepository).save(defaultSave);
    }

    @Test
    @DisplayName("findOrCreateByToken: новий токен, save 1 вже має свій токен -> все одно повертає save 1")
    void findOrCreateByToken_defaultAlreadyBound_returnsAnyway() {
        Save defaultSave = new Save();
        ReflectionTestUtils.setField(defaultSave, "id", 1L);
        defaultSave.setClientToken("uuid-other");
        when(saveRepository.findByClientToken("uuid-new")).thenReturn(Optional.empty());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(defaultSave));

        Save result = saveService.findOrCreateByToken("uuid-new");

        assertEquals(1L, result.getId());
        assertEquals("uuid-other", result.getClientToken());
        verify(saveRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreateByToken: порожній token -> IllegalArgumentException")
    void findOrCreateByToken_blankToken_throws() {
        assertThrows(IllegalArgumentException.class, () -> saveService.findOrCreateByToken(""));
        assertThrows(IllegalArgumentException.class, () -> saveService.findOrCreateByToken(null));
        verifyNoInteractions(saveRepository);
    }
}
