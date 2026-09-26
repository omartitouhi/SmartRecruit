package com.smartrecruit.user.model;

import jakarta.persistence.CollectionTable;
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

    private String name;
    private String sector;
    private String description;
    private String logoUrl;

    /** accountId des recruteurs rattachés (cf. DATA_MODEL.md §3.2). */
    @ElementCollection
    @CollectionTable(name = "company_members", joinColumns = @JoinColumn(name = "company_id"))
    @Builder.Default
    private List<UUID> members = new ArrayList<>();
}
