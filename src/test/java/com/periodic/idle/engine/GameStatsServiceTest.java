package com.periodic.idle.engine;

import com.periodic.idle.content.Generator;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameStatsServiceTest {

    @Mock private SaveRepository saveRepository;
    @Mock private PlayerResourceRepository playerResourceRepository;
    @Mock private PlayerGeneratorRepository playerGeneratorRepository;
    @Mock private PlayerUpgradeRepository playerUpgradeRepository;
    @Mock private PlayerElementRepository playerElementRepository;
    @Mock private PlayerMoleculeRepository playerMoleculeRepository;
    @Mock private PlayerStarRepository playerStarRepository;
    @Mock private PlayerAchievementRepository playerAchievementRepository;
    @Mock private com.periodic.idle.content.ElementRepository elementRepository;
    @Mock private com.periodic.idle.content.MoleculeRepository moleculeRepository;
    @Mock private com.periodic.idle.content.StarRepository starRepository;
    @Mock private com.periodic.idle.content.AchievementRepository achievementRepository;
    @Mock private GameEngine gameEngine;

    @InjectMocks
    private GameStatsService statsService;

    private Save save;
    private Resource energy;
    private Generator voidGen;
    private PlayerResource playerEnergy;
    private PlayerGenerator playerVoidGen;

    @BeforeEach
    void setUp() {
        save = instantiate(Save.class);
        ReflectionTestUtils.setField(save, "id", 1L);
        save.setPlayerName("dev");

        energy = createResource(1L, "E", "Енергія", 0);

        voidGen = instantiate(Generator.class);
        ReflectionTestUtils.setField(voidGen, "id", 1L);
        ReflectionTestUtils.setField(voidGen, "code", "void_gen");
        ReflectionTestUtils.setField(voidGen, "name", "Генератор пустоти");

        playerEnergy = instantiate(PlayerResource.class);
        ReflectionTestUtils.setField(playerEnergy, "id", 1L);
        playerEnergy.setSave(save);
        playerEnergy.setResource(energy);
        playerEnergy.setNumber(0);
        playerEnergy.setExponent(0);

        playerVoidGen = instantiate(PlayerGenerator.class);
        ReflectionTestUtils.setField(playerVoidGen, "id", 1L);
        playerVoidGen.setSave(save);
        playerVoidGen.setGenerator(voidGen);
        playerVoidGen.setLevel(1);

        lenient().when(playerElementRepository.findBySaveId(anyLong())).thenReturn(new ArrayList<>());
        lenient().when(gameEngine.calculateGeneratorBreakdown(1L))
                .thenReturn(Map.of(1L, new GameEngine.GenBreakdown(0.5, 0.0)));
        lenient().when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
    }

    @Test
    @DisplayName("calculateStats: повертає список множників і per-generator розбивку")
    void calculateStats_basicShape() {
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        Map<String, Object> stats = statsService.calculateStats(1L);

        assertNotNull(stats.get("multipliers"));
        assertNotNull(stats.get("generators"));
        assertNotNull(stats.get("totalEnergyPerSec"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mults = (List<Map<String, Object>>) stats.get("multipliers");
        // Очікуємо щонайменше 7 рядків (energyMult, genMult, core, p, n, e, energyPow).
        assertTrue(mults.size() >= 7, "повинно бути ≥7 множників, отримали " + mults.size());
        assertTrue(mults.stream().anyMatch(m -> "Протони → енергія".equals(m.get("name"))));
        assertTrue(mults.stream().anyMatch(m -> "Нейтрони → ціна".equals(m.get("name"))));
        assertTrue(mults.stream().anyMatch(m -> "Електрони → VC".equals(m.get("name"))));
    }

    @Test
    @DisplayName("calculateStats: рівень рядка для протонів = кількості протонів")
    void calculateStats_particleLevelEqualsCount() {
        Resource pRes = createResource(3L, "p", "Протон", 1);
        PlayerResource pp = instantiate(PlayerResource.class);
        pp.setResource(pRes);
        pp.setNumber(7.0);
        pp.setExponent(0L);

        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy, pp));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        Map<String, Object> stats = statsService.calculateStats(1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mults = (List<Map<String, Object>>) stats.get("multipliers");

        Map<String, Object> protonRow = mults.stream()
                .filter(m -> "Протони → енергія".equals(m.get("name")))
                .findFirst().orElseThrow();
        assertEquals(7, ((Number) protonRow.get("level")).intValue());
        // saturating(7) = 7/(1+7/1000) = 6.95134...; value = 1 + 0.25 * 6.95134... = 2.73784...
        assertEquals(2.7378351539225423, ((Number) protonRow.get("value")).doubleValue(), 1e-6);
    }

    @Test
    @DisplayName("calculateStats: секція lifetime — playtime, prestigeCount і прогрес по вмісту")
    void calculateStats_lifetimeSection() {
        save.setCreatedAt(LocalDateTime.now().minusSeconds(3600));
        save.setPrestigeCount(3L);
        save.setMatterCollapses(5L);
        save.setHypernovaCount(2L);
        save.setBrokenInfinity(true);

        PlayerMolecule collectedMolecule = instantiate(PlayerMolecule.class);
        collectedMolecule.setCount(4L);
        PlayerMolecule emptyMolecule = instantiate(PlayerMolecule.class);
        emptyMolecule.setCount(0L);

        PlayerStar ignitedStar = instantiate(PlayerStar.class);
        ignitedStar.setLevel(2);

        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerMoleculeRepository.findBySaveId(1L)).thenReturn(List.of(collectedMolecule, emptyMolecule));
        when(playerStarRepository.findBySaveId(1L)).thenReturn(List.of(ignitedStar));
        when(playerAchievementRepository.findBySaveId(1L)).thenReturn(List.of(instantiate(PlayerAchievement.class)));
        when(elementRepository.count()).thenReturn(36L);
        when(moleculeRepository.count()).thenReturn(10L);
        when(starRepository.count()).thenReturn(1L);
        when(achievementRepository.count()).thenReturn(15L);

        Map<String, Object> stats = statsService.calculateStats(1L);
        @SuppressWarnings("unchecked")
        Map<String, Object> lifetime = (Map<String, Object>) stats.get("lifetime");

        assertNotNull(lifetime);
        assertTrue(((Number) lifetime.get("playtimeSeconds")).doubleValue() >= 3599);
        assertEquals(3L, ((Number) lifetime.get("prestigeCount")).longValue());
        assertEquals(5L, ((Number) lifetime.get("matterCollapses")).longValue());
        assertEquals(2L, ((Number) lifetime.get("hypernovaCount")).longValue());
        assertEquals(true, lifetime.get("brokenInfinity"));
        assertEquals(1L, ((Number) lifetime.get("distinctMolecules")).longValue());
        assertEquals(10L, ((Number) lifetime.get("totalMolecules")).longValue());
        assertEquals(1L, ((Number) lifetime.get("starsIgnited")).longValue());
        assertEquals(1L, ((Number) lifetime.get("totalStars")).longValue());
        assertEquals(1L, ((Number) lifetime.get("achievementsUnlocked")).longValue());
        assertEquals(15L, ((Number) lifetime.get("totalAchievements")).longValue());
        assertEquals(36L, ((Number) lifetime.get("totalElements")).longValue());
    }

    private Resource createResource(Long id, String code, String name, int tier) {
        Resource r = instantiate(Resource.class);
        ReflectionTestUtils.setField(r, "id", id);
        ReflectionTestUtils.setField(r, "code", code);
        ReflectionTestUtils.setField(r, "name", name);
        ReflectionTestUtils.setField(r, "tier", tier);
        return r;
    }

    @SuppressWarnings("unchecked")
    private <T> T instantiate(Class<T> clazz) {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + clazz.getName(), e);
        }
    }
}
