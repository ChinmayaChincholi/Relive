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

    private String filePath;

    private String mediaType; // IMAGE or VIDEO

    private LocalDateTime uploadedAt;

    @Column(length = 3000)
    private String sceneCaption;

    private String eventType; // "day" or "night"

    private Integer faceCount;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String fileHash;

    // Full datetime from EXIF (date + time)
    private LocalDateTime dateTaken;

    // GPS coordinates as "lat,lon" string e.g. "12.9716,77.5946"
    private String location;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}