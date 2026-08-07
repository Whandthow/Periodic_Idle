package com.periodic.idle.web;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.Element;
import com.periodic.idle.content.ElementRepository;
import com.periodic.idle.content.Molecule;
import com.periodic.idle.content.MoleculeRepository;
import com.periodic.idle.content.TierUnlockCondition;
import com.periodic.idle.content.TierUnlockConditionRepository;
import com.periodic.idle.engine.GameEngine;
import com.periodic.idle.player.PlayerElement;
import com.periodic.idle.player.PlayerElementRepository;
import com.periodic.idle.player.PlayerMolecule;
import com.periodic.idle.player.PlayerMoleculeRepository;
import com.periodic.idle.player.PlayerResource;
import com.periodic.idle.player.PlayerResourceRepository;
import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Dev-інструменти для швидкого тестування гри.
 * Ці ендпоінти не для продакшну: вони напряму змінюють стан.
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevController {

    private final GameEngine gameEngine;
    private final PlayerResourceRepository playerResourceRepository;
    private final TierUnlockConditionRepository tierUnlockConditionRepository;
    private final SaveRepository saveRepository;
    private final ElementRepository elementRepository;
    private final MoleculeRepository moleculeRepository;
    private final PlayerElementRepository playerElementRepository;
    private final PlayerMoleculeRepository playerMoleculeRepository;

    @PostMapping("/tick-speed")
    public Map<String, Object> setTickSpeed(@RequestBody Map<String, Object> body) {
        double multiplier = ((Number) body.getOrDefault("multiplier", 1.0)).doubleValue();
        gameEngine.setTickSpeedMultiplier(multiplier);
        return Map.of("status", "ok", "tickSpeedMultiplier", gameEngine.getTickSpeedMultiplier());
    }

    @GetMapping("/tick-speed")
    public Map<String, Object> getTickSpeed() {
        return Map.of("tickSpeedMultiplier", gameEngine.getTickSpeedMultiplier());
    }

    /**
     * Додає приріст до експоненти ресурсу (body: {saveId, resourceCode, delta}).
     * resourceCode — наприклад "E" або "VC". delta — ціле число.
     */
    @PostMapping("/add-exp")
    @Transactional
    public Map<String, Object> addExponent(@RequestBody Map<String, Object> body) {
        Long saveId = ((Number) body.get("saveId")).longValue();
        String code = String.valueOf(body.get("resourceCode"));
        long delta = ((Number) body.getOrDefault("delta", 1)).longValue();

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource pr = resources.stream()
                .filter(r -> r.getResource() != null && code.equals(r.getResource().getCode()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Resource not found: " + code));

        // Якщо ресурсу зовсім нема — виставляємо базове значення 1 перед підйомом експоненти.
        if (pr.getNumber() <= 0) {
            pr.setNumber(1.0);
            pr.setExponent(0);
        }
        pr.setExponent(pr.getExponent() + delta);
        playerResourceRepository.save(pr);
        return Map.of("status", "ok",
                "resource", code,
                "number", pr.getNumber(),
                "exponent", pr.getExponent());
    }

    /**
     * Перестрибнути одразу на тір: видає МІНІМАЛЬНИЙ прогрес, що задовольняє
     * найлегшу з його OR-умов розблокування ({@code tier_unlock_conditions}),
     * а не довільну купу ресурсів — щоб дев-стрибок так само проходив через
     * той самий data-driven механізм, що й звичайна прогресія (розділ 10 CLAUDE.md).
     * Тір 0 не має умов — no-op. Body: {saveId, tier}.
     */
    @PostMapping("/jump-tier")
    @Transactional
    public Map<String, Object> jumpToTier(@RequestBody Map<String, Object> body) {
        Long saveId = ((Number) body.get("saveId")).longValue();
        int tier = ((Number) body.get("tier")).intValue();

        List<TierUnlockCondition> conditions = tierUnlockConditionRepository.findAll().stream()
                .filter(c -> c.getTier() == tier)
                .toList();
        if (conditions.isEmpty()) {
            return Map.of("status", "ok", "granted", false, "reason", "Тір не має умов розблокування");
        }

        // Найлегша OR-умова: найменший поріг — найдешевше видати.
        TierUnlockCondition easiest = conditions.stream()
                .min(Comparator.comparingDouble(TierUnlockCondition::getMinLog10))
                .orElseThrow();

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource pr = resources.stream()
                .filter(r -> r.getResource() != null
                        && r.getResource().getId().equals(easiest.getResource().getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Ресурс не ініціалізовано: " + easiest.getResource().getCode()));

        long exponent = (long) Math.ceil(Math.max(easiest.getMinLog10(), 0.0));
        pr.setNumber(1.0);
        pr.setExponent(exponent);
        playerResourceRepository.save(pr);

        return Map.of("status", "ok",
                "granted", true,
                "resource", pr.getResource().getCode(),
                "exponent", exponent);
    }

    /**
     * Видає точну (лінійну, не показникову) кількість частинки — "p"/"n"/"e".
     * На відміну від {@link #addExponent}, тут кількість, яку зручно задавати
     * гравцю руками (напр. +500 протонів), а не порядок величини.
     * Body: {saveId, resourceCode, amount}.
     */
    @PostMapping("/grant-resource")
    @Transactional
    public Map<String, Object> grantResource(@RequestBody Map<String, Object> body) {
        Long saveId = ((Number) body.get("saveId")).longValue();
        String code = String.valueOf(body.get("resourceCode"));
        long amount = ((Number) body.getOrDefault("amount", 0)).longValue();
        if (amount <= 0) throw new RuntimeException("Кількість має бути додатною");

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        PlayerResource pr = resources.stream()
                .filter(r -> r.getResource() != null && code.equals(r.getResource().getCode()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Resource not found: " + code));

        BigNum result = new BigNum(pr.getNumber(), pr.getExponent()).add(new BigNum((double) amount, 0));
        pr.setNumber(result.getNumber());
        pr.setExponent(result.getExponent());
        playerResourceRepository.save(pr);

        return Map.of("status", "ok", "resource", code, "number", pr.getNumber(), "exponent", pr.getExponent());
    }

    /** Видає точну кількість уже синтезованих атомів обраного елемента. Body: {saveId, elementId, amount}. */
    @PostMapping("/grant-element")
    @Transactional
    public Map<String, Object> grantElement(@RequestBody Map<String, Object> body) {
        Long saveId = ((Number) body.get("saveId")).longValue();
        Long elementId = ((Number) body.get("elementId")).longValue();
        long amount = ((Number) body.getOrDefault("amount", 0)).longValue();
        if (amount <= 0) throw new RuntimeException("Кількість має бути додатною");

        Save save = saveRepository.findById(saveId).orElseThrow(() -> new RuntimeException("Save not found"));
        Element element = elementRepository.findById(elementId).orElseThrow(() -> new RuntimeException("Element not found"));

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
        pe.setCount(pe.getCount() + amount);
        playerElementRepository.save(pe);

        return Map.of("status", "ok", "element", element.getSymbol(), "count", pe.getCount());
    }

    /** Видає точну кількість уже зібраних молекул. Body: {saveId, moleculeId, amount}. */
    @PostMapping("/grant-molecule")
    @Transactional
    public Map<String, Object> grantMolecule(@RequestBody Map<String, Object> body) {
        Long saveId = ((Number) body.get("saveId")).longValue();
        Long moleculeId = ((Number) body.get("moleculeId")).longValue();
        long amount = ((Number) body.getOrDefault("amount", 0)).longValue();
        if (amount <= 0) throw new RuntimeException("Кількість має бути додатною");

        Save save = saveRepository.findById(saveId).orElseThrow(() -> new RuntimeException("Save not found"));
        Molecule molecule = moleculeRepository.findById(moleculeId).orElseThrow(() -> new RuntimeException("Molecule not found"));

        List<PlayerMolecule> owned = playerMoleculeRepository.findBySaveId(saveId);
        PlayerMolecule pm = owned.stream()
                .filter(x -> x.getMolecule().getId().equals(moleculeId))
                .findFirst()
                .orElseGet(() -> {
                    PlayerMolecule created = new PlayerMolecule();
                    created.setSave(save);
                    created.setMolecule(molecule);
                    created.setCount(0);
                    return created;
                });
        pm.setCount(pm.getCount() + amount);
        playerMoleculeRepository.save(pm);

        return Map.of("status", "ok", "molecule", molecule.getFormula(), "count", pm.getCount());
    }
}
