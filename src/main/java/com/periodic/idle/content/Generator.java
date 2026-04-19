package com.periodic.idle.content;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "generator")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Generator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int tier;

    @ManyToOne
    @JoinColumn(name = "cost_resource_id")
    private Resource costResource;

    @Column
    private double baseCostNumber;

    @Column
    private long baseCostExponent;

    @Column
    private double costMultiplier;

    @OneToMany(mappedBy = "generator")
    private List<GeneratorOutput> outputs;

    @OneToMany(mappedBy = "generator")
    private List<GeneratorInput> inputs;
}
