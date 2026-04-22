package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.Upgrade;
import com.periodic.idle.content.UpgradeRepository;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UpgradeService {

    private final UpgradeRepository upgradeRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final PlayerResourceRepository playerResourceRepository;

    /** Запобіжник нескінченного циклу bulk-купівлі. */
    private static final int BULK_HARD_CAP = 100_000;

    @Transactional
    public void buy(Long saveId, Long upgradeId) {
        buyBulk(saveId, upgradeId, 1);
    }

    /**
     * Купити до {@code amount} рівнів апгрейду. {@code amount < 0} = max (скільки вистачить).
     * Якщо не вдалось купити жодного — кидає помилку.
     */
    @Transactional
    public int buyBulk(Long saveId, Long upgradeId, int amount) {
        if (amount == 0) return 0;

        Upgrade upgrade = upgradeRepository.findById(upgradeId)
                .orElseThrow(() -> new RuntimeException("Upgrade not found"));

        List<PlayerUpgrade> playerUpgrades = playerUpgradeRepository.findBySaveId(saveId);

        PlayerUpgrade pu = playerUpgrades.stream()
                .filter(p -> p.getUpgrade().getId().equals(upgradeId))
                .findFirst()
                .orElse(null);

        int currentLevel = pu != null ? pu.getLevel() : 0;

        if (currentLevel >= upgrade.getMaxLevel()) {
            throw new RuntimeException("Already max level");
        }

        // Гейт відкриття: залежить від тіру Ядра
        int requiredCoreTier = upgrade.getUnlockCoreTier();
        if (requiredCoreTier > 0) {
            int coreLevel = playerUpgrades.stream()
                    .filter(p -> "CORE".equals(p.getUpgrade().getEffectType()))
                    .mapToInt(PlayerUpgrade::getLevel)
                    .max()
                    .orElse(0);
            if (coreLevel < requiredCoreTier) {
                throw new RuntimeException("Upgrade locked: requires Core tier " + requiredCoreTier);
            }
        }

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource pr = resources.stream()
                .filter(r -> r.getResource().getId().equals(upgrade.getCostResource().getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Resource not found"));

        BigNum current = new BigNum(pr.getNumber(), pr.getExponent());

        int maxLevel = upgrade.getMaxLevel();
        int target = amount < 0 ? BULK_HARD_CAP : Math.min(amount, BULK_HARD_CAP);
        int bought = 0;
        int level = currentLevel;
        while (bought < target && level < maxLevel) {
            double costNum = upgrade.getCostNumber() * Math.pow(upgrade.getCostMultiplier(), level);
            if (!Double.isFinite(costNum) || costNum <= 0) break;
            BigNum cost = new BigNum(costNum, upgrade.getCostExponent());
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

        if (pu == null) {
            pu = new PlayerUpgrade();
            pu.setSave(pr.getSave());
            pu.setUpgrade(upgrade);
        }
        pu.setLevel(level);
        playerUpgradeRepository.save(pu);
        return bought;
    }
}
