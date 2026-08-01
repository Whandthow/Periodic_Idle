package com.periodic.idle.engine;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Наздоганяє прогрес для всіх saves одразу після старту застосунку —
 * покриває час, поки сервер був вимкнений (деплой, рестарт, крах).
 */
@Component
@RequiredArgsConstructor
public class OfflineProgressRunner implements ApplicationRunner {

    private final GameEngine gameEngine;

    @Override
    public void run(ApplicationArguments args) {
        gameEngine.applyOfflineProgress();
    }
}
