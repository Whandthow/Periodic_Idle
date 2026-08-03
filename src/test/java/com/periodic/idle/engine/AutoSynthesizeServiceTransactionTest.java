package com.periodic.idle.engine;

import com.periodic.idle.player.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Регресійний тест на реальному Spring-контексті (не Mockito-юніт, який AOP-проксі
 * взагалі не залучає): {@link AutoSynthesizeService#tickAutoSynthesize()} НЕ повинен
 * ділити одну фізичну транзакцію з {@link SynthesisService#synthesizeBulk}, інакше
 * помилка на будь-якому з наступних елементів (тут — усі, крім Гідрогену, які по черзі
 * впираються в послідовну прогресію/нестачу частинок) позначає rollback-only ще до
 * try/catch, і навіть успішний синтез Гідрогену в тому ж тіку не зберігається
 * (UnexpectedRollbackException при commit) — саме так це поводилось наживо.
 *
 * <p>{@link NoOpSchedulingConfig} підміняє {@link TaskScheduler} на no-op: без цього
 * реальні {@code @Scheduled}-тіки (GameEngine, AutoBuyService, і сам
 * AutoSynthesizeService — @EnableScheduling активний у повному Spring-контексті)
 * конкурують за той самий save з нашим явним викликом і роблять тест флейкі
 * (перевірено наживо: той самий сценарій то проходив, то падав на "expected 1 but was 0"
 * залежно від того, встиг фоновий тік втрутитися чи ні).
 */
@SpringBootTest
class AutoSynthesizeServiceTransactionTest {

    @TestConfiguration
    static class NoOpSchedulingConfig {
        @Bean
        TaskScheduler taskScheduler() {
            TaskScheduler noOp = Mockito.mock(TaskScheduler.class);
            ScheduledFuture<?> noOpFuture = Mockito.mock(ScheduledFuture.class);
            Mockito.when(noOp.schedule(Mockito.any(Runnable.class), Mockito.any(Trigger.class)))
                    .thenReturn((ScheduledFuture) noOpFuture);
            Mockito.when(noOp.schedule(Mockito.any(Runnable.class), Mockito.any(Instant.class)))
                    .thenReturn((ScheduledFuture) noOpFuture);
            Mockito.when(noOp.scheduleAtFixedRate(Mockito.any(Runnable.class), Mockito.any(Instant.class), Mockito.any(Duration.class)))
                    .thenReturn((ScheduledFuture) noOpFuture);
            Mockito.when(noOp.scheduleAtFixedRate(Mockito.any(Runnable.class), Mockito.any(Duration.class)))
                    .thenReturn((ScheduledFuture) noOpFuture);
            Mockito.when(noOp.scheduleWithFixedDelay(Mockito.any(Runnable.class), Mockito.any(Instant.class), Mockito.any(Duration.class)))
                    .thenReturn((ScheduledFuture) noOpFuture);
            Mockito.when(noOp.scheduleWithFixedDelay(Mockito.any(Runnable.class), Mockito.any(Duration.class)))
                    .thenReturn((ScheduledFuture) noOpFuture);
            return noOp;
        }
    }

    @Autowired private AutoSynthesizeService autoSynthesizeService;
    @Autowired private SaveService saveService;
    @Autowired private SaveRepository saveRepository;
    @Autowired private PlayerResourceRepository playerResourceRepository;
    @Autowired private PlayerElementRepository playerElementRepository;

    @Test
    @DisplayName("tickAutoSynthesize: провал наступних елементів не відкочує успішний синтез Гідрогену")
    void tickAutoSynthesize_partialFailure_stillPersistsSuccessfulSynthesis() {
        Save save = saveService.findOrCreateByToken("autosynth-tx-test-" + UUID.randomUUID());
        save.setAutoSynthesizeEnabled(true);
        saveRepository.save(save);

        // Рівно на 1 атом Гідрогену (H = 1p + 0n + 1e) — усі важчі елементи або впруться
        // в послідовну прогресію (Не-Гідроген вимагає попереднього елемента), або в
        // нестачу частинок одразу після того, як ці підуть на синтез H.
        setParticle(save.getId(), "p", 1);
        setParticle(save.getId(), "e", 1);

        assertDoesNotThrow(() -> autoSynthesizeService.tickAutoSynthesize());

        long hydrogenCount = playerElementRepository.findBySaveId(save.getId()).stream()
                .filter(pe -> pe.getElement().getAtomicNumber() == 1)
                .mapToLong(PlayerElement::getCount)
                .findFirst()
                .orElse(0L);
        assertEquals(1L, hydrogenCount,
                "успішний синтез Гідрогену мав зберегтися, незважаючи на провал наступних " +
                        "елементів у тому ж тіку");
    }

    private void setParticle(Long saveId, String code, double count) {
        PlayerResource pr = playerResourceRepository.findBySaveId(saveId).stream()
                .filter(r -> code.equals(r.getResource().getCode()))
                .findFirst().orElseThrow();
        pr.setNumber(count);
        pr.setExponent(0);
        playerResourceRepository.save(pr);
    }
}
