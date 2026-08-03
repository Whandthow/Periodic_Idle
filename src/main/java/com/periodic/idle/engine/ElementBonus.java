package com.periodic.idle.engine;

import com.periodic.idle.player.PlayerElement;

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
 *       двоступенева tanh-крива (лінійно в лог-масштабі до {@link #ATOM_SOFTCAP_LOG10},
 *       далі софт-декеле­рація, потім жорстка асимптота-стеля при
 *       {@link #ATOM_SOFTCAP_LOG10} + {@link #ATOM_HARD_RANGE_LOG10}) — "софт кап
 *       при великій кількості, хард кап при нереальній".</li>
 *   <li>{@link #cnoCatalystMult} — реальна астрофізика: щойно синтезовано Карбон,
 *       Нітроген і Оксиген (Z=6,7,8), вони каталізують протонний синтез у зорі
 *       (CNO-цикл), не витрачаючись самі — дискретний множник пропускної
 *       здатності {@link StarService}, не крива насичення.</li>
 * </ul>
 */
public final class ElementBonus {

    /** +6% до енергомножника за кожен "ефективний" різний елемент (крива насичення). */
    public static final double DIVERSITY_PER = 0.06;

    /** Масштаб насичення різноманіття: після стількох різних елементів крива відчутно вигинається. */
    private static final double DIVERSITY_SATURATION_SCALE = 15.0;

    /** +15% до енергомножника за кожну "ефективну" одиницю log10(сумарних атомів). */
    public static final double ATOM_COUNT_PER = 0.15;

    /** До цього порогу log10(totalAtoms) ефективна величина росте лінійно (софт кап ще не діє). */
    private static final double ATOM_SOFTCAP_LOG10 = 6.0;

    /** Ширина tanh-хвоста понад софт-кап: ефективна величина ніколи не перевищує SOFTCAP + RANGE (хард кап). */
    private static final double ATOM_HARD_RANGE_LOG10 = 6.0;

    /** Множник пропускної здатності зоряного синтезу (StarService), коли C+N+O уже синтезовано. */
    public static final double CNO_CATALYST_MULT = 2.0;

    private ElementBonus() {
    }

    /** "Ефективна" кількість після насичення: x / (1 + x/SCALE). */
    private static double saturating(double x, double scale) {
        return x / (1.0 + x / scale);
    }

    /** Кількість різних елементів, синтезованих хоча б раз (count > 0). */
    public static long distinctCount(List<PlayerElement> elements) {
        return elements.stream().filter(pe -> pe.getCount() > 0).count();
    }

    /** Сумарна кількість атомів усіх елементів разом (захист від переповнення long). */
    public static long totalAtomCount(List<PlayerElement> elements) {
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

    /** Множник енергії від різноманіття синтезованих елементів: 1 + saturating(distinct) * DIVERSITY_PER. */
    public static double diversityMult(List<PlayerElement> elements) {
        long distinct = distinctCount(elements);
        if (distinct <= 0) return 1.0;
        return 1.0 + saturating(distinct, DIVERSITY_SATURATION_SCALE) * DIVERSITY_PER;
    }

    /**
     * Множник енергії від сумарної кількості атомів: двоступенева softcap/hardcap-крива
     * над log10(totalAtoms) — див. клас-докстрінг.
     */
    public static double atomCountMult(List<PlayerElement> elements) {
        long total = totalAtomCount(elements);
        if (total <= 0) return 1.0;

        double x = Math.log10((double) total + 1.0);
        if (!Double.isFinite(x) || x <= 0) return 1.0;

        double effectiveX = x;
        if (x > ATOM_SOFTCAP_LOG10) {
            double excess = x - ATOM_SOFTCAP_LOG10;
            effectiveX = ATOM_SOFTCAP_LOG10 + ATOM_HARD_RANGE_LOG10 * Math.tanh(excess / ATOM_HARD_RANGE_LOG10);
        }
        double result = 1.0 + effectiveX * ATOM_COUNT_PER;
        return Double.isFinite(result) ? result : 1.0;
    }

    /** Чи синтезовано елемент із заданим атомним номером хоча б раз. */
    private static boolean hasElement(List<PlayerElement> elements, int atomicNumber) {
        return elements.stream()
                .anyMatch(pe -> pe.getElement() != null
                        && pe.getElement().getAtomicNumber() == atomicNumber
                        && pe.getCount() > 0);
    }

    /**
     * Множник пропускної здатності зоряного синтезу від CNO-каталізу: 1.0, поки
     * бракує хоча б одного з Карбону(6)/Нітрогену(7)/Оксигену(8); {@link #CNO_CATALYST_MULT}
     * щойно всі три синтезовано хоча б раз — дискретний unlock, не крива насичення
     * (в реальних зорях каталізатор або присутній, або ні; кількість не має значення).
     */
    public static double cnoCatalystMult(List<PlayerElement> elements) {
        boolean hasCNO = hasElement(elements, 6) && hasElement(elements, 7) && hasElement(elements, 8);
        return hasCNO ? CNO_CATALYST_MULT : 1.0;
    }
}
