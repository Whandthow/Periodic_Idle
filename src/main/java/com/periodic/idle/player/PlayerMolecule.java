package com.periodic.idle.player;

import com.periodic.idle.content.Molecule;
import jakarta.persistence.*;
import lombok.*;

/** Скільки разів гравець зібрав кожну молекулу (0 = ще не зібрана). */
@Entity
@Table(name = "player_molecules")
@Getter @Setter
@NoArgsConstructor
public class PlayerMolecule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "save_id")
    private Save save;

    @ManyToOne
    @JoinColumn(name = "molecule_id")
    private Molecule molecule;

    private long count;
}
