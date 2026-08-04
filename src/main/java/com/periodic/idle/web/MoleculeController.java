package com.periodic.idle.web;

import com.periodic.idle.content.Molecule;
import com.periodic.idle.content.MoleculeRepository;
import com.periodic.idle.engine.MoleculeService;
import com.periodic.idle.player.PlayerMolecule;
import com.periodic.idle.player.PlayerMoleculeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Тір 3: молекули — перегляд рецептів/стану і синтез з уже зібраних атомів. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MoleculeController {

    private final MoleculeRepository moleculeRepository;
    private final PlayerMoleculeRepository playerMoleculeRepository;
    private final MoleculeService moleculeService;

    @GetMapping("/molecules/{saveId}")
    public List<Map<String, Object>> getMolecules(@PathVariable Long saveId) {
        List<PlayerMolecule> playerMolecules = playerMoleculeRepository.findBySaveId(saveId);
        List<Molecule> allMolecules = moleculeRepository.findAll().stream()
                .sorted(Comparator.comparing(Molecule::getFormula))
                .toList();

        List<Map<String, Object>> out = new ArrayList<>();
        for (Molecule m : allMolecules) {
            long count = playerMolecules.stream()
                    .filter(pm -> pm.getMolecule().getId().equals(m.getId()))
                    .findFirst()
                    .map(PlayerMolecule::getCount)
                    .orElse(0L);

            List<Map<String, Object>> recipe = m.getComponents().stream()
                    .map(c -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("elementSymbol", c.getElement().getSymbol());
                        row.put("atomCount", c.getAtomCount());
                        return (Map<String, Object>) row;
                    })
                    .toList();

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("formula", m.getFormula());
            map.put("name", m.getName());
            map.put("bondEnergyEv", m.getBondEnergyEv());
            map.put("recipe", recipe);
            map.put("count", count);
            map.put("unlocked", moleculeService.canAffordAtLeastOne(saveId, m));
            out.add(map);
        }
        return out;
    }

    @PostMapping("/synthesize-molecule")
    public Map<String, Object> synthesizeMolecule(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Long moleculeId = ((Number) request.get("moleculeId")).longValue();
        Object amt = request.get("amount");
        long amount = amt == null ? 1 : ((Number) amt).longValue(); // -1 = max
        long synthesized = moleculeService.synthesizeBulk(saveId, moleculeId, amount);
        return Map.of("status", "ok", "synthesized", synthesized);
    }
}
