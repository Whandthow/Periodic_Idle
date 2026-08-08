package com.periodic.idle.engine;

import com.periodic.idle.content.Generator;
import com.periodic.idle.content.GeneratorRepository;
import com.periodic.idle.content.Resource;
import com.periodic.idle.content.ResourceRepository;
import com.periodic.idle.engine.config.PrestigeProperties;
import com.periodic.idle.player.PlayerGenerator;
import com.periodic.idle.player.PlayerGeneratorRepository;
import com.periodic.idle.player.PlayerResource;
import com.periodic.idle.player.PlayerResourceRepository;
import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * Per-browser resolution клієнтського токена в save. Кожен новий токен отримує
 * власний, повністю ізольований {@link Save} з нуля (окремий прогрес на кожен
 * браузер/пристрій) — так двоє гравців, що зайшли одночасно, не діляться станом.
 */
@Service
@RequiredArgsConstructor
public class SaveService {

    private final PrestigeProperties prestigeProperties;

    private final SaveRepository saveRepository;
    private final ResourceRepository resourceRepository;
    private final GeneratorRepository generatorRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;

    @Transactional
    public Save findOrCreateByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("clientToken required");
        }

        return saveRepository.findByClientToken(token)
                .orElseGet(() -> createNewSave(token));
    }

    private Save createNewSave(String token) {
        Save draft = new Save();
        draft.setPlayerName("Player");
        draft.setClientToken(token);
        draft.setLastTick(LocalDateTime.now());
        draft.setCreatedAt(LocalDateTime.now());
        final Save save = saveRepository.save(draft);

        for (Resource r : resourceRepository.findAll()) {
            PlayerResource pr = new PlayerResource();
            pr.setSave(save);
            pr.setResource(r);
            if ("E".equals(r.getCode())) {
                pr.setNumber(prestigeProperties.starterEnergyNumber());
                pr.setExponent(prestigeProperties.starterEnergyExponent());
            } else {
                pr.setNumber(0);
                pr.setExponent(0);
            }
            playerResourceRepository.save(pr);
        }

        // Перший генератор одразу на рівні 1 — інакше свіжий save застрягне без жодного виробництва.
        generatorRepository.findAll().stream()
                .min(Comparator.comparing(Generator::getId))
                .ifPresent(first -> {
                    PlayerGenerator pg = new PlayerGenerator();
                    pg.setSave(save);
                    pg.setGenerator(first);
                    pg.setLevel(1);
                    playerGeneratorRepository.save(pg);
                });

        return save;
    }
}
