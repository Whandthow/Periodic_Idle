package com.periodic.idle.engine;

import com.periodic.idle.content.*;
import com.periodic.idle.player.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaveTransferServiceTest {

    @Mock private SaveRepository saveRepository;
    @Mock private PlayerResourceRepository playerResourceRepository;
    @Mock private PlayerGeneratorRepository playerGeneratorRepository;
    @Mock private PlayerUpgradeRepository playerUpgradeRepository;
    @Mock private PlayerElementRepository playerElementRepository;
    @Mock private GeneratorRepository generatorRepository;
    @Mock private UpgradeRepository upgradeRepository;
    @Mock private ElementRepository elementRepository;

    @InjectMocks
    private SaveTransferService saveTransferService;

    private Save save;
    private PlayerResource energy;
    private PlayerGenerator gen1;

    @BeforeEach
    void setUp() {
        save = instantiate(Save.class);
        ReflectionTestUtils.setField(save, "id", 1L);

        Resource eRes = instantiate(Resource.class);
        ReflectionTestUtils.setField(eRes, "code", "E");
        energy = instantiate(PlayerResource.class);
        energy.setResource(eRes);
        energy.setNumber(5.0);
        energy.setExponent(3);

        Generator g = instantiate(Generator.class);
        ReflectionTestUtils.setField(g, "code", "void_gen");
        gen1 = instantiate(PlayerGenerator.class);
        gen1.setGenerator(g);
        gen1.setLevel(7);
    }

    @Test
    @DisplayName("exportSave: серіалізує ресурси/генератори/прапори за кодом контенту")
    void exportSave_serializesByCode() {
        save.setBrokenInfinity(true);
        save.setMatterCollapses(3L);
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(gen1));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of());
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of());

        Map<String, Object> exported = saveTransferService.exportSave(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> saveFlags = (Map<String, Object>) exported.get("save");
        assertEquals(true, saveFlags.get("brokenInfinity"));
        assertEquals(3L, saveFlags.get("matterCollapses"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) exported.get("resources");
        assertEquals(1, resources.size());
        assertEquals("E", resources.get(0).get("code"));
        assertEquals(5.0, resources.get(0).get("number"));
        assertEquals(3L, resources.get(0).get("exponent"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> generators = (List<Map<String, Object>>) exported.get("generators");
        assertEquals("void_gen", generators.get(0).get("code"));
        assertEquals(7, generators.get(0).get("level"));
    }

    @Test
    @DisplayName("importSave: відновлює ресурси/генератори/прапори за кодом, оновлює існуючі рядки")
    void importSave_restoresExistingRows() {
        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of(energy));
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(List.of(gen1));
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of());
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of());

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> saveFlags = Map.of("brokenInfinity", true, "matterCollapses", 9, "autobuyEnabled", false);
        data.put("save", saveFlags);
        data.put("resources", List.of(Map.of("code", "E", "number", 9.0, "exponent", 20)));
        data.put("generators", List.of(Map.of("code", "void_gen", "level", 99)));
        data.put("upgrades", List.of());
        data.put("elements", List.of());

        saveTransferService.importSave(1L, data);

        assertTrue(save.isBrokenInfinity());
        assertEquals(9L, save.getMatterCollapses());
        assertFalse(save.isAutobuyEnabled());
        assertEquals(9.0, energy.getNumber(), 1e-9);
        assertEquals(20L, energy.getExponent());
        assertEquals(99, gen1.getLevel());
    }

    @Test
    @DisplayName("importSave: генератор відсутній у гравця, але існує в контенті -> створює новий рядок")
    void importSave_missingPlayerGenerator_createsFromContent() {
        Generator gen2Content = instantiate(Generator.class);
        ReflectionTestUtils.setField(gen2Content, "code", "quantum_loop");

        when(saveRepository.findById(1L)).thenReturn(Optional.of(save));
        when(playerResourceRepository.findBySaveId(1L)).thenReturn(List.of());
        when(playerGeneratorRepository.findBySaveId(1L)).thenReturn(new ArrayList<>());
        when(playerUpgradeRepository.findBySaveId(1L)).thenReturn(List.of());
        when(playerElementRepository.findBySaveId(1L)).thenReturn(List.of());
        when(generatorRepository.findAll()).thenReturn(List.of(gen2Content));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generators", List.of(Map.of("code", "quantum_loop", "level", 5)));

        saveTransferService.importSave(1L, data);

        verify(playerGeneratorRepository).save(argThat(pg ->
                pg.getGenerator() == gen2Content && pg.getLevel() == 5));
    }

    @Test
    @DisplayName("importSave: null payload -> кидає помилку")
    void importSave_nullPayload_throws() {
        assertThrows(RuntimeException.class, () -> saveTransferService.importSave(1L, null));
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
