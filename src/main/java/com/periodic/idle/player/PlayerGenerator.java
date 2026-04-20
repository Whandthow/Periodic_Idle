package com.periodic.idle.player;

import com.periodic.idle.content.Generator;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_generators")
@Getter @Setter
@NoArgsConstructor
public class PlayerGenerator {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "save_id")
    private Save save;

    @ManyToOne
    @JoinColumn(name = "generator_id")
    private Generator generator;

    private int level;
}