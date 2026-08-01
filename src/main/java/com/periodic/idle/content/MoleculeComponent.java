package com.periodic.idle.content;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Скільки атомів якого елемента входить у рецепт молекули (напр. H2O = 2×H + 1×O). */
@Entity
@Table(name = "molecule_components")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoleculeComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "molecule_id")
    private Molecule molecule;

    @ManyToOne
    @JoinColumn(name = "element_id")
    private Element element;

    @Column(name = "atom_count", nullable = false)
    private int atomCount;
}
