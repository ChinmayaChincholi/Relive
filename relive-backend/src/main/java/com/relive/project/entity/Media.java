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

    private String mediaType;

    private LocalDateTime uploadedAt;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String fileHash;

    private LocalDateTime dateTaken;

    private String location;
}