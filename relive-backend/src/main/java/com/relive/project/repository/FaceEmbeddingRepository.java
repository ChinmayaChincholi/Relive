package com.relive.project.repository;

import com.relive.project.entity.FaceEmbedding;
import com.relive.project.entity.FacePerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaceEmbeddingRepository extends JpaRepository<FaceEmbedding, Long> {

    List<FaceEmbedding> findByMedia_Id(Long mediaId);

    List<FaceEmbedding> findByMedia_User_Email(String email);

    List<FaceEmbedding> findByPerson(FacePerson person);

    List<FaceEmbedding> findByPersonIsNullAndMedia_User_Email(String email);
}