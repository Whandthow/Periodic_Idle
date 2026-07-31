package com.periodic.idle.engine;

import com.periodic.idle.content.Element;
import com.periodic.idle.content.ElementRepository;
import com.periodic.idle.content.Generator;
import com.periodic.idle.content.GeneratorRepository;
import com.periodic.idle.content.Upgrade;
import com.periodic.idle.content.UpgradeRepository;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Мануальне збереження/відновлення прогресу як текстового (JSON) блоку —
 * гравець може скопіювати/скачати його як .txt і завантажити пізніше
 * (на іншому пристрої чи як бекап), незалежно від серверної БД.
 * Прив'язка йде за CODE контенту (не raw id), щоб файл лишався портативним
 * навіть якщо міграції змінять числові id.
 */
@Service
@RequiredArgsConstructor
public class SaveTransferService {

    private static final int FORMAT_VERSION = 1;

    private final SaveRepository saveRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final PlayerElementRepository playerElementRepository;
    private final GeneratorRepository generatorRepository;
    private final UpgradeRepository upgradeRepository;
    private final ElementRepository elementRepository;

    public Map<String, Object> exportSave(Long saveId) {
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", FORMAT_VERSION);
        out.put("exportedAt", java.time.Instant.now().toString());

        Map<String, Object> saveFlags = new LinkedHashMap<>();
        saveFlags.put("brokenInfinity", save.isBrokenInfinity());
        saveFlags.put("matterCollapses", save.getMatterCollapses());
        saveFlags.put("autobuyEnabled", save.isAutobuyEnabled());
        out.put("save", saveFlags);

        out.put("resources", playerResourceRepository.findBySaveId(saveId).stream()
                .filter(pr -> pr.getResource() != null)
                .map(pr -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", pr.getResource().getCode());
                    m.put("number", pr.getNumber());
                    m.put("exponent", pr.getExponent());
                    return m;
                }).toList());

        out.put("generators", playerGeneratorRepository.findBySaveId(saveId).stream()
                .filter(pg -> pg.getGenerator() != null)
                .map(pg -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", pg.getGenerator().getCode());
                    m.put("level", pg.getLevel());
                    return m;
                }).toList());

        out.put("upgrades", playerUpgradeRepository.findBySaveId(saveId).stream()
                .filter(pu -> pu.getUpgrade() != null)
                .map(pu -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", pu.getUpgrade().getCode());
                    m.put("level", pu.getLevel());
                    return m;
                }).toList());

        out.put("elements", playerElementRepository.findBySaveId(saveId).stream()
                .filter(pe -> pe.getElement() != null)
                .map(pe -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("symbol", pe.getElement().getSymbol());
                    m.put("count", pe.getCount());
                    return m;
                }).toList());

        return out;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void importSave(Long saveId, Map<String, Object> data) {
        if (data == null) throw new RuntimeException("Empty import payload");
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));

        Object saveFlagsObj = data.get("save");
        if (saveFlagsObj instanceof Map) {
            Map<String, Object> saveFlags = (Map<String, Object>) saveFlagsObj;
            if (saveFlags.get("brokenInfinity") instanceof Boolean b) save.setBrokenInfinity(b);
            if (saveFlags.get("matterCollapses") instanceof Number n) save.setMatterCollapses(n.longValue());
            if (saveFlags.get("autobuyEnabled") instanceof Boolean b) save.setAutobuyEnabled(b);
            saveRepository.save(save);
        }

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        for (Map<String, Object> row : asMapList(data.get("resources"))) {
            String code = String.valueOf(row.get("code"));
            resources.stream()
                    .filter(pr -> pr.getResource() != null && code.equals(pr.getResource().getCode()))
                    .findFirst()
                    .ifPresent(pr -> {
                        pr.setNumber(((Number) row.get("number")).doubleValue());
                        pr.setExponent(((Number) row.get("exponent")).longValue());
                        playerResourceRepository.save(pr);
                    });
        }

        List<PlayerGenerator> generators = playerGeneratorRepository.findBySaveId(saveId);
        for (Map<String, Object> row : asMapList(data.get("generators"))) {
            String code = String.valueOf(row.get("code"));
            int level = ((Number) row.get("level")).intValue();
            PlayerGenerator pg = generators.stream()
                    .filter(g -> g.getGenerator() != null && code.equals(g.getGenerator().getCode()))
                    .findFirst()
                    .orElse(null);
            if (pg == null) {
                Generator g = generatorRepository.findAll().stream()
                        .filter(x -> code.equals(x.getCode())).findFirst().orElse(null);
                if (g == null) continue;
                pg = new PlayerGenerator();
                pg.setSave(save);
                pg.setGenerator(g);
            }
            pg.setLevel(level);
            playerGeneratorRepository.save(pg);
        }

        List<PlayerUpgrade> upgrades = playerUpgradeRepository.findBySaveId(saveId);
        for (Map<String, Object> row : asMapList(data.get("upgrades"))) {
            String code = String.valueOf(row.get("code"));
            int level = ((Number) row.get("level")).intValue();
            PlayerUpgrade pu = upgrades.stream()
                    .filter(u -> u.getUpgrade() != null && code.equals(u.getUpgrade().getCode()))
                    .findFirst()
                    .orElse(null);
            if (pu == null) {
                Upgrade u = upgradeRepository.findAll().stream()
                        .filter(x -> code.equals(x.getCode())).findFirst().orElse(null);
                if (u == null) continue;
                pu = new PlayerUpgrade();
                pu.setSave(save);
                pu.setUpgrade(u);
            }
            pu.setLevel(level);
            playerUpgradeRepository.save(pu);
        }

        List<PlayerElement> elements = playerElementRepository.findBySaveId(saveId);
        for (Map<String, Object> row : asMapList(data.get("elements"))) {
            String symbol = String.valueOf(row.get("symbol"));
            long count = ((Number) row.get("count")).longValue();
            PlayerElement pe = elements.stream()
                    .filter(e -> e.getElement() != null && symbol.equals(e.getElement().getSymbol()))
                    .findFirst()
                    .orElse(null);
            if (pe == null) {
                Element el = elementRepository.findAll().stream()
                        .filter(x -> symbol.equals(x.getSymbol())).findFirst().orElse(null);
                if (el == null) continue;
                pe = new PlayerElement();
                pe.setSave(save);
                pe.setElement(el);
            }
            pe.setCount(count);
            playerElementRepository.save(pe);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return (List<Map<String, Object>>) list;
    }
}
