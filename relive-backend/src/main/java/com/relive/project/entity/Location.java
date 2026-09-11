package com.relive.project.entity;

import jakarta.persistence.*;
import lombok.*;

// One row per geocoded granularity per photo (city / region / country),
// mirroring the existing FacePerson/FaceEmbedding split rather than
// cramming location data into the general-purpose media_keywords table
// with a domain discriminator.
@Entity
@Table(name = "locations", indexes = {
        @Index(name = "idx_location_name", columnList = "locationName")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Always lowercase (e.g. "bengaluru", "karnataka", "india").
    private String locationName;
    @ManyToOne
    @JoinColumn(name = "media_id")
    private Media media;
}