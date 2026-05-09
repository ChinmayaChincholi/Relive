package com.relive.project.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "face_persons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacePerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null until the user assigns a name to this person cluster.
    private String name;
}