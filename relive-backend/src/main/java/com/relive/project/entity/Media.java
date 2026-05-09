package com.relive.project.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;

    // Absolute path to the stored file on disk.
    // Stored as absolute so it works regardless of working directory.
    private String filePath;

    private String mediaType; // "IMAGE" or "VIDEO"

    private LocalDateTime uploadedAt;

    @Column(length = 3000)
    private String sceneCaption;

    private String eventType; // "day" or "night"

    private Integer faceCount;

    @Column(nullable = false)
    private String status; // PROCESSING | COMPLETED | FAILED

    @Column(nullable = false)
    private String fileHash; // SHA-256 of file bytes — deduplication guard

    // Full datetime from EXIF
    private LocalDateTime dateTaken;

    // Human-readable location string from reverse geocoding, e.g. "Bengaluru, Karnataka, India"
    private String location;
}