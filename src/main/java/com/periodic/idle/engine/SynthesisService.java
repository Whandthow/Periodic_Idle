package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.common.BindingEnergy;
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
 *
 * <p>Наукова концепція (CLAUDE.md, розділ 1): нуклеосинтез розділений на дві реальні фази.
 * <b>Первинний</b> (Big Bang nucleosynthesis, Z&lt;=3 — H, He, Li) доступний одразу, як і
 * первинному Всесвіту вистачило лічених хвилин розширення й охолодження. <b>Зоряний</b>
 * (C-N-O-цикл, Z&gt;=4) вимагає "запаленої зорі" — гравець повинен спершу накопичити
 * достатньо гелію (паливо), як реальна протозоря повинна досягти критичної маси, перш
 * ніж гравітаційний тиск запустить термоядерний синтез важчих елементів.
 *
 * <p>Синтез також враховує реальну криву питомої енергії зв'язку ядра (SEMF/Вайцзеккер,
 * {@link BindingEnergy}). Елементи легші за залізо-56 — екзотермічні (синтез повертає
 * енергію в E, як термоядерний синтез у зорі); елементи важчі за залізо — ендотермічні
 * (синтез коштує E, як r-process у наднових).
 */
@Service
@RequiredArgsConstructor
public class SynthesisService {

    /** Жорсткий запобіжник нескінченного циклу при буст-синтезі. */
    private static final long BULK_HARD_CAP = 100_000L;

    /** Залізо-56 — пік кривої енергії зв'язку: межа "самопідтримного" термоядерного синтезу зорі. */
    public static final int IRON_ATOMIC_NUMBER = 26;

    /** H, He, Li — усе, що встиг дати первинний нуклеосинтез за перші ~20хв після Великого вибуху. */
    public static final int PRIMORDIAL_MAX_ATOMIC_NUMBER = 3;

    /**
     * Скільки Гіпернов (StarService — катастрофічний вибух зорі через нестачу палива) потрібно
     * пережити, щоб відкрити синтез елементів важчих за залізо. Реальна фізика: елементи важчі
     * за залізо-56 не утворюються у звичайному термоядерному синтезі головної послідовності —
     * лише r-process (rapid neutron capture) у катастрофічних подіях (наднові, злиття
     * нейтронних зірок) здатен подолати ендотермічний бар'єр (розділ 7.7 CLAUDE.md). Гіпернова
     * StarService — ігровий еквівалент такої події; save.hypernovaCount ніколи не скидається,
     * тож ця умова, на відміну від "запаленої зорі" (heliumCount), не може бути втрачена.
     */
    public static final long HEAVY_ELEMENT_HYPERNOVA_REQUIRED = 1L;

    /**
     * Скільки атомів гелію потрібно накопичити, щоб "запалити зорю" (умовний поріг критичної
     * маси протозорі) і відкрити зоряний нуклеосинтез (Z&gt;=4). Перший прохід — потребує
     * живого тестування (docs/balance.md).
     */
    public static final long STELLAR_IGNITION_HELIUM_COUNT = 1_000L;

    /**
     * Масштаб переведення МеВ у ігрові одиниці E. Перший прохід (як V12-баланс) —
     * підібраний так, щоб внесок був відчутним на масштабі Тіру 2 (E типово ~1e308),
     * але потребує живого тестування (див. docs/balance.md).
     */
    private static final long ENERGY_SCALE_EXPONENT = 298L;

    private final ElementRepository elementRepository;
    private final PlayerElementRepository playerElementRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final SaveRepository saveRepository;

    /**
     * Синтезувати до {@code amount} атомів (amount &lt; 0 = максимум за наявні частинки й енергію).
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
        if (element.getAtomicNumber() > PRIMORDIAL_MAX_ATOMIC_NUMBER
                && heliumCount(saveId) < STELLAR_IGNITION_HELIUM_COUNT) {
            throw new RuntimeException("Потрібна зоря: накопичте " + STELLAR_IGNITION_HELIUM_COUNT
                    + " гелію, щоб запустити зоряний нуклеосинтез (C-N-O-цикл)");
        }
        if (element.getAtomicNumber() > IRON_ATOMIC_NUMBER && !isHeavyElementSynthesisUnlocked(saveId)) {
            throw new RuntimeException("Потрібна наднова: елементи важчі за залізо утворюються лише "
                    + "через r-process — переживіть Гіпернову зорі (Тір 4), перш ніж синтезувати цей елемент");
        }

        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource p = findByCode(resources, "p");
        PlayerResource n = findByCode(resources, "n");
        PlayerResource e = findByCode(resources, "e");
        PlayerResource energy = findByCode(resources, "E");
        if (p == null || n == null || e == null || energy == null) {
            throw new RuntimeException("Particle resources not initialized");
        }

        long maxByP = maxAffordable(wholeAmount(p), element.getCostProtons());
        long maxByN = maxAffordable(wholeAmount(n), element.getCostNeutrons());
        long maxByE = maxAffordable(wholeAmount(e), element.getCostElectrons());
        long maxAffordable = Math.min(maxByP, Math.min(maxByN, maxByE));

        int massNumber = (int) (element.getCostProtons() + element.getCostNeutrons());
        double meVPerAtom = BindingEnergy.totalMeV(element.getAtomicNumber(), massNumber);
        boolean endothermic = meVPerAtom > 0 && element.getAtomicNumber() > IRON_ATOMIC_NUMBER;
        BigNum energyPerAtom = meVPerAtom > 0 ? new BigNum(meVPerAtom, ENERGY_SCALE_EXPONENT) : null;

        long maxByEnergy = Long.MAX_VALUE;
        if (endothermic) {
            BigNum currentEnergy = new BigNum(energy.getNumber(), energy.getExponent());
            maxByEnergy = maxAffordableByEnergy(currentEnergy, energyPerAtom);
            maxAffordable = Math.min(maxAffordable, maxByEnergy);
        }

        long target = amount < 0
                ? Math.min(maxAffordable, BULK_HARD_CAP)
                : Math.min(Math.min(amount, maxAffordable), BULK_HARD_CAP);

        if (target <= 0) {
            if (amount < 0) return 0;
            if (endothermic && maxByEnergy <= 0) {
                throw new RuntimeException(
                        "Not enough energy: елементи важчі за залізо потребують витрат E (ендотермічний синтез)");
            }
            throw new RuntimeException("Not enough particles");
        }

        subtractWhole(p, element.getCostProtons() * target);
        subtractWhole(n, element.getCostNeutrons() * target);
        subtractWhole(e, element.getCostElectrons() * target);
        playerResourceRepository.save(p);
        playerResourceRepository.save(n);
        playerResourceRepository.save(e);

        if (energyPerAtom != null) {
            BigNum totalEnergyDelta = energyPerAtom.multiply((double) target);
            if (endothermic) {
                BigNum current = new BigNum(energy.getNumber(), energy.getExponent());
                BigNum result = current.subtract(totalEnergyDelta);
                energy.setNumber(result.getNumber());
                energy.setExponent(result.getExponent());
            } else {
                addEnergyRespectingCap(energy, totalEnergyDelta, save.isBrokenInfinity());
            }
            playerResourceRepository.save(energy);
        }

        List<PlayerElement> owned = playerElementRepository.findBySaveId(saveId);
        PlayerElement pe = owned.stream()
                .filter(x -> x.getElement().getId().equals(elementId))
                .findFirst()
                .orElseGet(() -> {
                    PlayerElement created = new PlayerElement();
                    created.setSave(save);
                    created.setElement(element);
                    created.setCount(0);
                    return created;
                });
        pe.setCount(pe.getCount() + target);
        playerElementRepository.save(pe);

        return target;
    }

    /**
     * Скільки атомів дозволяє наявна енергія при заданій ціні за атом.
     * Дзеркалить {@link #wholeAmount(PlayerResource)}: захист від overflow на великих BigNum.
     */
    private long maxAffordableByEnergy(BigNum available, BigNum costPerAtom) {
        if (costPerAtom.getNumber() <= 0) return Long.MAX_VALUE;
        BigNum ratio = available.divide(costPerAtom);
        if (ratio.getExponent() < 0) return 0;
        if (ratio.getExponent() >= 18) return Long.MAX_VALUE;
        double raw = ratio.getNumber() * Math.pow(10, ratio.getExponent());
        if (!Double.isFinite(raw) || raw >= (double) Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) Math.floor(raw);
    }

    /** Додає енергію з екзотермічного синтезу, поважаючи кап 1e308 (як GameEngine.processSave). */
    private void addEnergyRespectingCap(PlayerResource energy, BigNum delta, boolean brokenInfinity) {
        BigNum current = new BigNum(energy.getNumber(), energy.getExponent());
        BigNum result = current.add(delta);
        if (!brokenInfinity && result.getExponent() >= GameEngine.ENERGY_CAP_EXPONENT) {
            energy.setNumber(1.0);
            energy.setExponent(GameEngine.ENERGY_CAP_EXPONENT);
        } else {
            energy.setNumber(result.getNumber());
            energy.setExponent(result.getExponent());
        }
    }

    private boolean previousDiscovered(Long saveId, Element element) {
        return playerElementRepository.findBySaveId(saveId).stream()
                .anyMatch(pe -> pe.getElement().getAtomicNumber() == element.getAtomicNumber() - 1
                        && pe.getCount() > 0);
    }

    /** Скільки атомів гелію (Z=2) синтезовано — "паливо" для запалення зорі. */
    public long heliumCount(Long saveId) {
        return playerElementRepository.findBySaveId(saveId).stream()
                .filter(pe -> pe.getElement().getAtomicNumber() == 2)
                .mapToLong(PlayerElement::getCount)
                .findFirst()
                .orElse(0L);
    }

    /** Чи відкритий зоряний нуклеосинтез (Z&gt;=4) для цього save. */
    public boolean isStellarIgnited(Long saveId) {
        return heliumCount(saveId) >= STELLAR_IGNITION_HELIUM_COUNT;
    }

    /** Чи відкритий r-process синтез важких елементів (Z&gt;26) — гравець пережив Гіпернову. */
    public boolean isHeavyElementSynthesisUnlocked(Long saveId) {
        return saveRepository.findById(saveId)
                .map(Save::getHypernovaCount)
                .orElse(0L) >= HEAVY_ELEMENT_HYPERNOVA_REQUIRED;
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
