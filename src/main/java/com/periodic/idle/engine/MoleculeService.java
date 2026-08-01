package com.periodic.idle.engine;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.content.Molecule;
import com.periodic.idle.content.MoleculeComponent;
import com.periodic.idle.content.MoleculeRepository;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Тір 3: молекули, зібрані з уже синтезованих атомів (хімічні зв'язки).
 *
 * <p>Наукова концепція (CLAUDE.md, розділ 1): формування молекули з вільних атомів
 * завжди екзотермічне — вивільняє енергію хімічного зв'язку (на відміну від ядерного
 * синтезу в {@link SynthesisService}, де є крос-овер точка на залізі-56). Енергія
 * зв'язку задається в еВ ({@link Molecule#getBondEnergyEv()} — реальні довідникові
 * значення) і переводиться в МеВ (÷1e6), використовуючи ту саму шкалу
 * {@code BigNum(meV, ENERGY_SCALE_EXPONENT)}, що й ядерна фізика — так у грі природно
 * відтворюється реальний розрив на ~6 порядків між хімічною і ядерною енергією.
 */
@Service
@RequiredArgsConstructor
public class MoleculeService {

    /** Жорсткий запобіжник нескінченного циклу при буст-синтезі. */
    private static final long BULK_HARD_CAP = 100_000L;

    private static final double EV_PER_MEV = 1_000_000.0;

    /** Той самий масштаб, що й SynthesisService.ENERGY_SCALE_EXPONENT — єдина шкала енергії для гри. */
    private static final long ENERGY_SCALE_EXPONENT = 298L;

    private final MoleculeRepository moleculeRepository;
    private final PlayerMoleculeRepository playerMoleculeRepository;
    private final PlayerElementRepository playerElementRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final SaveRepository saveRepository;

    /**
     * Зібрати до {@code amount} молекул (amount &lt; 0 = максимум за наявні атоми).
     * Повертає фактичну зібрану кількість.
     */
    @Transactional
    public long synthesizeBulk(Long saveId, Long moleculeId, long amount) {
        if (amount == 0) return 0;

        Molecule molecule = moleculeRepository.findById(moleculeId)
                .orElseThrow(() -> new RuntimeException("Molecule not found"));
        List<MoleculeComponent> recipe = molecule.getComponents();
        if (recipe == null || recipe.isEmpty()) {
            throw new RuntimeException("Molecule has no recipe");
        }

        Map<Long, PlayerElement> ownedByElementId = playerElementRepository.findBySaveId(saveId).stream()
                .collect(Collectors.toMap(pe -> pe.getElement().getId(), pe -> pe, (a, b) -> a));

        long maxAffordable = Long.MAX_VALUE;
        for (MoleculeComponent c : recipe) {
            PlayerElement pe = ownedByElementId.get(c.getElement().getId());
            long available = pe == null ? 0 : pe.getCount();
            if (available <= 0) {
                if (amount < 0) return 0;
                throw new RuntimeException("Спочатку синтезуйте " + c.getElement().getSymbol()
                        + " у періодичній таблиці");
            }
            long maxByThis = c.getAtomCount() <= 0 ? Long.MAX_VALUE : available / c.getAtomCount();
            maxAffordable = Math.min(maxAffordable, maxByThis);
        }

        long target = amount < 0
                ? Math.min(maxAffordable, BULK_HARD_CAP)
                : Math.min(Math.min(amount, maxAffordable), BULK_HARD_CAP);

        if (target <= 0) {
            if (amount < 0) return 0;
            throw new RuntimeException("Not enough atoms");
        }

        Save save = saveRepository.findById(saveId)
                .orElseThrow(() -> new RuntimeException("Save not found"));

        for (MoleculeComponent c : recipe) {
            PlayerElement pe = ownedByElementId.get(c.getElement().getId());
            pe.setCount(pe.getCount() - c.getAtomCount() * target);
            playerElementRepository.save(pe);
        }

        double meVPerMolecule = molecule.getBondEnergyEv() / EV_PER_MEV;
        if (meVPerMolecule > 0) {
            PlayerResource energy = playerResourceRepository.findBySaveId(saveId).stream()
                    .filter(r -> r.getResource() != null && "E".equals(r.getResource().getCode()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Resource E missing"));
            BigNum delta = new BigNum(meVPerMolecule, ENERGY_SCALE_EXPONENT).multiply((double) target);
            addEnergyRespectingCap(energy, delta, save.isBrokenInfinity());
            playerResourceRepository.save(energy);
        }

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
        pm.setCount(pm.getCount() + target);
        playerMoleculeRepository.save(pm);

        return target;
    }

    /** Чи має гравець достатньо кожного атома рецепту хоча б на 1 молекулу. */
    public boolean canAffordAtLeastOne(Long saveId, Molecule molecule) {
        Map<Long, PlayerElement> ownedByElementId = playerElementRepository.findBySaveId(saveId).stream()
                .collect(Collectors.toMap(pe -> pe.getElement().getId(), pe -> pe, (a, b) -> a));
        List<MoleculeComponent> recipe = molecule.getComponents();
        if (recipe == null || recipe.isEmpty()) return false;
        return recipe.stream().allMatch(c -> {
            PlayerElement pe = ownedByElementId.get(c.getElement().getId());
            return pe != null && pe.getCount() >= c.getAtomCount();
        });
    }

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
}
