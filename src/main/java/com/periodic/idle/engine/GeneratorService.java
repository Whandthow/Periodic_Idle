package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.Generator;
import com.periodic.idle.content.GeneratorRepository;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeneratorService {

    private final GeneratorRepository generatorRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final SaveRepository saveRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;

    /** Нижня межа коефіцієнта подорожчання генератора. */
    private static final double MIN_COST_MULTIPLIER = 1.03;

    /** Жорсткий запобіжник нескінченного циклу при буст-купівлі. */
    private static final int BULK_HARD_CAP = 100_000;

    /**
     * Купити до {@code amount} рівнів. Якщо {@code amount < 0} — купити скільки вистачає ресурсів.
     * Для одного (amount == 1) поведінка така сама, як {@link #buy(Long, Long)}.
     * Повертає фактичну куплену кількість. Якщо нічого не вдалось — кидає помилку.
     */
    @Transactional
    public int buyBulk(Long saveId, Long generatorId, int amount) {
        if (amount == 0) return 0;

        Generator generator = generatorRepository.findById(generatorId)
                .orElseThrow(() -> new RuntimeException("Generator not found"));

        List<PlayerGenerator> playerGenerators = playerGeneratorRepository.findBySaveId(saveId);
        PlayerGenerator pg = playerGenerators.stream()
                .filter(p -> p.getGenerator().getId().equals(generatorId))
                .findFirst()
                .orElse(null);
        int currentLevel = pg != null ? pg.getLevel() : 0;

        List<PlayerUpgrade> playerUpgrades = playerUpgradeRepository.findBySaveId(saveId);
        double effectiveMultiplier = effectiveCostMultiplier(generator.getCostMultiplier(), playerUpgrades);

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource pr = resources.stream()
                .filter(r -> r.getResource().getId().equals(generator.getCostResource().getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Resource not found"));

        BigNum current = new BigNum(pr.getNumber(), pr.getExponent());

        int target = amount < 0 ? BULK_HARD_CAP : Math.min(amount, BULK_HARD_CAP);
        int bought = 0;
        int level = currentLevel;
        while (bought < target) {
            double costNum = generator.getBaseCostNumber() * Math.pow(effectiveMultiplier, level);
            if (!Double.isFinite(costNum) || costNum <= 0) break;
            BigNum cost = new BigNum(costNum, generator.getBaseCostExponent());
            if (current.compareTo(cost) < 0) break;
            current = current.subtract(cost);
            level++;
            bought++;
        }

        if (bought == 0) {
            throw new RuntimeException("Not enough resources");
        }

        pr.setNumber(current.getNumber());
        pr.setExponent(current.getExponent());

        if (pg == null) {
            pg = new PlayerGenerator();
            pg.setSave(saveRepository.findById(saveId)
                    .orElseThrow(() -> new RuntimeException("Save not found")));
            pg.setGenerator(generator);
        }
        pg.setLevel(level);
        playerGeneratorRepository.save(pg);
        return bought;
    }

    /** Купити скільки вистачить на всіх генераторах (пріоритет — за зростанням id). */
    @Transactional
    public int buyAllMax(Long saveId) {
        int total = 0;
        List<Generator> generators = generatorRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Generator::getId))
                .toList();
        // Кілька проходів: після прокачки дорогого може знову стати доступний дешевший.
        boolean progressed = true;
        int pass = 0;
        while (progressed && pass < 8) {
            progressed = false;
            for (Generator g : generators) {
                try {
                    int got = buyBulk(saveId, g.getId(), -1);
                    if (got > 0) { total += got; progressed = true; }
                } catch (RuntimeException ignored) { /* не вистачає на цей ген */ }
            }
            pass++;
        }
        return total;
    }

    @Transactional
    public void buy(Long saveId, Long generatorId) {
        Generator generator = generatorRepository.findById(generatorId)
                .orElseThrow(() -> new RuntimeException("Generator not found"));

        List<PlayerGenerator> playerGenerators = playerGeneratorRepository.findBySaveId(saveId);

        PlayerGenerator pg = playerGenerators.stream()
                .filter(p -> p.getGenerator().getId().equals(generatorId))
                .findFirst()
                .orElse(null);

        int currentLevel = pg != null ? pg.getLevel() : 0;

        // Вартість = baseCost * effectiveMultiplier^currentLevel, де effectiveMultiplier враховує COST_SCALE_REDUCE
        List<PlayerUpgrade> playerUpgrades = playerUpgradeRepository.findBySaveId(saveId);
        double effectiveMultiplier = effectiveCostMultiplier(generator.getCostMultiplier(), playerUpgrades);
        double costNum = generator.getBaseCostNumber() * Math.pow(effectiveMultiplier, currentLevel);
        long costExp = generator.getBaseCostExponent();
        BigNum cost = new BigNum(costNum, costExp);

        // Перевіряємо ресурси
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource pr = resources.stream()
                .filter(r -> r.getResource().getId().equals(generator.getCostResource().getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Resource not found"));

        BigNum current = new BigNum(pr.getNumber(), pr.getExponent());

        if (current.compareTo(cost) < 0) {
            throw new RuntimeException("Not enough resources");
        }

        // Списуємо
        BigNum result = current.subtract(cost);
        pr.setNumber(result.getNumber());
        pr.setExponent(result.getExponent());

        // Підвищуємо рівень
        if (pg == null) {
            pg = new PlayerGenerator();
            pg.setSave(saveRepository.findById(saveId)
                    .orElseThrow(() -> new RuntimeException("Save not found")));
            pg.setGenerator(generator);
            pg.setLevel(1);
        } else {
            pg.setLevel(currentLevel + 1);
        }
        playerGeneratorRepository.save(pg);
    }

    /**
     * Примір: baseMult=1.5, upgrade effectValue=0.01 з level=10 → effective=1.4.
     * Зниження обмежене нижньою межею MIN_COST_MULTIPLIER.
     */
    public double effectiveCostMultiplier(double baseMultiplier, List<PlayerUpgrade> upgrades) {
        double totalReduce = 0.0;
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if ("COST_SCALE_REDUCE".equals(pu.getUpgrade().getEffectType())) {
                totalReduce += pu.getUpgrade().getEffectValue() * pu.getLevel();
            }
        }
        return Math.max(MIN_COST_MULTIPLIER, baseMultiplier - totalReduce);
    }
}
