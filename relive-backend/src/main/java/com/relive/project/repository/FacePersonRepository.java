package com.relive.project.repository;

import com.relive.project.entity.FacePerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacePersonRepository extends JpaRepository<FacePerson, Long> {

    List<FacePerson> findByUser_Email(String email);

    // Returns List to support multiple persons with the same name
    // (e.g. same person at different ages named identically)
    List<FacePerson> findByNameAndUser_Email(String name, String email);
}