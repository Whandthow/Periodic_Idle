package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.GeneratorOutput;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GameEngine {

    private static final long TICK_INTERVAL_MS = 100;
    private static final double TICK_INTERVAL_SEC = TICK_INTERVAL_MS / 1000.0;

    private final SaveRepository saveRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    @Transactional
    public void tick() {
        List<Save> saves = saveRepository.findAll();
        for (Save save : saves) {
            processSave(save);
            save.setLastTick(LocalDateTime.now());
        }
    }

    private void processSave(Save save) {
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(save.getId());
        Map<Long, Double> productionPerSec = computeProduction(save.getId());

        for (Map.Entry<Long, Double> entry : productionPerSec.entrySet()) {
            PlayerResource pr = findResource(resources, entry.getKey());
            if (pr == null) continue;

            double addPerTick = entry.getValue() * TICK_INTERVAL_SEC;

            BigNum current = new BigNum(pr.getNumber(), pr.getExponent());
            BigNum addition = new BigNum(addPerTick, 0);
            BigNum result = current.add(addition);

            pr.setNumber(result.getNumber());
            pr.setExponent(result.getExponent());
        }
    }

    /** Виробництво за секунду, згруповане за resourceId. Використовується для UI. */
    public Map<Long, Double> calculateProductionPerSec(Long saveId) {
        return computeProduction(saveId);
    }

    private Map<Long, Double> computeProduction(Long saveId) {
        List<PlayerGenerator> generators = playerGeneratorRepository.findBySaveId(saveId);
        List<PlayerUpgrade> upgrades = playerUpgradeRepository.findBySaveId(saveId);

        double energyMult = calcMultiplier(upgrades, "ENERGY_MULT");
        double genMult = calcMultiplier(upgrades, "GENERATOR_MULT");

        Map<Long, Double> production = new HashMap<>();
        for (PlayerGenerator pg : generators) {
            if (pg.getLevel() <= 0) continue;

            for (GeneratorOutput output : pg.getGenerator().getOutputs()) {
                double ratePerSec = output.getRatePerLevel() * pg.getLevel() * genMult * energyMult;
                production.merge(output.getResource().getId(), ratePerSec, Double::sum);
            }
        }
        return production;
    }

    private double calcMultiplier(List<PlayerUpgrade> upgrades, String effectType) {
        double mult = 1.0;
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if (pu.getUpgrade().getEffectType().equals(effectType)) {
                mult += pu.getUpgrade().getEffectValue() * pu.getLevel();
            }
        }
        return mult;
    }

    private PlayerResource findResource(List<PlayerResource> resources, Long resourceId) {
        return resources.stream()
                .filter(r -> r.getResource().getId().equals(resourceId))
                .findFirst()
                .orElse(null);
    }
}
