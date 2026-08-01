package com.periodic.idle.common;

/**
 * Напівемпірична формула Вайцзеккера (SEMF) для енергії зв'язку атомного ядра.
 * Дає реальну фізичну форму кривої питомої енергії зв'язку: зростає від легких
 * елементів, досягає піку на залізі-56 (~8.8 МеВ/нуклон), потім повільно спадає —
 * тому термоядерний синтез у зорях природно живить себе лише до заліза (SynthesisService).
 */
public final class BindingEnergy {

    private static final double A_VOLUME = 15.8;
    private static final double A_SURFACE = 18.3;
    private static final double A_COULOMB = 0.714;
    private static final double A_ASYMMETRY = 23.2;
    private static final double A_PAIRING = 12.0;

    private BindingEnergy() {}

    /**
     * Повна енергія зв'язку ядра (МеВ) для атомного номера Z і масового числа A.
     * Одинокий протон (A&lt;=1, звичайний H) фізично не має енергії зв'язку — 0.
     */
    public static double totalMeV(int atomicNumber, int massNumber) {
        if (massNumber <= 1 || atomicNumber <= 0) return 0.0;

        double a = massNumber;
        double z = atomicNumber;
        double n = massNumber - atomicNumber;

        double volume = A_VOLUME * a;
        double surface = A_SURFACE * Math.pow(a, 2.0 / 3.0);
        double coulomb = A_COULOMB * z * (z - 1) / Math.pow(a, 1.0 / 3.0);
        double asymmetry = A_ASYMMETRY * Math.pow(a - 2 * z, 2) / a;

        boolean zEven = atomicNumber % 2 == 0;
        boolean nEven = ((long) n) % 2 == 0;
        double pairing;
        if (zEven && nEven) pairing = A_PAIRING / Math.sqrt(a);
        else if (!zEven && !nEven) pairing = -A_PAIRING / Math.sqrt(a);
        else pairing = 0.0;

        double be = volume - surface - coulomb - asymmetry + pairing;
        return Math.max(be, 0.0);
    }

    /** Енергія зв'язку на нуклон (МеВ/A) — для UI/довідки, показує реальну форму кривої. */
    public static double perNucleonMeV(int atomicNumber, int massNumber) {
        if (massNumber <= 0) return 0.0;
        return totalMeV(atomicNumber, massNumber) / massNumber;
    }
}
