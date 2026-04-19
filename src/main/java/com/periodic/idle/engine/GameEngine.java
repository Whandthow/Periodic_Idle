package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.GeneratorOutput;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GameEngine {

    private final SaveRepository saveRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;

    @Scheduled(fixedRate = 100)
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
        List<PlayerGenerator> generators = playerGeneratorRepository.findBySaveId(save.getId());
        List<PlayerUpgrade> upgrades = playerUpgradeRepository.findBySaveId(save.getId());

        double energyMult = calcMultiplier(upgrades, "ENERGY_MULT");
        double genMult = calcMultiplier(upgrades, "GENERATOR_MULT");

        for (PlayerGenerator pg : generators) {
            if (pg.getLevel() <= 0) continue;

            for (GeneratorOutput output : pg.getGenerator().getOutputs()) {
                double rate = output.getRatePerLevel() * pg.getLevel();
                rate *= genMult;
                rate *= energyMult;

                PlayerResource pr = findResource(resources, output.getResource().getId());
                if (pr == null) continue;

                BigNum current = new BigNum(pr.getNumber(), pr.getExponent());
                BigNum addition = new BigNum(rate, 0);
                BigNum result = current.add(addition);

                pr.setNumber(result.getNumber());
                pr.setExponent(result.getExponent());
            }
        }
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