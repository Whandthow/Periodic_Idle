package com.periodic.idle.engine;

import com.periodic.idle.engine.config.ElementBonusProperties;
import com.periodic.idle.player.PlayerElement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Пасивні бонуси Тіру 2 (синтезовані атоми) до виробництва Тіру 0/1 — той самий
 * принцип, що й {@link ParticleBonus} (частинки Тіру 1 → Тір 0), лише вхід тепер
 * елементи. Три незалежні джерела:
 *
 * <ul>
 *   <li>{@link #diversityMult} — за кількість РІЗНИХ синтезованих елементів
 *       (не сирих атомів, щоб не можна було нафармити одним дешевим елементом);
 *       крива насичення, як у {@link ParticleBonus}.</li>
 *   <li>{@link #atomCountMult} — за сумарну кількість атомів усіх елементів разом,
 *       двоступенева tanh-крива (лінійно в лог-масштабі до softcap, далі софт-декеле­рація,
 *       потім жорстка асимптота-стеля) — "софт кап при великій кількості, хард кап
 *       при нереальній".</li>
 *   <li>{@link #cnoCatalystMult} — реальна астрофізика: щойно синтезовано Карбон,
 *       Нітроген і Оксиген (Z=6,7,8), вони каталізують протонний синтез у зорі
 *       (CNO-цикл), не витрачаючись самі — дискретний множник пропускної
 *       здатності {@link StarService}, не крива насичення.</li>
 * </ul>
 *
 * <p>Коефіцієнти — {@link ElementBonusProperties} ({@code balance.element-bonus.*}
 * у application.yml), не Java-константи.
 */
@Component
@RequiredArgsConstructor
public class ElementBonus {

    private final ElementBonusProperties props;

    /** "Ефективна" кількість після насичення: x / (1 + x/SCALE). */
    private double saturating(double x, double scale) {
        return x / (1.0 + x / scale);
    }

    /** Кількість різних елементів, синтезованих хоча б раз (count > 0). */
    public long distinctCount(List<PlayerElement> elements) {
        return elements.stream().filter(pe -> pe.getCount() > 0).count();
    }

    /** Сумарна кількість атомів усіх елементів разом (захист від переповнення long). */
    public long totalAtomCount(List<PlayerElement> elements) {
        long total = 0L;
        for (PlayerElement pe : elements) {
            long c = pe.getCount();
            if (c <= 0) continue;
            long next = total + c;
            if (next < total) return Long.MAX_VALUE; // overflow -> вже й так "нереальна кількість"
            total = next;
        }
        return total;
    }

    /** Множник енергії від різноманіття синтезованих елементів: 1 + saturating(distinct) * diversityPer. */
    public double diversityMult(List<PlayerElement> elements) {
        long distinct = distinctCount(elements);
        if (distinct <= 0) return 1.0;
        return 1.0 + saturating(distinct, props.diversitySaturationScale()) * props.diversityPer();
    }

    /**
     * Множник енергії від сумарної кількості атомів: двоступенева softcap/hardcap-крива
     * над log10(totalAtoms) — див. клас-докстрінг.
     */
    public double atomCountMult(List<PlayerElement> elements) {
        long total = totalAtomCount(elements);
        if (total <= 0) return 1.0;

        double x = Math.log10((double) total + 1.0);
        if (!Double.isFinite(x) || x <= 0) return 1.0;

        double effectiveX = x;
        if (x > props.atomSoftcapLog10()) {
            double excess = x - props.atomSoftcapLog10();
            effectiveX = props.atomSoftcapLog10() + props.atomHardRangeLog10() * Math.tanh(excess / props.atomHardRangeLog10());
        }
        double result = 1.0 + effectiveX * props.atomCountPer();
        return Double.isFinite(result) ? result : 1.0;
    }

    /** Чи синтезовано елемент із заданим атомним номером хоча б раз. */
    private boolean hasElement(List<PlayerElement> elements, int atomicNumber) {
        return elements.stream()
                .anyMatch(pe -> pe.getElement() != null
                        && pe.getElement().getAtomicNumber() == atomicNumber
                        && pe.getCount() > 0);
    }

    /**
     * Множник пропускної здатності зоряного синтезу від CNO-каталізу: 1.0, поки
     * бракує хоча б одного з Карбону(6)/Нітрогену(7)/Оксигену(8); {@code cnoCatalystMult}
     * щойно всі три синтезовано хоча б раз — дискретний unlock, не крива насичення
     * (в реальних зорях каталізатор або присутній, або ні; кількість не має значення).
     */
    public double cnoCatalystMult(List<PlayerElement> elements) {
        boolean hasCNO = hasElement(elements, 6) && hasElement(elements, 7) && hasElement(elements, 8);
        return hasCNO ? props.cnoCatalystMult() : 1.0;
    }

    /** Сирі коефіцієнти (не множники) — для відображення формули в UI (GameStatsService). */
    public double diversityPer() {
        return props.diversityPer();
    }

    public double atomCountPer() {
        return props.atomCountPer();
    }
}
