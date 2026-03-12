package com.relive.project.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "media_objects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String objectName;

    @ManyToOne
    @JoinColumn(name = "media_id")
    private Media media;
}
