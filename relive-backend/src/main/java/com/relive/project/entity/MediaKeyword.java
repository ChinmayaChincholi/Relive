package com.relive.project.entity;

import jakarta.persistence.*;
import lombok.*;

// Replaces MediaObject.java. Used to also hold LOCATION rows (from reverse
// geocoding) alongside VOCAB rows, discriminated by a `domain` column.
// LOCATION now lives in its own `locations` table (see Location.java) —
// PERSON was never stored here either (it lives in face_persons /
// face_embeddings), so once LOCATION also left, this table only ever holds
// VOCAB rows and the domain discriminator column served no purpose anymore.
@Entity
@Table(name = "media_keywords", indexes = {
        @Index(name = "idx_keyword", columnList = "keyword")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaKeyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Always lowercase, always lemmatized before storage.
    private String keyword;
    @ManyToOne
    @JoinColumn(name = "media_id")
    private Media media;
}