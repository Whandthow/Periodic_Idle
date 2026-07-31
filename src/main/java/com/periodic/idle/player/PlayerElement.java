package com.periodic.idle.player;

import com.periodic.idle.content.Element;
import jakarta.persistence.*;
import lombok.*;

/** Скільки разів гравець синтезував кожен елемент (0 = ще не відкритий). */
@Entity
@Table(name = "player_elements")
@Getter @Setter
@NoArgsConstructor
public class PlayerElement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "save_id")
    private Save save;

    @ManyToOne
    @JoinColumn(name = "element_id")
    private Element element;

    private long count;
}
