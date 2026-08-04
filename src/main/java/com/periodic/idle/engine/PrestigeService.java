package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Скидання прогресу задля кристалів пустоти (Void Crystals, VC).
 * Формула:
 *   log10Energy = exponent + log10(number)
 *   minLog10Energy = PRESTIGE_MIN_LOG10_ENERGY + PRESTIGE_MIN_LOG10_GROWTH * save.prestigeCount (V23, росте з кожним престижем)
 *   якщо log10Energy < minLog10Energy → 0 кристалів
 *   base_log10 = (log10Energy - minLog10Energy) / PRESTIGE_DIVISOR + 1.0
 *   final = base_log10 + log10(CRYSTAL_GAIN multiplier)
 *   кристали = 10^final
 */
@Service
@RequiredArgsConstructor
public class PrestigeService {

    // V17 rebalance: жива бот-симуляція V12-констант (25.0 / 3.0) через реальний
    // застосунок показала, що перша реінкарнація фактично вимагала ~120 БЕЗПЕРЕРВНИХ
    // ігрових днів замість задокументованих 1-3 годин активної гри — поріг був
    // на порядки недосяжний за розумний час навіть із агресивним реінвестуванням.
    // Було 25.0 / 3.0 (до цього — 9.0 / 2.0) — див. docs/balance.md.
    public static final double PRESTIGE_MIN_LOG10_ENERGY = 18.0;
    public static final double PRESTIGE_DIVISOR = 2.5;

    /**
     * V23 (docs/balance.md): поріг престижу тепер РОСТЕ з кожним наступним престижем
     * ({@code effectiveMinLog10Energy = PRESTIGE_MIN_LOG10_ENERGY + PRESTIGE_MIN_LOG10_GROWTH * prestigeCount}),
     * а не лишається фіксованим 18.0. Жива бот-верифікація зниженої CORE-стелі (V23,
     * {@code UpgradeMultipliers}) показала: сама стеля НЕ була коренем "снігової
     * кулі" повторних престижів — навіть з набагато меншим максимальним CORE-бустом
     * (~1e100 замість ~1e280) жадібний бот і далі робив престиж кожні ~2с
     * безперервно (як і в V17/V18), бо фіксований поріг 18.0 тривіально долається
     * БУДЬ-яким ненульовим CORE-бустом після 1-го престижу. Задокументована ціль
     * "кожна наступна реінкарнація вимагає істотно більше" (розділ 1 CLAUDE.md)
     * вимагає саме зростаючого порогу, а не лише капу самого буста — це і є
     * рішення "в" із трьох запропонованих у V17 (§ "Нова знахідка: снігова куля").
     *
     * <p><b>Перший прохід зі значенням 20.0 виявився занадто агресивним</b> (жива
     * перевірка, docs/balance.md): досяжна без CORE-допомоги стеля (як і в V17,
     * ~23-27) опинилась НИЖЧЕ порогу вже 2-го престижу (18+20=38) — і оскільки
     * зниження CORE-стелі (вище) саме позбавило CORE спроможності "пробити" цю
     * різницю грубою силою, 2-й престиж не вдавався жодного разу за 5+ хвилин
     * бот-прогону. Знижено до 5.0, щоб перші кілька престижів лишались досяжними
     * звичайним реінвестуванням, а CORE ставав по-справжньому необхідним лише
     * через кілька циклів, коли поріг переростає базову стелю.
     */
    private static final double PRESTIGE_MIN_LOG10_GROWTH = 5.0;
    /** Стартова енергія після resetу, щоб можна було одразу купити 1-й генератор. */
    public static final double STARTER_ENERGY_NUMBER = 1.0;
    public static final long   STARTER_ENERGY_EXPONENT = 1L; // 10 E

    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final SaveRepository saveRepository;

    public BigNum calcPotentialGain(Long saveId) {
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource energy = findByCode(resources, "E");
        if (energy == null || energy.getNumber() <= 0) return new BigNum(0, 0);

        double minLog10Energy = effectiveMinLog10Energy(saveId);
        double log10Energy = Math.log10(energy.getNumber()) + energy.getExponent();
        if (log10Energy < minLog10Energy) return new BigNum(0, 0);

        List<PlayerUpgrade> upgrades = playerUpgradeRepository.findBySaveId(saveId);
        double crystalMult = calcCrystalGainMultiplier(upgrades)
                * ParticleBonus.electronCrystalMult(resources);

        double baseLog10 = (log10Energy - minLog10Energy) / PRESTIGE_DIVISOR + 1.0;
        double finalLog10 = baseLog10 + Math.log10(crystalMult);

        long exp = (long) Math.floor(finalLog10);
        double num = Math.pow(10, finalLog10 - exp);
        return new BigNum(num, exp);
    }

    /**
     * Поріг `log10(E)` для НАСТУПНОГО престижу цього save — росте лінійно з кожним
     * уже виконаним престижем (розділ вище). Публічний, щоб UI (`/api/prestige-info`)
     * міг показати гравцеві актуальну вимогу, а не застарілу константу.
     */
    public double effectiveMinLog10Energy(Long saveId) {
        long prestigeCount = saveRepository.findById(saveId).map(Save::getPrestigeCount).orElse(0L);
        return PRESTIGE_MIN_LOG10_ENERGY + PRESTIGE_MIN_LOG10_GROWTH * prestigeCount;
    }

    @Transactional
    public BigNum prestige(Long saveId) {
        BigNum gain = calcPotentialGain(saveId);
        if (gain.getNumber() <= 0 && gain.getExponent() <= 0) {
            throw new RuntimeException("Not enough energy for prestige");
        }

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource energy = findByCode(resources, "E");
        PlayerResource crystals = findByCode(resources, "VC");
        if (energy == null || crystals == null) {
            throw new RuntimeException("Resources E/VC missing");
        }

        // Скидаємо енергію до стартової (щоб 1-й генератор вже був доступний)
        energy.setNumber(STARTER_ENERGY_NUMBER);
        energy.setExponent(STARTER_ENERGY_EXPONENT);
        playerResourceRepository.save(energy);

        // Додаємо кристали
        BigNum currentVc = new BigNum(crystals.getNumber(), crystals.getExponent());
        BigNum newVc = currentVc.add(gain);
        crystals.setNumber(newVc.getNumber());
        crystals.setExponent(newVc.getExponent());
        playerResourceRepository.save(crystals);

        // Скидаємо рівні генераторів (апдейти лишаємо)
        List<PlayerGenerator> gens = playerGeneratorRepository.findBySaveId(saveId);
        for (PlayerGenerator pg : gens) {
            pg.setLevel(0);
        }
        playerGeneratorRepository.saveAll(gens);

        saveRepository.findById(saveId).ifPresent(save -> {
            save.setPrestigeCount(save.getPrestigeCount() + 1);
            saveRepository.save(save);
        });

        return gain;
    }

    /**
     * Повний скид збереження: енергія → стартова, VC → 0, усі рівні генераторів
     * та апгрейдів → 0. Використовується кнопкою "Скинути" в налаштуваннях.
     */
    @Transactional
    public void hardReset(Long saveId) {
        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource energy = findByCode(resources, "E");
        PlayerResource crystals = findByCode(resources, "VC");
        if (energy == null) {
            throw new RuntimeException("Resource E missing");
        }

        energy.setNumber(STARTER_ENERGY_NUMBER);
        energy.setExponent(STARTER_ENERGY_EXPONENT);
        playerResourceRepository.save(energy);

        if (crystals != null) {
            crystals.setNumber(0);
            crystals.setExponent(0);
            playerResourceRepository.save(crystals);
        }

        List<PlayerGenerator> gens = playerGeneratorRepository.findBySaveId(saveId);
        for (PlayerGenerator pg : gens) {
            pg.setLevel(0);
        }
        playerGeneratorRepository.saveAll(gens);

        List<PlayerUpgrade> upgrades = playerUpgradeRepository.findBySaveId(saveId);
        for (PlayerUpgrade pu : upgrades) {
            pu.setLevel(0);
        }
        playerUpgradeRepository.saveAll(upgrades);

        // Скидаємо матерію (p/n/e) і прапори Тіру 1.
        for (PlayerResource pr : resources) {
            if (pr.getResource() == null) continue;
            String code = pr.getResource().getCode();
            if ("p".equals(code) || "n".equals(code) || "e".equals(code)) {
                pr.setNumber(0);
                pr.setExponent(0);
                playerResourceRepository.save(pr);
            }
        }
        saveRepository.findById(saveId).ifPresent(save -> {
            save.setBrokenInfinity(false);
            save.setMatterCollapses(0L);
            save.setPrestigeCount(0L);
            saveRepository.save(save);
        });
    }

    private double calcCrystalGainMultiplier(List<PlayerUpgrade> upgrades) {
        double mult = 1.0;
        for (PlayerUpgrade pu : upgrades) {
            if (pu.getLevel() <= 0) continue;
            if ("CRYSTAL_GAIN".equals(pu.getUpgrade().getEffectType())) {
                mult += pu.getUpgrade().getEffectValue() * pu.getLevel();
            }
        }
        return mult;
    }

    private PlayerResource findByCode(List<PlayerResource> resources, String code) {
        return resources.stream()
                .filter(r -> r.getResource() != null && code.equals(r.getResource().getCode()))
                .findFirst()
                .orElse(null);
    }
}
