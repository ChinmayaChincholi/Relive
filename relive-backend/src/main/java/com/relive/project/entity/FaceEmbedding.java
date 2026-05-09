package com.relive.project.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "face_embeddings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cropPath;

    // Face embedding stored as CSV of floats.
    // 512-dim × ~22 chars each ≈ 11,264 chars; 30000 gives comfortable headroom.
    @Column(length = 30000)
    private String embeddingCsv;

    private Double confidence;

    @ManyToOne
    @JoinColumn(name = "media_id")
    private Media media;

    // Null until clustering assigns this face to a FacePerson.
    @ManyToOne
    @JoinColumn(name = "person_id")
    private FacePerson person;
}