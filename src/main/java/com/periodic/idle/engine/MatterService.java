package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Тір 1: "Колапс матерії" (повторюваний обмін: e308 енергії -> +1 частинка + скид Тіру 0)
 * і одноразовий "Break Infinity" (знімає кап 1e308 після достатньої кількості колапсів).
 */
@Service
@RequiredArgsConstructor
public class MatterService {

    /** Скільки колапсів матерії потрібно накопичити, щоб відкрити Break Infinity. */
    public static final long BREAK_INFINITY_REQUIRED = 10L;

    private static final Set<String> VALID_PARTICLES = Set.of("p", "n", "e");

    private final SaveRepository saveRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;

    @Transactional
    public void collapse(Long saveId, String particle) {
        if (particle == null || !VALID_PARTICLES.contains(particle)) {
            throw new RuntimeException("Unknown particle: " + particle);
        }
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource energy = findByCode(resources, "E");
        if (energy == null) {
            throw new RuntimeException("Resource E missing");
        }
        double log10Energy = energy.getNumber() > 0
                ? Math.log10(energy.getNumber()) + energy.getExponent()
                : 0.0;
        if (log10Energy < GameEngine.ENERGY_CAP_EXPONENT) {
            throw new RuntimeException("Потрібно 1e308 енергії");
        }

        // Повний ресет Тіру 0: енергія -> стартова, рівні генераторів і апгрейдів -> 0,
        // Кристали Пустоти -> 0. Єдине, що переживає колапс, — самі частинки (p/n/e):
        // саме вони тепер єдина "вічна" валюта прогресу Тіру 1 (ParticleBonus, розділ 7.1).
        // Без цього Ядро (CORE), яке масштабується від log10(VC), могло необмежено
        // накопичуватись між колапсами й переповнювати double у GameEngine.calcCoreBoost.
        energy.setNumber(PrestigeService.STARTER_ENERGY_NUMBER);
        energy.setExponent(PrestigeService.STARTER_ENERGY_EXPONENT);
        playerResourceRepository.save(energy);

        List<PlayerGenerator> gens = playerGeneratorRepository.findBySaveId(saveId);
        for (PlayerGenerator pg : gens) {
            pg.setLevel(0);
        }
        playerGeneratorRepository.saveAll(gens);

        List<PlayerUpgrade> upgrades = playerUpgradeRepository.findBySaveId(saveId);
        for (PlayerUpgrade pu : upgrades) {
            pu.setLevel(0);
        }
        playerUpgradeRepository.saveAll(upgrades);

        PlayerResource crystals = findByCode(resources, "VC");
        if (crystals != null) {
            crystals.setNumber(0);
            crystals.setExponent(0);
            playerResourceRepository.save(crystals);
        }

        // +1 частинка обраного типу.
        PlayerResource target = findByCode(resources, particle);
        if (target == null) {
            throw new RuntimeException("Particle resource not initialized: " + particle);
        }
        BigNum result = new BigNum(target.getNumber(), target.getExponent()).add(new BigNum(1, 0));
        target.setNumber(result.getNumber());
        target.setExponent(result.getExponent());
        playerResourceRepository.save(target);

        save.setMatterCollapses(save.getMatterCollapses() + 1);
        saveRepository.save(save);
    }

    @Transactional
    public void breakInfinity(Long saveId) {
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));
        if (save.isBrokenInfinity()) return;
        if (save.getMatterCollapses() < BREAK_INFINITY_REQUIRED) {
            long remaining = BREAK_INFINITY_REQUIRED - save.getMatterCollapses();
            throw new RuntimeException("Потрібно ще " + remaining + " колапсів матерії");
        }
        save.setBrokenInfinity(true);
        saveRepository.save(save);
    }

    private PlayerResource findByCode(List<PlayerResource> resources, String code) {
        return resources.stream()
                .filter(r -> r.getResource() != null && code.equals(r.getResource().getCode()))
                .findFirst()
                .orElse(null);
    }
}
