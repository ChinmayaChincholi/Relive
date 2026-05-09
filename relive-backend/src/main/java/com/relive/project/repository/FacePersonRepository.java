package com.relive.project.repository;

import com.relive.project.entity.FacePerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacePersonRepository extends JpaRepository<FacePerson, Long> {

    // Returns List to support multiple persons with the same name.
    List<FacePerson> findByName(String name);
}