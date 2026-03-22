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

    // Facenet512 produces 512-dim embeddings
    // 512 floats × ~22 chars each = ~11,264 chars, use 30000 to be safe
    @Column(length = 30000)
    private String embeddingCsv;

    private Double confidence;

    @ManyToOne
    @JoinColumn(name = "media_id")
    private Media media;

    @ManyToOne
    @JoinColumn(name = "person_id")
    private FacePerson person;
}