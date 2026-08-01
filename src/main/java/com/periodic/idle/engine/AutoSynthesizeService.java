package com.periodic.idle.engine;

import com.periodic.idle.content.Element;
import com.periodic.idle.content.ElementRepository;
import com.periodic.idle.content.Molecule;
import com.periodic.idle.content.MoleculeRepository;
import com.periodic.idle.player.Save;
import com.periodic.idle.player.SaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Автосинтез елементів (Тір 2) і молекул (Тір 3): щоразу намагається зібрати
 * максимум (amount=-1) кожного контенту, що вже наявний у грі — точно так само,
 * як AutoBuyService намагається купити кожен покритий генератор. Player-driven
 * toggle ({@link Save#isAutoSynthesizeEnabled()}), а не апгрейд — обидва тіри
 * ще не мають власного дерева апгрейдів.
 *
 * <p>Помилки окремого елемента/молекули (недостатньо частинок/атомів/енергії,
 * послідовна прогресія, "запалена зоря") просто пропускаються — це не збій,
 * а звичайний стан "ще не готово", як і при ручному кліку.
 */
@Service
@RequiredArgsConstructor
public class AutoSynthesizeService {

    /** Рідше за game tick — синтез не такий чутливий до затримки, як генератори. */
    private static final long AUTO_SYNTHESIZE_INTERVAL_MS = 1000;

    private final SaveRepository saveRepository;
    private final ElementRepository elementRepository;
    private final MoleculeRepository moleculeRepository;
    private final SynthesisService synthesisService;
    private final MoleculeService moleculeService;

    @Scheduled(fixedRate = AUTO_SYNTHESIZE_INTERVAL_MS)
    @Transactional
    public void tickAutoSynthesize() {
        for (Save save : saveRepository.findAll()) {
            if (!save.isAutoSynthesizeEnabled()) continue;
            processSave(save.getId());
        }
    }

    void processSave(Long saveId) {
        List<Element> elements = elementRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Element::getAtomicNumber))
                .toList();
        for (Element el : elements) {
            try {
                synthesisService.synthesizeBulk(saveId, el.getId(), -1);
            } catch (RuntimeException ignored) {
                // недостатньо частинок/енергії, попередній елемент не відкрито,
                // зоря ще не запалена тощо — просто пропускаємо цей елемент цього тіку
            }
        }

        List<Molecule> molecules = moleculeRepository.findAll();
        for (Molecule m : molecules) {
            try {
                moleculeService.synthesizeBulk(saveId, m.getId(), -1);
            } catch (RuntimeException ignored) {
                // бракує якогось атома рецепту — пропускаємо
            }
        }
    }
}
