package com.relive.project.repository;

import com.relive.project.entity.FacePerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacePersonRepository extends JpaRepository<FacePerson, Long> {

    List<FacePerson> findByName(String name);

    List<FacePerson> findByNameIgnoreCase(String name);

}