package com.periodic.idle.content;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "generator_outputs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeneratorOutput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "generator_id")
    private Generator generator;

    @ManyToOne
    @JoinColumn(name = "resource_id")
    private Resource resource;

    private double ratePerLevel;
}
