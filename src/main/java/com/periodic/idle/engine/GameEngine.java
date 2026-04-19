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

    @Scheduled(fixedRate = 1000)
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

        for (PlayerGenerator pg : generators) {
            if (pg.getLevel() <= 0) continue;

            for (GeneratorOutput output : pg.getGenerator().getOutputs()) {
                double rate = output.getRatePerLevel() * pg.getLevel();

                PlayerResource pr = resources.stream()
                        .filter(r -> r.getResource().getId().equals(output.getResource().getId()))
                        .findFirst()
                        .orElse(null);

                if (pr == null) continue;

                BigNum current = new BigNum(pr.getNumber(), pr.getExponent());
                BigNum addition = new BigNum(rate, 0);
                BigNum result = current.add(addition);

                pr.setNumber(result.getNumber());
                pr.setExponent(result.getExponent());
            }
        }
    }
}