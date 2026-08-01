package com.periodic.idle.player;

import com.periodic.idle.content.Achievement;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Факт розблокування досягнення для конкретного save (одна дата, без повторів). */
@Entity
@Table(name = "player_achievements")
@Getter @Setter
@NoArgsConstructor
public class PlayerAchievement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "save_id")
    private Save save;

    @ManyToOne
    @JoinColumn(name = "achievement_id")
    private Achievement achievement;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt;
}
