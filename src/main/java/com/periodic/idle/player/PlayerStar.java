package com.periodic.idle.player;

import com.periodic.idle.content.Star;
import jakarta.persistence.*;
import lombok.*;

/** Рівень зорі гравця (0 = ще не запалена). */
@Entity
@Table(name = "player_stars")
@Getter @Setter
@NoArgsConstructor
public class PlayerStar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "save_id")
    private Save save;

    @ManyToOne
    @JoinColumn(name = "star_id")
    private Star star;

    private int level;
}
