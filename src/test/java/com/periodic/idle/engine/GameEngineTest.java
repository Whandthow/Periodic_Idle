package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.Generator;
import com.periodic.idle.content.GeneratorOutput;
import com.periodic.idle.content.Resource;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameEngineTest {

    @Mock
    private SaveRepository saveRepository;
    @Mock
    private PlayerResourceRepository playerResourceRepository;
    @Mock
    private PlayerGeneratorRepository playerGeneratorRepository;
    @Mock
    private PlayerUpgradeRepository playerUpgradeRepository;

    @InjectMocks
    private GameEngine gameEngine;

    private Save save;
    private Resource energy;
    private Generator voidGen;
    private GeneratorOutput voidGenOutput;
    private PlayerResource playerEnergy;
    private PlayerGenerator playerVoidGen;

    @BeforeEach
    void setUp() {
        save = instantiate(Save.class);
        ReflectionTestUtils.setField(save, "id", 1L);
        save.setPlayerName("dev");
        save.setLastTick(LocalDateTime.now().minusSeconds(5));

        energy = createResource(1L, "E", "Енергія", 0);

        // Генератор пустоти: виробляє 0.5 E за рівень
        voidGen = instantiate(Generator.class);
        ReflectionTestUtils.setField(voidGen, "id", 1L);
        ReflectionTestUtils.setField(voidGen, "code", "void_gen");

        voidGenOutput = instantiate(GeneratorOutput.class);
        ReflectionTestUtils.setField(voidGenOutput, "id", 1L);
        ReflectionTestUtils.setField(voidGenOutput, "generator", voidGen);
        ReflectionTestUtils.setField(voidGenOutput, "resource", energy);
        ReflectionTestUtils.setField(voidGenOutput, "ratePerLevel", 0.5);

        ReflectionTestUtils.setField(voidGen, "outputs", List.of(voidGenOutput));

        // Гравець: 0 енергії, генератор рівня 1
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
    }

    // === tick() ===

    @Test
    @DisplayName("tick обробляє всі saves і оновлює lastTick")
    void tick_updatesLastTick() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        gameEngine.tick();

        assertNotNull(save.getLastTick());
        assertTrue(save.getLastTick().isAfter(before));
    }

    @Test
    @DisplayName("tick без saves — нічого не падає")
    void tick_noSaves_noop() {
        when(saveRepository.findAll()).thenReturn(new ArrayList<>());

        assertDoesNotThrow(() -> gameEngine.tick());
    }

    // === processSave — базова генерація ===

    @Test
    @DisplayName("Генератор level 1, rate 0.5 → додає 0.5 енергії за тік")
    void processSave_basicGeneration() {
        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        gameEngine.tick();

        // rate/sec = 0.5, tick adds rate * 0.1 = 0.05
        BigNum expected = new BigNum(0.05, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
        assertEquals(expected.getExponent(), playerEnergy.getExponent());
    }

    @Test
    @DisplayName("Генератор level 5 → додає 2.5 енергії за тік")
    void processSave_higherLevel() {
        playerVoidGen.setLevel(5);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        gameEngine.tick();

        // rate/sec = 0.5 * 5 = 2.5, tick = 0.25
        BigNum expected = new BigNum(0.25, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
        assertEquals(expected.getExponent(), playerEnergy.getExponent());
    }

    @Test
    @DisplayName("Генератор level 0 — пропускається, ресурс не змінюється")
    void processSave_levelZero_skipped() {
        playerVoidGen.setLevel(0);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        gameEngine.tick();

        assertEquals(0, playerEnergy.getNumber(), 0.001);
        assertEquals(0, playerEnergy.getExponent());
    }

    @Test
    @DisplayName("Ресурс накопичується між тіками")
    void processSave_accumulates() {
        // Починаємо з 1.0e3 = 1000 енергії
        playerEnergy.setNumber(1.0);
        playerEnergy.setExponent(3);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        gameEngine.tick();

        // 1000 + 0.05 → ~1.00005e3
        BigNum expected = new BigNum(1.0, 3).add(new BigNum(0.05, 0));
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
        assertEquals(expected.getExponent(), playerEnergy.getExponent());
    }

    // === Множники від апгрейдів ===

    @Test
    @DisplayName("ENERGY_MULT апгрейд level 2, effectValue 0.5 → множник 2.0")
    void processSave_energyMultUpgrade() {
        PlayerUpgrade energyUpgrade = createPlayerUpgrade(save,
                createUpgradeContent(1L, "ENERGY_MULT", 0.5), 2);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(energyUpgrade));

        gameEngine.tick();

        // rate/sec = 0.5 * 1 * 1.0 * 2.0 = 1.0, tick = 0.1
        BigNum expected = new BigNum(0.1, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
        assertEquals(expected.getExponent(), playerEnergy.getExponent());
    }

    @Test
    @DisplayName("GENERATOR_MULT апгрейд level 3, effectValue 0.3 → множник 1.9")
    void processSave_generatorMultUpgrade() {
        PlayerUpgrade genUpgrade = createPlayerUpgrade(save,
                createUpgradeContent(2L, "GENERATOR_MULT", 0.3), 3);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(genUpgrade));

        gameEngine.tick();

        // rate/sec = 0.5 * 1 * 1.9 * 1.0 = 0.95, tick = 0.095
        BigNum expected = new BigNum(0.095, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
        assertEquals(expected.getExponent(), playerEnergy.getExponent());
    }

    @Test
    @DisplayName("Обидва множники одночасно множать rate")
    void processSave_bothMultipliers() {
        PlayerUpgrade energyUp = createPlayerUpgrade(save,
                createUpgradeContent(1L, "ENERGY_MULT", 0.5), 2); // mult = 2.0
        PlayerUpgrade genUp = createPlayerUpgrade(save,
                createUpgradeContent(2L, "GENERATOR_MULT", 1.0), 1); // mult = 2.0

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(energyUp, genUp));

        gameEngine.tick();

        // rate/sec = 0.5 * 1 * 2.0 * 2.0 = 2.0, tick = 0.2
        BigNum expected = new BigNum(0.2, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
        assertEquals(expected.getExponent(), playerEnergy.getExponent());
    }

    @Test
    @DisplayName("Апгрейд з level 0 не впливає на множник")
    void processSave_upgradeLevel0_ignored() {
        PlayerUpgrade zeroUpgrade = createPlayerUpgrade(save,
                createUpgradeContent(1L, "ENERGY_MULT", 5.0), 0);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(zeroUpgrade));

        gameEngine.tick();

        // Множник = 1.0 (апгрейд з level 0 ігнорується), tick = 0.05
        BigNum expected = new BigNum(0.05, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    @Test
    @DisplayName("COST_DISCOUNT апгрейд не впливає на genMult і energyMult")
    void processSave_irrelevantUpgradeType_ignored() {
        PlayerUpgrade costUpgrade = createPlayerUpgrade(save,
                createUpgradeContent(3L, "COST_DISCOUNT", 0.1), 5);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(costUpgrade));

        gameEngine.tick();

        // Множники обидва 1.0, tick = 0.05
        BigNum expected = new BigNum(0.05, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    // === PHANTOM_GEN ===

    @Test
    @DisplayName("PHANTOM_GEN T1 подвоює енергію для ген1 і ген2 (bonus=1)")
    void processSave_phantomTier1() {
        Generator gen2 = instantiate(Generator.class);
        ReflectionTestUtils.setField(gen2, "id", 2L);
        GeneratorOutput gen2Out = instantiate(GeneratorOutput.class);
        ReflectionTestUtils.setField(gen2Out, "generator", gen2);
        ReflectionTestUtils.setField(gen2Out, "resource", energy);
        ReflectionTestUtils.setField(gen2Out, "ratePerLevel", 2.0);
        ReflectionTestUtils.setField(gen2, "outputs", List.of(gen2Out));

        PlayerGenerator playerGen2 = instantiate(PlayerGenerator.class);
        playerGen2.setSave(save);
        playerGen2.setGenerator(gen2);
        playerGen2.setLevel(1);

        PlayerUpgrade up = createPlayerUpgrade(save,
                createUpgradeContent(60L, "PHANTOM_GEN", 1.0), 1);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L))
                .thenReturn(List.of(playerVoidGen, playerGen2));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(up));

        gameEngine.tick();

        // gen1: 0.5 * (1+1) = 1.0/sec; gen2: 2.0 * (1+1) = 4.0/sec; total = 5.0/sec; tick = 0.5
        BigNum expected = new BigNum(0.5, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    @Test
    @DisplayName("PHANTOM_GEN T3: bonus=2 (T-2=1, max(1,1)=1)... fix: T3→max(1,1)=1")
    void processSave_phantomTier3() {
        // T=3 → bonus = max(1, 3-2) = 1 (тобто T3 ще не прискорює; T4=2, T5=3)
        PlayerUpgrade up = createPlayerUpgrade(save,
                createUpgradeContent(60L, "PHANTOM_GEN", 1.0), 4);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(up));

        gameEngine.tick();

        // T4 → bonus = max(1, 4-2) = 2; rate = 0.5 * (1+2) = 1.5/sec; tick = 0.15
        BigNum expected = new BigNum(0.15, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    // === GEN_STACK ===

    @Test
    @DisplayName("GEN_STACK T1 робить ген1 level-квадратичним (level^2)")
    void processSave_genStackQuadratic() {
        playerVoidGen.setLevel(5);

        PlayerUpgrade up = createPlayerUpgrade(save,
                createUpgradeContent(50L, "GEN_STACK", 1.0), 1);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(up));

        gameEngine.tick();

        // rate/sec = 0.5 * 5 (level) * 5 (stack = level) = 12.5; tick = 1.25
        BigNum expected = new BigNum(1.25, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    @Test
    @DisplayName("GEN_STACK T1 не впливає на ген2 (поза межами upLevel)")
    void processSave_genStackOnlyFirst() {
        Generator gen2 = instantiate(Generator.class);
        ReflectionTestUtils.setField(gen2, "id", 2L);
        GeneratorOutput gen2Out = instantiate(GeneratorOutput.class);
        ReflectionTestUtils.setField(gen2Out, "generator", gen2);
        ReflectionTestUtils.setField(gen2Out, "resource", energy);
        ReflectionTestUtils.setField(gen2Out, "ratePerLevel", 1.0);
        ReflectionTestUtils.setField(gen2, "outputs", List.of(gen2Out));

        PlayerGenerator playerGen2 = instantiate(PlayerGenerator.class);
        playerGen2.setSave(save);
        playerGen2.setGenerator(gen2);
        playerGen2.setLevel(3);

        PlayerUpgrade up = createPlayerUpgrade(save,
                createUpgradeContent(50L, "GEN_STACK", 1.0), 1);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L))
                .thenReturn(List.of(playerVoidGen, playerGen2));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(up));

        gameEngine.tick();

        // gen1 (level 1, stack 1): 0.5/sec; gen2 (level 3, stack 1.0): 3/sec; total 3.5/sec; tick 0.35
        BigNum expected = new BigNum(0.35, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    // === ENERGY_POW ===

    @Test
    @DisplayName("ENERGY_POW: level 10 з coeff 0.1 → pow=2.0; base 100 → 100^2 = 10000")
    void processSave_energyPow() {
        // Для великої бази піднесення в степінь дає помітний ефект.
        // Встановимо ген level 200 → base rate = 0.5 * 200 = 100.
        playerVoidGen.setLevel(200);

        PlayerUpgrade powUp = createPlayerUpgrade(save,
                createUpgradeContent(40L, "ENERGY_POW", 0.1), 10);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(powUp));

        gameEngine.tick();

        // rate/sec = 100^2 = 10000; tick = 10000 * 0.1 = 1000
        double actual = playerEnergy.getNumber() * Math.pow(10, playerEnergy.getExponent());
        assertEquals(1000.0, actual, 1.0);
    }

    @Test
    @DisplayName("ENERGY_POW: rate < 1 не підноситься у степінь (pow-gate за ratePerSec>1)")
    void processSave_energyPow_ignoredBelowOne() {
        PlayerUpgrade powUp = createPlayerUpgrade(save,
                createUpgradeContent(40L, "ENERGY_POW", 0.1), 10);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(powUp));

        gameEngine.tick();

        // base rate=0.5 < 1 → pow не застосовується; tick = 0.05
        BigNum expected = new BigNum(0.05, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    // === GEN_SPECIFIC_MULT ===

    @Test
    @DisplayName("GEN_SPECIFIC_MULT T1 бустить лише ген1 (pos 1), ген2 без змін")
    void processSave_genSpecificTier1() {
        Generator gen2 = instantiate(Generator.class);
        ReflectionTestUtils.setField(gen2, "id", 2L);
        ReflectionTestUtils.setField(gen2, "code", "quantum_loop");
        GeneratorOutput gen2Out = instantiate(GeneratorOutput.class);
        ReflectionTestUtils.setField(gen2Out, "generator", gen2);
        ReflectionTestUtils.setField(gen2Out, "resource", energy);
        ReflectionTestUtils.setField(gen2Out, "ratePerLevel", 2.0);
        ReflectionTestUtils.setField(gen2, "outputs", List.of(gen2Out));

        PlayerGenerator playerGen2 = instantiate(PlayerGenerator.class);
        ReflectionTestUtils.setField(playerGen2, "id", 2L);
        playerGen2.setSave(save);
        playerGen2.setGenerator(gen2);
        playerGen2.setLevel(1);

        PlayerUpgrade up = createPlayerUpgrade(save,
                createUpgradeContent(30L, "GEN_SPECIFIC_MULT", 0.3), 1);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L))
                .thenReturn(List.of(playerVoidGen, playerGen2));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(up));

        gameEngine.tick();

        // gen1: 0.5 * 1 * (1 + 1*0.3) = 0.65/sec; gen2: 2.0 * 1 * 1.0 = 2.0/sec
        // total = 2.65/sec, tick = 0.265
        BigNum expected = new BigNum(0.265, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    @Test
    @DisplayName("GEN_SPECIFIC_MULT T3 бустить ген1 сильніше, ніж ген2")
    void processSave_genSpecificTier3_stacking() {
        Generator gen2 = instantiate(Generator.class);
        ReflectionTestUtils.setField(gen2, "id", 2L);
        GeneratorOutput gen2Out = instantiate(GeneratorOutput.class);
        ReflectionTestUtils.setField(gen2Out, "generator", gen2);
        ReflectionTestUtils.setField(gen2Out, "resource", energy);
        ReflectionTestUtils.setField(gen2Out, "ratePerLevel", 2.0);
        ReflectionTestUtils.setField(gen2, "outputs", List.of(gen2Out));

        PlayerGenerator playerGen2 = instantiate(PlayerGenerator.class);
        playerGen2.setSave(save);
        playerGen2.setGenerator(gen2);
        playerGen2.setLevel(1);

        PlayerUpgrade up = createPlayerUpgrade(save,
                createUpgradeContent(30L, "GEN_SPECIFIC_MULT", 0.3), 3);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L))
                .thenReturn(List.of(playerVoidGen, playerGen2));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(up));

        gameEngine.tick();

        // gen1 (pos 1, level 3): mult = 1 + 3*0.3 = 1.9 → 0.5 * 1.9 = 0.95/sec
        // gen2 (pos 2, level 3): mult = 1 + 2*0.3 = 1.6 → 2.0 * 1.6 = 3.2/sec
        // total = 4.15/sec, tick = 0.415
        BigNum expected = new BigNum(0.415, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    // === Softcap / tickSpeed / Core boost ===

    @Test
    @DisplayName("ENERGY_MULT softcap: level 24 дає effective 22 (20 + sqrt(4)), множник 12")
    void processSave_energyMultSoftcap() {
        PlayerUpgrade energyUpgrade = createPlayerUpgrade(save,
                createUpgradeContent(10L, "ENERGY_MULT", 0.5), 24);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(energyUpgrade));

        gameEngine.tick();

        // level 24 > 20 -> effective = 20 + sqrt(4) = 22; mult = 1 + 0.5 * 22 = 12
        // rate/sec = 0.5 * 1 * 1.0 * 12.0 = 6.0; tick = 0.6
        BigNum expected = new BigNum(0.6, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
        assertEquals(expected.getExponent(), playerEnergy.getExponent());
    }

    @Test
    @DisplayName("tickSpeedMultiplier = 3.0 потрійкує приріст за тік")
    void tick_speedMultiplier() {
        gameEngine.setTickSpeedMultiplier(3.0);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        gameEngine.tick();

        // rate = 0.5, tick = 0.5 * 0.1 * 3 = 0.15
        BigNum expected = new BigNum(0.15, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
        assertEquals(expected.getExponent(), playerEnergy.getExponent());
    }

    @Test
    @DisplayName("Ядро + 100 кристалів: coreLevel 5, coeff 0.1 -> boost 10x")
    void processSave_coreCrystalBoost() {
        Resource crystals = createResource(2L, "VC", "Кристал пустоти", 0);
        PlayerResource playerCrystals = instantiate(PlayerResource.class);
        ReflectionTestUtils.setField(playerCrystals, "id", 2L);
        playerCrystals.setSave(save);
        playerCrystals.setResource(crystals);
        playerCrystals.setNumber(1.0);
        playerCrystals.setExponent(2); // 1e2 = 100

        PlayerUpgrade coreUpgrade = createPlayerUpgrade(save,
                createUpgradeContent(20L, "CORE", 0.1), 5);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L))
                .thenReturn(List.of(playerEnergy, playerCrystals));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(coreUpgrade));

        gameEngine.tick();

        // log10(100) = 2; logBoost = 5 * 0.1 * 2 = 1; boost = 10
        // rate/sec = 0.5 * 1 * 1 * 1 * 10 = 5; tick = 0.5
        BigNum expected = new BigNum(0.5, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.01);
        assertEquals(expected.getExponent(), playerEnergy.getExponent());
    }

    @Test
    @DisplayName("Ядро без кристалів не дає буст (boost=1)")
    void processSave_coreWithoutCrystals() {
        PlayerUpgrade coreUpgrade = createPlayerUpgrade(save,
                createUpgradeContent(20L, "CORE", 0.1), 5);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(coreUpgrade));

        gameEngine.tick();

        // Без кристалів boost=1.0; tick = 0.05
        BigNum expected = new BigNum(0.05, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    // === calculateGeneratorBreakdown ===

    @Test
    @DisplayName("breakdown: один ген level 1 → energyPerSec=0.5, phantom=0")
    void breakdown_singleGen() {
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        Map<Long, GameEngine.GenBreakdown> result = gameEngine.calculateGeneratorBreakdown(1L);

        assertEquals(1, result.size());
        GameEngine.GenBreakdown br = result.get(1L);
        assertNotNull(br);
        assertEquals(0.5, br.energyPerSec(), 0.001);
        assertEquals(0.0, br.phantomBonus(), 0.001);
    }

    @Test
    @DisplayName("breakdown: ген з рівнем 0 — energyPerSec=0, але запис присутній")
    void breakdown_levelZero() {
        playerVoidGen.setLevel(0);
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        Map<Long, GameEngine.GenBreakdown> result = gameEngine.calculateGeneratorBreakdown(1L);

        assertEquals(0.0, result.get(1L).energyPerSec(), 0.001);
    }

    @Test
    @DisplayName("breakdown: phantom bonus відображається у полі")
    void breakdown_phantomBonus() {
        PlayerUpgrade up = createPlayerUpgrade(save,
                createUpgradeContent(60L, "PHANTOM_GEN", 1.0), 4);

        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(up));

        Map<Long, GameEngine.GenBreakdown> result = gameEngine.calculateGeneratorBreakdown(1L);

        // T=4 -> bonus = max(1, 4-2) = 2
        assertEquals(2.0, result.get(1L).phantomBonus(), 0.001);
        // rate = 0.5 * (1+2) = 1.5
        assertEquals(1.5, result.get(1L).energyPerSec(), 0.001);
    }

    @Test
    @DisplayName("breakdown: сума energyPerSec усіх генів = загальне виробництво")
    void breakdown_sumMatchesProduction() {
        Generator gen2 = instantiate(Generator.class);
        ReflectionTestUtils.setField(gen2, "id", 2L);
        GeneratorOutput gen2Out = instantiate(GeneratorOutput.class);
        ReflectionTestUtils.setField(gen2Out, "generator", gen2);
        ReflectionTestUtils.setField(gen2Out, "resource", energy);
        ReflectionTestUtils.setField(gen2Out, "ratePerLevel", 3.0);
        ReflectionTestUtils.setField(gen2, "outputs", List.of(gen2Out));

        PlayerGenerator playerGen2 = instantiate(PlayerGenerator.class);
        playerGen2.setSave(save);
        playerGen2.setGenerator(gen2);
        playerGen2.setLevel(2);

        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L))
                .thenReturn(List.of(playerVoidGen, playerGen2));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());

        Map<Long, GameEngine.GenBreakdown> breakdown = gameEngine.calculateGeneratorBreakdown(1L);
        Map<Long, Double> production = gameEngine.calculateProductionPerSec(1L);

        double breakdownSum = breakdown.values().stream()
                .mapToDouble(GameEngine.GenBreakdown::energyPerSec)
                .sum();
        assertEquals(production.get(1L), breakdownSum, 0.001);
    }

    // === NaN / Infinity guards ===

    @Test
    @DisplayName("calcCoreBoost: при VC=NaN повертає 1.0 (без зараження production)")
    void coreBoost_nanCrystals_safe() {
        Resource crystals = createResource(2L, "VC", "Кристал пустоти", 0);
        PlayerResource playerCrystals = instantiate(PlayerResource.class);
        playerCrystals.setSave(save);
        playerCrystals.setResource(crystals);
        playerCrystals.setNumber(Double.NaN); // корумповано
        playerCrystals.setExponent(0);

        PlayerUpgrade coreUpgrade = createPlayerUpgrade(save,
                createUpgradeContent(20L, "CORE", 0.1), 5);

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L))
                .thenReturn(List.of(playerEnergy, playerCrystals));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(coreUpgrade));

        gameEngine.tick();

        // без NaN-зараження: rate = 0.5 (як без ядра), tick = 0.05
        BigNum expected = new BigNum(0.05, 0);
        assertEquals(expected.getNumber(), playerEnergy.getNumber(), 0.001);
    }

    @Test
    @DisplayName("processSave пропускає ресурс з non-finite rate (не викликає BigNum Infinity)")
    void processSave_nonFiniteRate_skipped() {
        // Налаштовуємо ген який перевищить Infinity через ENERGY_POW при великій базі.
        playerVoidGen.setLevel(1_000_000); // rate = 0.5 * 1e6 = 5e5
        PlayerUpgrade pow = createPlayerUpgrade(save,
                createUpgradeContent(40L, "ENERGY_POW", 1.0), 100); // pow = 1 + 100 = 101

        when(saveRepository.findAll()).thenReturn(List.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(playerEnergy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(playerVoidGen));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of(pow));

        // 5e5^101 ≈ 1e5757 → Infinity як double. Engine має пропустити це без падіння.
        assertDoesNotThrow(() -> gameEngine.tick());
        // Енергія не зіпсована (0 залишилось 0).
        assertEquals(0, playerEnergy.getNumber(), 0.001);
    }

    // === Helper методи ===

    private Upgrade createUpgradeContent(Long id, String effectType, double effectValue) {
        Upgrade u = instantiate(Upgrade.class);
        ReflectionTestUtils.setField(u, "id", id);
        ReflectionTestUtils.setField(u, "effectType", effectType);
        ReflectionTestUtils.setField(u, "effectValue", effectValue);
        return u;
    }

    private PlayerUpgrade createPlayerUpgrade(Save save, Upgrade upgrade, int level) {
        PlayerUpgrade pu = new PlayerUpgrade();
        pu.setSave(save);
        pu.setUpgrade(upgrade);
        pu.setLevel(level);
        return pu;
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
