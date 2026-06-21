package com.nocteon.nocteon_api.tastingNote.entity;

import com.nocteon.nocteon_api.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "tasting_note_translations")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TastingNoteTranslation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tasting_note_id", nullable = false)
    private TastingNote tastingNote;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false)
    private String name;
}