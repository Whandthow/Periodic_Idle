package com.periodic.idle.engine;

import com.periodic.idle.content.Upgrade;
import com.periodic.idle.content.UpgradeRepository;
import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoUpgradeServiceTest {

    @Mock private SaveRepository saveRepository;
    @Mock private UpgradeRepository upgradeRepository;
    @Mock private UpgradeService upgradeService;

    @InjectMocks
    private AutoUpgradeService autoUpgradeService;

    private Upgrade upgrade(Long id) {
        Upgrade u = instantiate(Upgrade.class);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    @DisplayName("processSave: намагається купити max кожного апгрейду в грі")
    void processSave_buysEveryUpgradeMax() {
        when(upgradeRepository.findAll()).thenReturn(List.of(upgrade(1L), upgrade(2L), upgrade(3L)));

        autoUpgradeService.processSave(1L);

        verify(upgradeService).buyBulk(eq(1L), eq(1L), eq(-1));
        verify(upgradeService).buyBulk(eq(1L), eq(2L), eq(-1));
        verify(upgradeService).buyBulk(eq(1L), eq(3L), eq(-1));
    }

    @Test
    @DisplayName("processSave: якщо buyBulk кидає — наступні апгрейди все одно виконуються")
    void processSave_continuesOnError() {
        when(upgradeRepository.findAll()).thenReturn(List.of(upgrade(1L), upgrade(2L)));
        doThrow(new RuntimeException("Not enough resources")).when(upgradeService).buyBulk(1L, 1L, -1);

        autoUpgradeService.processSave(1L);

        verify(upgradeService).buyBulk(1L, 1L, -1);
        verify(upgradeService).buyBulk(1L, 2L, -1);
    }

    @Test
    @DisplayName("tickAutoUpgrade: пропускає save з autoUpgradeEnabled=false")
    void tickAutoUpgrade_skipsDisabledSave() {
        Save disabled = instantiate(Save.class);
        ReflectionTestUtils.setField(disabled, "id", 99L);
        disabled.setAutoUpgradeEnabled(false);
        disabled.setMatterCollapses(AutoUpgradeService.AUTO_UPGRADE_UNLOCK_COLLAPSES);

        when(saveRepository.findAll()).thenReturn(List.of(disabled));

        autoUpgradeService.tickAutoUpgrade();

        verifyNoInteractions(upgradeService);
        verifyNoInteractions(upgradeRepository);
    }

    @Test
    @DisplayName("tickAutoUpgrade: пропускає save, що ще не досяг AUTO_UPGRADE_UNLOCK_COLLAPSES")
    void tickAutoUpgrade_skipsSaveBelowUnlockThreshold() {
        Save notYetUnlocked = instantiate(Save.class);
        ReflectionTestUtils.setField(notYetUnlocked, "id", 5L);
        notYetUnlocked.setAutoUpgradeEnabled(true);
        notYetUnlocked.setMatterCollapses(AutoUpgradeService.AUTO_UPGRADE_UNLOCK_COLLAPSES - 1);

        when(saveRepository.findAll()).thenReturn(List.of(notYetUnlocked));

        autoUpgradeService.tickAutoUpgrade();

        verifyNoInteractions(upgradeService);
        verifyNoInteractions(upgradeRepository);
    }

    @Test
    @DisplayName("tickAutoUpgrade: увімкнено і поріг досягнуто — викликає processSave")
    void tickAutoUpgrade_runsForEnabledAndUnlockedSave() {
        Save enabled = instantiate(Save.class);
        ReflectionTestUtils.setField(enabled, "id", 7L);
        enabled.setAutoUpgradeEnabled(true);
        enabled.setMatterCollapses(AutoUpgradeService.AUTO_UPGRADE_UNLOCK_COLLAPSES);

        when(saveRepository.findAll()).thenReturn(List.of(enabled));
        when(upgradeRepository.findAll()).thenReturn(List.of(upgrade(1L)));

        autoUpgradeService.tickAutoUpgrade();

        verify(upgradeService).buyBulk(eq(7L), eq(1L), eq(-1));
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
