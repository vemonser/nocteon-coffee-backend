package com.nocteon.nocteon_api.tastingNote.entity;

import java.util.ArrayList;
import java.util.List;

import com.nocteon.nocteon_api.common.entity.SoftDeletableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "tasting_notes")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TastingNote extends SoftDeletableEntity {

    @Column(nullable = false, unique = true)
    private String slug;

    @OneToMany(mappedBy = "tastingNote", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TastingNoteTranslation> translations = new ArrayList<>();
}