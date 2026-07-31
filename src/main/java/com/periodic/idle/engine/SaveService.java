package com.periodic.idle.engine;

import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-browser resolution клієнтського токена в save. Гра поки що працює з єдиним
 * спільним save (id=1, посіяний Flyway-міграціями) — токен просто прив'язується
 * до нього при першому зверненні з нового браузера. Справжні multi-save
 * (окремий save на кожен токен) — крок 16 у роадмапі.
 */
@Service
@RequiredArgsConstructor
public class SaveService {

    private static final Long DEFAULT_SAVE_ID = 1L;

    private final SaveRepository saveRepository;

    @Transactional
    public Save findOrCreateByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("clientToken required");
        }

        return saveRepository.findByClientToken(token)
                .orElseGet(() -> {
                    Save save = saveRepository.findById(DEFAULT_SAVE_ID)
                            .orElseThrow(() -> new RuntimeException("Default save (id=1) not found"));
                    if (save.getClientToken() == null) {
                        save.setClientToken(token);
                        save = saveRepository.save(save);
                    }
                    return save;
                });
    }
}
