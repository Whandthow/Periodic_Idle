package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.common.BindingEnergy;
import com.periodic.idle.content.Star;
import com.periodic.idle.content.StarRepository;
import com.periodic.idle.engine.config.GameEngineProperties;
import com.periodic.idle.engine.config.StarProperties;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Тір 4: зорі головної послідовності. На відміну від {@link SynthesisService}
 * (миттєвий синтез), зоря — пасивний процес: споживає вже синтезований Гідроген
 * (Тір 2) і виробляє Гелій з часом, як генератор Тіру 0 для E, але для атомів.
 *
 * <p>Наукова концепція (CLAUDE.md, розділ 1): реальний proton-proton chain
 * (4 ¹H -> ⁴He + 2e⁺ + 2ν) — той самий {@link BindingEnergy#totalMeV} для He-4,
 * що вже рахує Тір 2, тепер вивільняється поступово, а не миттєво.
 *
 * <p><b>Ризик (гіпернова):</b> якщо Гідрогену не вистачає на черговий тік —
 * зоря колапсує катастрофічно: усі {@code player_elements}/{@code player_molecules}
 * цього save обнуляються (і сама зоря гасне, level -> 0). Свідома "довгограйна"
 * механіка ризику: тримати паливо (синтез/автосинтез Гідрогену) вище темпу
 * споживання зорі, інакше втрачаєш весь атомний прогрес.
 */
@Service
@RequiredArgsConstructor
public class StarService {

    private final StarProperties props;
    private final GameEngineProperties gameEngineProperties;
    private final ElementBonus elementBonus;

    private final StarRepository starRepository;
    private final PlayerStarRepository playerStarRepository;
    private final PlayerElementRepository playerElementRepository;
    private final PlayerMoleculeRepository playerMoleculeRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final SaveRepository saveRepository;

    /**
     * Купити до {@code amount} рівнів зорі за атоми Гідрогену (Тір 2).
     * amount &lt; 0 = максимум за наявний Гідроген. Повертає фактично куплену кількість.
     */
    @Transactional
    public int buyLevel(Long saveId, Long starId, int amount) {
        if (amount == 0) return 0;

        Star star = starRepository.findById(starId)
                .orElseThrow(() -> new RuntimeException("Star not found"));

        List<PlayerElement> elements = playerElementRepository.findBySaveId(saveId);
        PlayerElement hydrogen = findByAtomicNumber(elements, props.hydrogenAtomicNumber());
        long available = hydrogen == null ? 0 : hydrogen.getCount();

        PlayerStar ps = playerStarRepository.findBySaveId(saveId).stream()
                .filter(x -> x.getStar().getId().equals(starId))
                .findFirst()
                .orElse(null);
        int currentLevel = ps != null ? ps.getLevel() : 0;

        int target = amount < 0 ? Integer.MAX_VALUE : amount;
        int bought = 0;
        int level = currentLevel;
        long spent = 0;
        while (bought < target) {
            long cost = (long) Math.ceil(star.getBaseCostHydrogen() * Math.pow(star.getCostMultiplier(), level));
            if (spent + cost > available) break;
            spent += cost;
            level++;
            bought++;
        }

        if (bought == 0) {
            if (amount < 0) return 0;
            throw new RuntimeException("Not enough hydrogen");
        }

        hydrogen.setCount(available - spent);
        playerElementRepository.save(hydrogen);

        if (ps == null) {
            ps = new PlayerStar();
            ps.setSave(saveRepository.findById(saveId)
                    .orElseThrow(() -> new RuntimeException("Save not found")));
            ps.setStar(star);
        }
        ps.setLevel(level);
        playerStarRepository.save(ps);

        return bought;
    }

    /**
     * НЕ {@code @Transactional} тут навмисно (розділ 5.2 CLAUDE.md — той самий
     * структурний ризик, що й AutoBuyService/AutoSynthesizeService): кожен
     * {@code processStarTick} — власна незалежна транзакція, тож гіпернова чи
     * будь-яка інша помилка на одній зорі/save не відкочує решту.
     */
    @Scheduled(fixedRateString = "${balance.star.tick-interval-ms}")
    public void tick() {
        for (Save save : saveRepository.findAll()) {
            for (PlayerStar ps : playerStarRepository.findBySaveId(save.getId())) {
                if (ps.getLevel() <= 0) continue;
                try {
                    processStarTick(save.getId(), ps.getId());
                } catch (RuntimeException ignored) {
                    // одна зоря не повинна ламати обробку інших saves/зірок цього тіку
                }
            }
        }
    }

    @Transactional
    void processStarTick(Long saveId, Long playerStarId) {
        PlayerStar ps = playerStarRepository.findById(playerStarId).orElse(null);
        if (ps == null || ps.getLevel() <= 0) return;
        Star star = ps.getStar();

        List<PlayerElement> elements = playerElementRepository.findBySaveId(saveId);
        // CNO-каталіз (реальна астрофізика, ElementBonus): щойно синтезовано C+N+O,
        // вони каталізують протонний синтез не витрачаючись самі — зоря пропускає
        // пропорційно більше подій за секунду (і споживає, і виробляє більше).
        double cnoMult = elementBonus.cnoCatalystMult(elements);
        double desiredEvents = star.getEventsPerSecPerLevel() * ps.getLevel() * props.tickIntervalSec() * cnoMult;
        long requiredHydrogen = (long) Math.ceil(desiredEvents * star.getFuelHPerEvent());

        PlayerElement hydrogen = findByAtomicNumber(elements, props.hydrogenAtomicNumber());
        long hAvailable = hydrogen == null ? 0 : hydrogen.getCount();

        if (hAvailable < requiredHydrogen) {
            triggerHypernova(saveId);
            return;
        }

        hydrogen.setCount(hAvailable - requiredHydrogen);
        playerElementRepository.save(hydrogen);

        PlayerElement helium = findByAtomicNumber(elements, props.heliumAtomicNumber());
        if (helium == null) {
            throw new RuntimeException("Helium element not initialized");
        }
        long producedHelium = (long) (desiredEvents * star.getOutputHePerEvent());
        helium.setCount(helium.getCount() + producedHelium);
        playerElementRepository.save(helium);

        double meVReleased = desiredEvents * BindingEnergy.totalMeV(props.heliumAtomicNumber(), 4);
        if (meVReleased > 0) {
            Save save = saveRepository.findById(saveId).orElseThrow(() -> new RuntimeException("Save not found"));
            PlayerResource energy = playerResourceRepository.findBySaveId(saveId).stream()
                    .filter(r -> r.getResource() != null && "E".equals(r.getResource().getCode()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Resource E missing"));
            BigNum delta = new BigNum(meVReleased, props.energyScaleExponent());
            addEnergyRespectingCap(energy, delta, save.isBrokenInfinity());
            playerResourceRepository.save(energy);
        }
    }

    /**
     * Гіпернова: катастрофічний колапс зорі через нестачу палива. Обнуляє геть усі
     * синтезовані атоми й молекули цього save (і гасить усі зорі гравця) — ризик-плата
     * за неувагу до балансу видобутку/споживання Гідрогену. Лічильник ніколи не скидається.
     */
    private void triggerHypernova(Long saveId) {
        List<PlayerElement> elements = playerElementRepository.findBySaveId(saveId);
        for (PlayerElement pe : elements) pe.setCount(0);
        playerElementRepository.saveAll(elements);

        List<PlayerMolecule> molecules = playerMoleculeRepository.findBySaveId(saveId);
        for (PlayerMolecule pm : molecules) pm.setCount(0);
        playerMoleculeRepository.saveAll(molecules);

        List<PlayerStar> stars = playerStarRepository.findBySaveId(saveId);
        for (PlayerStar s : stars) s.setLevel(0);
        playerStarRepository.saveAll(stars);

        Save save = saveRepository.findById(saveId).orElseThrow(() -> new RuntimeException("Save not found"));
        save.setHypernovaCount(save.getHypernovaCount() + 1);
        saveRepository.save(save);
    }

    private void addEnergyRespectingCap(PlayerResource energy, BigNum delta, boolean brokenInfinity) {
        BigNum current = new BigNum(energy.getNumber(), energy.getExponent());
        BigNum result = current.add(delta);
        if (!brokenInfinity && result.getExponent() >= gameEngineProperties.energyCapExponent()) {
            energy.setNumber(1.0);
            energy.setExponent(gameEngineProperties.energyCapExponent());
        } else {
            energy.setNumber(result.getNumber());
            energy.setExponent(result.getExponent());
        }
    }

    private PlayerElement findByAtomicNumber(List<PlayerElement> elements, int atomicNumber) {
        return elements.stream()
                .filter(pe -> pe.getElement().getAtomicNumber() == atomicNumber)
                .findFirst()
                .orElse(null);
    }
}
