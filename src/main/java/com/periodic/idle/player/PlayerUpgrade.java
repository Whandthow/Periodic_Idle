package com.periodic.idle.player;

import com.periodic.idle.content.Upgrade;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_upgrades")
@Getter @Setter
@NoArgsConstructor
public class PlayerUpgrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "save_id")
    private Save save;

    @ManyToOne
    @JoinColumn(name = "upgrade_id")
    private Upgrade upgrade;

    private int level;
}