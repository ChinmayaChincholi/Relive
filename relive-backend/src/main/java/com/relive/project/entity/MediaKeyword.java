package com.relive.project.entity;

import jakarta.persistence.*;
import lombok.*;

// One row per vocabulary keyword per photo. Location names live in the
// `locations` table (see Location.java); people live in face_persons /
// face_embeddings.
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