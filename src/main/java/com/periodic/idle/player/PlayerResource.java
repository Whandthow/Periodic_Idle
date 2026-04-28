package com.periodic.idle.player;

import com.periodic.idle.content.Resource;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_resources")
@Getter @Setter
@NoArgsConstructor
public class PlayerResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "save_id")
    private Save save;

    @ManyToOne
    @JoinColumn(name = "resource_id")
    private Resource resource;

    private double number;
    private long exponent;
}