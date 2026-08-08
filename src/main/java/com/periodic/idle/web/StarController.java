package com.periodic.idle.web;

import com.periodic.idle.content.Star;
import com.periodic.idle.content.StarRepository;
import com.periodic.idle.engine.ElementBonus;
import com.periodic.idle.engine.StarService;
import com.periodic.idle.player.PlayerElement;
import com.periodic.idle.player.PlayerElementRepository;
import com.periodic.idle.player.PlayerStar;
import com.periodic.idle.player.PlayerStarRepository;
import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Тір 4: зорі головної послідовності — перегляд стану (fuel/output, CNO-каталіз) і купівля рівнів. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StarController {

    private final ElementBonus elementBonus;

    private final StarRepository starRepository;
    private final PlayerStarRepository playerStarRepository;
    private final PlayerElementRepository playerElementRepository;
    private final SaveRepository saveRepository;
    private final StarService starService;

    @GetMapping("/stars/{saveId}")
    public Map<String, Object> getStars(@PathVariable Long saveId) {
        List<PlayerStar> playerStars = playerStarRepository.findBySaveId(saveId);
        List<PlayerElement> elements = playerElementRepository.findBySaveId(saveId);
        long hydrogenAvailable = elements.stream()
                .filter(pe -> pe.getElement().getAtomicNumber() == 1)
                .findFirst()
                .map(PlayerElement::getCount)
                .orElse(0L);
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));
        // CNO-каталіз (ElementBonus, реальна астрофізика): C+N+O синтезовані -> зоря
        // пропускає пропорційно більше подій за секунду (StarService.processStarTick).
        double cnoMult = elementBonus.cnoCatalystMult(elements);

        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Star star : starRepository.findAll()) {
            int level = playerStars.stream()
                    .filter(ps -> ps.getStar().getId().equals(star.getId()))
                    .findFirst()
                    .map(PlayerStar::getLevel)
                    .orElse(0);

            long nextLevelCost = (long) Math.ceil(
                    star.getBaseCostHydrogen() * Math.pow(star.getCostMultiplier(), level));
            double fuelPerSec = star.getEventsPerSecPerLevel() * level * star.getFuelHPerEvent() * cnoMult;
            double outputPerSec = star.getEventsPerSecPerLevel() * level * star.getOutputHePerEvent() * cnoMult;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", star.getId());
            map.put("code", star.getCode());
            map.put("name", star.getName());
            map.put("level", level);
            map.put("nextLevelCostHydrogen", nextLevelCost);
            map.put("fuelHPerSec", fuelPerSec);
            map.put("outputHePerSec", outputPerSec);
            map.put("fuelHPerEvent", star.getFuelHPerEvent());
            map.put("outputHePerEvent", star.getOutputHePerEvent());
            out.add(map);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stars", out);
        result.put("hydrogenAvailable", hydrogenAvailable);
        result.put("hypernovaCount", save.getHypernovaCount());
        result.put("cnoCatalystMult", cnoMult);
        result.put("cnoCatalystActive", cnoMult > 1.0);
        return result;
    }

    @PostMapping("/buy-star")
    public Map<String, Object> buyStar(@RequestBody Map<String, Object> request) {
        Long saveId = ((Number) request.get("saveId")).longValue();
        Long starId = ((Number) request.get("starId")).longValue();
        Object amt = request.get("amount");
        int amount = amt == null ? 1 : ((Number) amt).intValue(); // -1 = max
        int bought = starService.buyLevel(saveId, starId, amount);
        return Map.of("status", "ok", "bought", bought);
    }
}
