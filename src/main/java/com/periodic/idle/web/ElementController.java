package com.periodic.idle.web;

import com.periodic.idle.common.BindingEnergy;
import com.periodic.idle.content.Element;
import com.periodic.idle.content.ElementRepository;
import com.periodic.idle.engine.SynthesisService;
import com.periodic.idle.player.PlayerElement;
import com.periodic.idle.player.PlayerElementRepository;
import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Тір 2: періодична таблиця — перегляд елементів (з гейтами зорі/гіпернови) і синтез атомів. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ElementController {

    private final ElementRepository elementRepository;
    private final PlayerElementRepository playerElementRepository;
    private final SaveRepository saveRepository;
    private final SynthesisService synthesisService;

    @GetMapping("/elements/{saveId}")
    public List<Map<String, Object>> getElements(@PathVariable Long saveId) {
        List<PlayerElement> playerElements = playerElementRepository.findBySaveId(saveId);
        List<Element> allElements = elementRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Element::getAtomicNumber))
                .toList();

        int maxDiscovered = 0;
        for (PlayerElement pe : playerElements) {
            if (pe.getCount() > 0) {
                maxDiscovered = Math.max(maxDiscovered, pe.getElement().getAtomicNumber());
            }
        }

        // Наукова концепція (CLAUDE.md, розділ 1): зоряний нуклеосинтез (Z>3) вимагає
        // "запаленої зорі" — накопиченого гелієвого палива (SynthesisService).
        long heliumCount = synthesisService.heliumCount(saveId);
        boolean stellarIgnited = heliumCount >= synthesisService.stellarIgnitionHeliumCount();

        // Елементи важчі за залізо (r-process) вимагають, щоб гравець пережив Гіпернову зорі
        // (Тір 4) хоча б раз — реальні наднові/злиття нейтронних зірок, не звичайний синтез.
        long hypernovaCount = saveRepository.findById(saveId).map(Save::getHypernovaCount).orElse(0L);
        boolean heavyElementsUnlocked = hypernovaCount >= synthesisService.heavyElementHypernovaRequired();

        List<Map<String, Object>> out = new ArrayList<>();
        for (Element el : allElements) {
            long count = playerElements.stream()
                    .filter(pe -> pe.getElement().getId().equals(el.getId()))
                    .findFirst()
                    .map(PlayerElement::getCount)
                    .orElse(0L);

            boolean requiresStar = el.getAtomicNumber() > synthesisService.primordialMaxAtomicNumber();
            boolean requiresHypernova = el.getAtomicNumber() > synthesisService.ironAtomicNumber();
            // Розблоковано для спроби синтезу: перший елемент завжди, інші — коли попередній вже
            // відкритий, І (якщо це зоряний нуклеосинтез) зоря вже "запалена", І (якщо це r-process,
            // важче за залізо) гравець уже пережив Гіпернову.
            boolean sequentiallyUnlocked = el.getAtomicNumber() == 1 || el.getAtomicNumber() <= maxDiscovered + 1;
            boolean unlocked = sequentiallyUnlocked && (!requiresStar || stellarIgnited)
                    && (!requiresHypernova || heavyElementsUnlocked);

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", el.getId());
            map.put("atomicNumber", el.getAtomicNumber());
            map.put("symbol", el.getSymbol());
            map.put("name", el.getName());
            map.put("atomicWeight", el.getAtomicWeight());
            map.put("period", el.getPeriod());
            map.put("groupNumber", el.getGroupNumber());
            map.put("shellConfig", el.getShellConfig());
            map.put("costProtons", el.getCostProtons());
            map.put("costNeutrons", el.getCostNeutrons());
            map.put("costElectrons", el.getCostElectrons());
            map.put("count", count);
            map.put("unlocked", unlocked);
            map.put("requiresStar", requiresStar);
            map.put("stellarIgnited", stellarIgnited);
            map.put("requiresHypernova", requiresHypernova);
            map.put("heavyElementsUnlocked", heavyElementsUnlocked);
            if (requiresStar && !stellarIgnited) {
                map.put("lockedReason", "Потрібна зоря: " + heliumCount + " / "
                        + synthesisService.stellarIgnitionHeliumCount() + " He");
            } else if (requiresHypernova && !heavyElementsUnlocked) {
                map.put("lockedReason", "Потрібна наднова: переживіть Гіпернову зорі (Тір 4), "
                        + "щоб відкрити r-process синтез важчих за залізо елементів");
            } else if (!sequentiallyUnlocked) {
                map.put("lockedReason", "Спочатку синтезуйте попередній елемент");
            } else {
                map.put("lockedReason", null);
            }
            // Наукова концепція (CLAUDE.md, розділ 1): реальна енергія зв'язку ядра (SEMF),
            // видима гравцеві — синтез до заліза-56 повертає E, важче за залізо — коштує E.
            int massNumber = (int) (el.getCostProtons() + el.getCostNeutrons());
            double bindingEnergyMeV = BindingEnergy.totalMeV(el.getAtomicNumber(), massNumber);
            map.put("bindingEnergyMeV", bindingEnergyMeV);
            map.put("exothermic", el.getAtomicNumber() <= synthesisService.ironAtomicNumber());
            out.add(map);
        }
        return out;
    }

    @PostMapping("/synthesize")
    public Map<String, Object> synthesize(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Long elementId = ((Number) request.get("elementId")).longValue();
        Object amt = request.get("amount");
        long amount = amt == null ? 1 : ((Number) amt).longValue(); // -1 = max
        long synthesized = synthesisService.synthesizeBulk(saveId, elementId, amount);
        return Map.of("status", "ok", "synthesized", synthesized);
    }
}
