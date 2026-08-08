package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.engine.config.GameEngineProperties;
import com.periodic.idle.engine.config.MatterProperties;
import com.periodic.idle.engine.config.PrestigeProperties;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Тір 1: "Колапс матерії" (повторюваний обмін: e308 енергії -> +1 частинка + скид Тіру 0)
 * і одноразовий "Break Infinity" (знімає кап 1e308 після достатньої кількості колапсів).
 */
@Service
@RequiredArgsConstructor
public class MatterService {

    private static final Set<String> VALID_PARTICLES = Set.of("p", "n", "e");

    private final MatterProperties props;
    private final GameEngineProperties gameEngineProperties;
    private final PrestigeProperties prestigeProperties;
    private final CollapseCycleBonus collapseCycleBonus;

    private final SaveRepository saveRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerGeneratorRepository playerGeneratorRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;

    /** Скільки колапсів матерії потрібно накопичити, щоб відкрити Break Infinity. */
    public long breakInfinityRequired() {
        return props.breakInfinityRequired();
    }

    @Transactional
    public void collapse(Long saveId, String particle) {
        if (particle == null || !VALID_PARTICLES.contains(particle)) {
            throw new RuntimeException("Unknown particle: " + particle);
        }
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource energy = findByCode(resources, "E");
        if (energy == null) {
            throw new RuntimeException("Resource E missing");
        }
        double log10Energy = energy.getNumber() > 0
                ? Math.log10(energy.getNumber()) + energy.getExponent()
                : 0.0;
        if (log10Energy < gameEngineProperties.energyCapExponent()) {
            throw new RuntimeException("Потрібно 1e308 енергії");
        }

        // Повний ресет Тіру 0: енергія -> стартова, рівні генераторів і апгрейдів -> 0,
        // Кристали Пустоти -> 0. Єдине, що переживає колапс, — самі частинки (p/n/e):
        // саме вони тепер єдина "вічна" валюта прогресу Тіру 1 (ParticleBonus, розділ 7.1).
        // Без цього Ядро (CORE), яке масштабується від log10(VC), могло необмежено
        // накопичуватись між колапсами й переповнювати double у GameEngine.calcCoreBoost.
        energy.setNumber(prestigeProperties.starterEnergyNumber());
        energy.setExponent(prestigeProperties.starterEnergyExponent());
        playerResourceRepository.save(energy);

        List<PlayerGenerator> gens = playerGeneratorRepository.findBySaveId(saveId);
        for (PlayerGenerator pg : gens) {
            pg.setLevel(0);
        }
        playerGeneratorRepository.saveAll(gens);

        // AUTOBUY-апгрейд (покриття автокупівлі генераторів) навмисно НЕ скидається —
        // інакше кожен колапс глушив би AutoBuyService до ручної повторної покупки,
        // хоча сама автокупівля — це наскрізна зручність, а не прогрес Тіру 0, який
        // колапс і покликаний обнуляти.
        List<PlayerUpgrade> upgrades = playerUpgradeRepository.findBySaveId(saveId);
        for (PlayerUpgrade pu : upgrades) {
            if ("AUTOBUY".equals(pu.getUpgrade().getEffectType())) continue;
            pu.setLevel(0);
        }
        playerUpgradeRepository.saveAll(upgrades);

        // Кристали Пустоти скидаються так само, як апгрейди й генератори — АЛЕ лише
        // поки не накопичено CollapseCycleBonus.VC_PERSISTS_AFTER_COLLAPSES (10 000)
        // колапсів: після цього гравець "довів" накопичений цикл-досвід і отримує
        // право на постійний VC/CORE-снігова-ком (без цього повторний колапс матерії
        // практично недосяжний без VC — жива бот-симуляція, docs/balance.md V17/V18;
        // CollapseCycleBonus — незалежний від VC місток до цього моменту).
        if (save.getMatterCollapses() < collapseCycleBonus.vcPersistsAfterCollapses()) {
            PlayerResource crystals = findByCode(resources, "VC");
            if (crystals != null) {
                crystals.setNumber(0);
                crystals.setExponent(0);
                playerResourceRepository.save(crystals);
            }
        }

        // +1 частинка обраного типу.
        PlayerResource target = findByCode(resources, particle);
        if (target == null) {
            throw new RuntimeException("Particle resource not initialized: " + particle);
        }
        BigNum result = new BigNum(target.getNumber(), target.getExponent()).add(new BigNum(1, 0));
        target.setNumber(result.getNumber());
        target.setExponent(result.getExponent());
        playerResourceRepository.save(target);

        save.setMatterCollapses(save.getMatterCollapses() + 1);
        saveRepository.save(save);
    }

    @Transactional
    public void breakInfinity(Long saveId) {
        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));
        if (save.isBrokenInfinity()) return;
        if (save.getMatterCollapses() < props.breakInfinityRequired()) {
            long remaining = props.breakInfinityRequired() - save.getMatterCollapses();
            throw new RuntimeException("Потрібно ще " + remaining + " колапсів матерії");
        }
        save.setBrokenInfinity(true);
        saveRepository.save(save);
    }

    private PlayerResource findByCode(List<PlayerResource> resources, String code) {
        return resources.stream()
                .filter(r -> r.getResource() != null && code.equals(r.getResource().getCode()))
                .findFirst()
                .orElse(null);
    }
}
