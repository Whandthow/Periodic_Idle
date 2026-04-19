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

    @Transactional
    public void buy(Long saveId, Long upgradeId) {
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

        // Рахуємо вартість
        double costNum = upgrade.getCostNumber() * Math.pow(upgrade.getCostMultiplier(), currentLevel);
        long costExp = upgrade.getCostExponent();
        BigNum cost = new BigNum(costNum, costExp);

        // Перевіряємо ресурси
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource pr = resources.stream()
                .filter(r -> r.getResource().getId().equals(upgrade.getCostResource().getId()))
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
        if (pu == null) {
            pu = new PlayerUpgrade();
            pu.setSave(pr.getSave());
            pu.setUpgrade(upgrade);
            pu.setLevel(1);
        } else {
            pu.setLevel(currentLevel + 1);
        }
        playerUpgradeRepository.save(pu);
    }
}