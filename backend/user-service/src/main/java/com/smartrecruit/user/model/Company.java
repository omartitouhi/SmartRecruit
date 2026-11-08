package com.smartrecruit.user.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Field-for-field match of the {@code Company} entity in {@code DATA_MODEL.md} (§3.2).
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    private UUID id;

    @Column(length = 150)
    private String name;

    @Column(length = 100)
    private String sector;

    @Column(length = 5000)
    private String description;

    @Column(length = 500)
    private String logoUrl;

    /**
     * accountId des recruteurs rattachés (cf. DATA_MODEL.md §3.2). Unique : dans le MVP un recruteur
     * n'appartient qu'à une entreprise, et la base le garantit même en cas de requêtes concurrentes.
     */
    @ElementCollection
    @CollectionTable(name = "company_members", joinColumns = @JoinColumn(name = "company_id"))
    @Column(name = "members", unique = true)
    @Builder.Default
    private List<UUID> members = new ArrayList<>();
}
