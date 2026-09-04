package com.relive.project.entity;

import jakarta.persistence.*;
import lombok.*;

// Replaces MediaObject.java. Holds both VOCAB (from Qwen's 22-category
// generation + synonyms + object-detection fallback) and LOCATION (from
// reverse geocoding, 3 rows per geocoded photo: city/region/country)
// keywords — merging these into one table with a domain discriminator was a
// deliberate simplification (see design discussion §5, single Vocabulary
// hashmap decision) rather than fragmenting into per-category tables.
@Entity
@Table(name = "media_keywords", indexes = {
        @Index(name = "idx_keyword_domain", columnList = "keyword,domain")
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

    // Always lowercase, always lemmatized (for VOCAB) before storage.
    private String keyword;

    @Enumerated(EnumType.STRING)
    private Domain domain;

    @ManyToOne
    @JoinColumn(name = "media_id")
    private Media media;
}