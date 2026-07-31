package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.Element;
import com.periodic.idle.content.ElementRepository;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Тір 2: синтез атомів з протонів/нейтронів/електронів за рецептом елемента.
 * Прогресія послідовна: елемент Z &gt; 1 доступний лише якщо елемент Z-1 вже синтезовано хоч раз.
 */
@Service
@RequiredArgsConstructor
public class SynthesisService {

    /** Жорсткий запобіжник нескінченного циклу при буст-синтезі. */
    private static final long BULK_HARD_CAP = 100_000L;

    private final ElementRepository elementRepository;
    private final PlayerElementRepository playerElementRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final SaveRepository saveRepository;

    /**
     * Синтезувати до {@code amount} атомів (amount &lt; 0 = максимум за наявні частинки).
     * Повертає фактичну синтезовану кількість.
     */
    @Transactional
    public long synthesizeBulk(Long saveId, Long elementId, long amount) {
        if (amount == 0) return 0;

        Element element = elementRepository.findById(elementId)
                .orElseThrow(() -> new RuntimeException("Element not found"));

        if (element.getAtomicNumber() > 1 && !previousDiscovered(saveId, element)) {
            throw new RuntimeException("Спочатку синтезуйте попередній елемент у таблиці");
        }

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource p = findByCode(resources, "p");
        PlayerResource n = findByCode(resources, "n");
        PlayerResource e = findByCode(resources, "e");
        if (p == null || n == null || e == null) {
            throw new RuntimeException("Particle resources not initialized");
        }

        long maxByP = maxAffordable(wholeAmount(p), element.getCostProtons());
        long maxByN = maxAffordable(wholeAmount(n), element.getCostNeutrons());
        long maxByE = maxAffordable(wholeAmount(e), element.getCostElectrons());
        long maxAffordable = Math.min(maxByP, Math.min(maxByN, maxByE));

        long target = amount < 0
                ? Math.min(maxAffordable, BULK_HARD_CAP)
                : Math.min(Math.min(amount, maxAffordable), BULK_HARD_CAP);

        if (target <= 0) {
            if (amount < 0) return 0;
            throw new RuntimeException("Not enough particles");
        }

        subtractWhole(p, element.getCostProtons() * target);
        subtractWhole(n, element.getCostNeutrons() * target);
        subtractWhole(e, element.getCostElectrons() * target);
        playerResourceRepository.save(p);
        playerResourceRepository.save(n);
        playerResourceRepository.save(e);

        List<PlayerElement> owned = playerElementRepository.findBySaveId(saveId);
        PlayerElement pe = owned.stream()
                .filter(x -> x.getElement().getId().equals(elementId))
                .findFirst()
                .orElseGet(() -> {
                    PlayerElement created = new PlayerElement();
                    created.setSave(saveRepository.findById(saveId)
                            .orElseThrow(() -> new RuntimeException("Save not found")));
                    created.setElement(element);
                    created.setCount(0);
                    return created;
                });
        pe.setCount(pe.getCount() + target);
        playerElementRepository.save(pe);

        return target;
    }

    private boolean previousDiscovered(Long saveId, Element element) {
        return playerElementRepository.findBySaveId(saveId).stream()
                .anyMatch(pe -> pe.getElement().getAtomicNumber() == element.getAtomicNumber() - 1
                        && pe.getCount() > 0);
    }

    private long maxAffordable(long available, long cost) {
        if (cost <= 0) return Long.MAX_VALUE;
        return available / cost;
    }

    /** Ціла кількість частинки як BigNum -> long (без урахування дробової частини). */
    private long wholeAmount(PlayerResource pr) {
        double mantissa = pr.getNumber();
        long exponent = pr.getExponent();
        if (!Double.isFinite(mantissa) || mantissa <= 0) return 0L;
        if (exponent >= 18) return Long.MAX_VALUE;
        double raw = mantissa * Math.pow(10, exponent);
        if (!Double.isFinite(raw) || raw >= (double) Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) Math.floor(raw);
    }

    private void subtractWhole(PlayerResource pr, long amount) {
        if (amount <= 0) return;
        BigNum current = new BigNum(pr.getNumber(), pr.getExponent());
        BigNum result = current.subtract(new BigNum(amount, 0));
        pr.setNumber(result.getNumber());
        pr.setExponent(result.getExponent());
    }

    private PlayerResource findByCode(List<PlayerResource> resources, String code) {
        return resources.stream()
                .filter(r -> r.getResource() != null && code.equals(r.getResource().getCode()))
                .findFirst()
                .orElse(null);
    }
}
