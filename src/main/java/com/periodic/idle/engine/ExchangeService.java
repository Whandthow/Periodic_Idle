package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.player.PlayerResource;
import com.periodic.idle.player.PlayerResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Обмін ресурсів між тірами.
 * Поки що єдина операція: "Розщепити кристал" — 1 VC -> 1p + 1n + 1e.
 */
@Service
@RequiredArgsConstructor
public class ExchangeService {

    private static final int BULK_HARD_CAP = 1_000_000;

    private final PlayerResourceRepository playerResourceRepository;

    /**
     * Розщепити до {@code amount} кристалів (amount &lt; 0 = максимум). Повертає скільки реально розщеплено.
     */
    @Transactional
    public long splitCrystals(Long saveId, long amount) {
        if (amount == 0) return 0;

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource vc = findByCode(resources, "VC");
        PlayerResource p  = findByCode(resources, "p");
        PlayerResource n  = findByCode(resources, "n");
        PlayerResource e  = findByCode(resources, "e");
        if (vc == null || p == null || n == null || e == null) {
            throw new RuntimeException("Matter resources are not initialized");
        }

        BigNum vcBig = new BigNum(vc.getNumber(), vc.getExponent());
        // Поточна кількість кристалів як double-log10 для порівняння
        long available = bigNumToLongClamped(vcBig);

        long target;
        if (amount < 0) {
            target = Math.min(available, BULK_HARD_CAP);
        } else {
            target = Math.min(Math.min(amount, available), BULK_HARD_CAP);
        }
        if (target <= 0) {
            throw new RuntimeException("Not enough crystals");
        }

        BigNum cost = new BigNum(target, 0);
        BigNum remaining = vcBig.subtract(cost);
        vc.setNumber(remaining.getNumber());
        vc.setExponent(remaining.getExponent());
        playerResourceRepository.save(vc);

        addWhole(p, target);
        addWhole(n, target);
        addWhole(e, target);
        playerResourceRepository.save(p);
        playerResourceRepository.save(n);
        playerResourceRepository.save(e);

        return target;
    }

    private void addWhole(PlayerResource pr, long amount) {
        BigNum current = new BigNum(pr.getNumber(), pr.getExponent());
        BigNum result = current.add(new BigNum(amount, 0));
        pr.setNumber(result.getNumber());
        pr.setExponent(result.getExponent());
    }

    /** Перетворює BigNum у long; якщо занадто велике — обмежує Long.MAX_VALUE. */
    private long bigNumToLongClamped(BigNum value) {
        if (value.getNumber() <= 0) return 0;
        if (value.getExponent() >= 18) return Long.MAX_VALUE;
        double raw = value.getNumber() * Math.pow(10, value.getExponent());
        if (raw >= (double) Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) Math.floor(raw);
    }

    private PlayerResource findByCode(List<PlayerResource> resources, String code) {
        return resources.stream()
                .filter(r -> r.getResource() != null && code.equals(r.getResource().getCode()))
                .findFirst()
                .orElse(null);
    }
}
