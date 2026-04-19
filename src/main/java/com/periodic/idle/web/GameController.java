package com.periodic.idle.web;

import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GameController {

    private final PlayerResourceRepository playerResourceRepository;

    @GetMapping("/state/{saveId}")
    public List<Map<String, Object>> getState(@PathVariable Long saveId) {
        return playerResourceRepository.findBySaveId(saveId).stream()
                .map(pr -> Map.<String, Object>of(
                        "resource", pr.getResource().getCode(),
                        "number", pr.getNumber(),
                        "exponent", pr.getExponent()
                ))
                .toList();
    }
}