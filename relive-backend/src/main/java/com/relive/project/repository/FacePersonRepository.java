package com.relive.project.repository;

import com.relive.project.entity.FacePerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacePersonRepository extends JpaRepository<FacePerson, Long> {

    // Exact match — kept for internal use where we know the exact stored name
    List<FacePerson> findByName(String name);

    // Case-insensitive match — used for search queries where the user may
    // type "chinmaya", "Chinmaya", or "CHINMAYA" and all should match
    List<FacePerson> findByNameIgnoreCase(String name);

    // Contains match — handles partial name searches like "chin" matching "Chinmaya"
    List<FacePerson> findByNameContainingIgnoreCase(String name);
}