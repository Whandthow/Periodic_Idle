package com.periodic.idle.player;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saves")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Save {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String playerName;
    private LocalDateTime lastTick;
}