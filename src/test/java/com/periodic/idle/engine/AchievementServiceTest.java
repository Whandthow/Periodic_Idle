package com.periodic.idle.engine;

import com.periodic.idle.content.Achievement;
import com.periodic.idle.content.AchievementRepository;
import com.periodic.idle.content.Element;
import com.periodic.idle.content.Resource;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock private AchievementRepository achievementRepository;
    @Mock private PlayerAchievementRepository playerAchievementRepository;
    @Mock private PlayerResourceRepository playerResourceRepository;
    @Mock private PlayerElementRepository playerElementRepository;
    @Mock private SaveRepository saveRepository;

    @InjectMocks
    private AchievementService achievementService;

    private Save save;
    private Resource energy;

    @BeforeEach
    void setUp() {
        save = instantiate(Save.class);
        ReflectionTestUtils.setField(save, "id", 1L);
        energy = makeResource(1L, "E");
    }

    @Test
    @DisplayName("checkAndUnlock: RESOURCE_LOG10 виконано -> зберігає PlayerAchievement")
    void checkAndUnlock_resourceLog10_satisfied_unlocks() {
        Achievement a = makeAchievement(10L, "RESOURCE_LOG10", energy, 6);
        PlayerResource pr = makePlayerResource(energy, 1.0, 6); // log10 = 6

        when(achievementRepository.findAll()).thenReturn(List.of(a));
        when(playerAchievementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(pr));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        achievementService.checkAndUnlock(1L);

        verify(playerAchievementRepository).save(argThat(pa ->
                pa.getAchievement() == a && pa.getSave() == save && pa.getUnlockedAt() != null));
    }

    @Test
    @DisplayName("checkAndUnlock: RESOURCE_LOG10 не виконано -> нічого не зберігає")
    void checkAndUnlock_resourceLog10_notSatisfied_noop() {
        Achievement a = makeAchievement(10L, "RESOURCE_LOG10", energy, 6);
        PlayerResource pr = makePlayerResource(energy, 1.0, 5); // log10 = 5 < 6

        when(achievementRepository.findAll()).thenReturn(List.of(a));
        when(playerAchievementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(pr));
        when(playerElementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        achievementService.checkAndUnlock(1L);

        verify(playerAchievementRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkAndUnlock: вже розблоковане досягнення пропускається (не перевіряється повторно)")
    void checkAndUnlock_alreadyUnlocked_skipped() {
        Achievement a = makeAchievement(10L, "RESOURCE_LOG10", energy, 6);
        PlayerAchievement existing = new PlayerAchievement();
        existing.setAchievement(a);

        when(achievementRepository.findAll()).thenReturn(List.of(a));
        when(playerAchievementRepository.findBySaveId(1L)).thenReturn(List.of(existing));

        achievementService.checkAndUnlock(1L);

        verify(playerAchievementRepository, never()).save(any());
        verifyNoInteractions(playerResourceRepository);
    }

    @Test
    @DisplayName("checkAndUnlock: MATTER_COLLAPSES читає save.matterCollapses")
    void checkAndUnlock_matterCollapses_satisfied() {
        save.setMatterCollapses(10L);
        Achievement a = makeAchievement(11L, "MATTER_COLLAPSES", null, 10);

        when(achievementRepository.findAll()).thenReturn(List.of(a));
        when(playerAchievementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerElementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        achievementService.checkAndUnlock(1L);

        verify(playerAchievementRepository).save(argThat(pa -> pa.getAchievement() == a));
    }

    @Test
    @DisplayName("checkAndUnlock: BROKEN_INFINITY читає save.brokenInfinity")
    void checkAndUnlock_brokenInfinity_satisfied() {
        save.setBrokenInfinity(true);
        Achievement a = makeAchievement(12L, "BROKEN_INFINITY", null, 0);

        when(achievementRepository.findAll()).thenReturn(List.of(a));
        when(playerAchievementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerElementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        achievementService.checkAndUnlock(1L);

        verify(playerAchievementRepository).save(argThat(pa -> pa.getAchievement() == a));
    }

    @Test
    @DisplayName("checkAndUnlock: ELEMENTS_SYNTHESIZED рахує лише елементи з count>0")
    void checkAndUnlock_elementsSynthesized_countsOnlyPositive() {
        Achievement a = makeAchievement(13L, "ELEMENTS_SYNTHESIZED", null, 2);

        PlayerElement synthesized1 = makePlayerElement(1, 3);
        PlayerElement synthesized2 = makePlayerElement(2, 1);
        PlayerElement notSynthesized = makePlayerElement(3, 0);

        when(achievementRepository.findAll()).thenReturn(List.of(a));
        when(playerAchievementRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerElementRepository.findBySaveId(1L))
                .thenReturn(List.of(synthesized1, synthesized2, notSynthesized));

        achievementService.checkAndUnlock(1L);

        verify(playerAchievementRepository).save(argThat(pa -> pa.getAchievement() == a));
    }

    @Test
    @DisplayName("checkAndUnlock: без досягнень у БД — нічого не падає, репозиторії save не викликаються")
    void checkAndUnlock_noAchievements_noop() {
        when(achievementRepository.findAll()).thenReturn(new ArrayList<>());

        assertDoesNotThrow(() -> achievementService.checkAndUnlock(1L));
        verifyNoInteractions(playerResourceRepository, saveRepository);
    }

    @Test
    @DisplayName("listWithStatus: позначає unlocked=true лише для розблокованих, з датою")
    void listWithStatus_marksUnlockedCorrectly() {
        Achievement unlocked = makeAchievement(20L, "RESOURCE_LOG10", energy, 2);
        Achievement locked = makeAchievement(21L, "RESOURCE_LOG10", energy, 100);

        PlayerAchievement pa = new PlayerAchievement();
        pa.setAchievement(unlocked);
        pa.setUnlockedAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));

        when(achievementRepository.findAll()).thenReturn(List.of(unlocked, locked));
        when(playerAchievementRepository.findBySaveId(1L)).thenReturn(List.of(pa));

        List<Map<String, Object>> result = achievementService.listWithStatus(1L);

        assertEquals(2, result.size());
        Map<String, Object> unlockedRow = result.stream()
                .filter(r -> "ach_20".equals(r.get("code")))
                .findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, unlockedRow.get("unlocked"));
        assertNotNull(unlockedRow.get("unlockedAt"));

        Map<String, Object> lockedRow = result.stream()
                .filter(r -> r.get("unlocked") == Boolean.FALSE)
                .findFirst().orElseThrow();
        assertNull(lockedRow.get("unlockedAt"));
    }

    private Achievement makeAchievement(Long id, String conditionType, Resource resource, double threshold) {
        Achievement a = instantiate(Achievement.class);
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(a, "code", "ach_" + id);
        ReflectionTestUtils.setField(a, "name", "Achievement " + id);
        ReflectionTestUtils.setField(a, "description", "desc");
        ReflectionTestUtils.setField(a, "conditionType", conditionType);
        ReflectionTestUtils.setField(a, "resource", resource);
        ReflectionTestUtils.setField(a, "threshold", threshold);
        return a;
    }

    private Resource makeResource(Long id, String code) {
        Resource r = instantiate(Resource.class);
        ReflectionTestUtils.setField(r, "id", id);
        ReflectionTestUtils.setField(r, "code", code);
        return r;
    }

    private PlayerResource makePlayerResource(Resource resource, double number, long exponent) {
        PlayerResource pr = instantiate(PlayerResource.class);
        pr.setResource(resource);
        pr.setNumber(number);
        pr.setExponent(exponent);
        return pr;
    }

    private PlayerElement makePlayerElement(int atomicNumber, long count) {
        Element el = instantiate(Element.class);
        ReflectionTestUtils.setField(el, "id", (long) atomicNumber);
        ReflectionTestUtils.setField(el, "atomicNumber", atomicNumber);
        PlayerElement pe = instantiate(PlayerElement.class);
        pe.setElement(el);
        pe.setCount(count);
        return pe;
    }

    @SuppressWarnings("unchecked")
    private <T> T instantiate(Class<T> clazz) {
        try {
            var c = clazz.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
