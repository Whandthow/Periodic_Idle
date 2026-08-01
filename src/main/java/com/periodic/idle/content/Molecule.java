package com.periodic.idle.content;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Тір 3: молекула, зібрана з атомів (хімічні зв'язки). Read-only контент, посіяний Flyway.
 * bondEnergyEv — реальна енергія зв'язку (сумарна, атомізаційна) в електрон-вольтах:
 * на ~6 порядків менша за ядерну енергію зв'язку (МеВ) — так само, як у реальній фізиці
 * хімічні реакції на порядки слабші за ядерні (MoleculeService переводить eV -> MeV).
 */
@Entity
@Table(name = "molecules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Molecule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String formula;

    @Column(nullable = false)
    private String name;

    @Column(name = "bond_energy_ev", nullable = false)
    private double bondEnergyEv;

    @OneToMany(mappedBy = "molecule")
    private List<MoleculeComponent> components;
}
