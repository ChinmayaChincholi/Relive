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

    private String name; // null until user names this person

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}