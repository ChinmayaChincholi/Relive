package com.relive.project.repository;

import com.relive.project.entity.FaceEmbedding;
import com.relive.project.entity.FacePerson;
import com.relive.project.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface FaceEmbeddingRepository extends JpaRepository<FaceEmbedding, Long> {

    List<FaceEmbedding> findByMedia_Id(Long mediaId);

    List<FaceEmbedding> findAll();

    List<FaceEmbedding> findByPerson(FacePerson person);

    List<FaceEmbedding> findByPersonIsNull();

    @Modifying
    void deleteByMedia(Media media);
}