package com.periodic.idle.web;

import com.periodic.idle.engine.PrestigeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Реінкарнація Тіру 0: розрахунок потенційного виграшу, сам престиж, повний скид save. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PrestigeController {

    private final PrestigeService prestigeService;

    @GetMapping("/prestige-info/{saveId}")
    public Map<String, Object> prestigeInfo(@PathVariable Long saveId) {
        var gain = prestigeService.calcPotentialGain(saveId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("number", gain.getNumber());
        map.put("exponent", gain.getExponent());
        map.put("minLog10Energy", PrestigeService.PRESTIGE_MIN_LOG10_ENERGY);
        return map;
    }

    @PostMapping("/prestige")
    public Map<String, Object> prestige(@RequestBody Map<String, Long> request) {
        var gain = prestigeService.prestige(request.get("saveId"));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "ok");
        map.put("gainNumber", gain.getNumber());
        map.put("gainExponent", gain.getExponent());
        return map;
    }

    @PostMapping("/reset")
    public Map<String, String> resetSave(@RequestBody Map<String, Long> request) {
        prestigeService.hardReset(request.get("saveId"));
        return Map.of("status", "ok");
    }
}
