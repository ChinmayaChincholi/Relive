package com.relive.project.repository;

import com.relive.project.entity.FaceEmbedding;
import com.relive.project.entity.FacePerson;
import com.relive.project.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FaceEmbeddingRepository extends JpaRepository<FaceEmbedding, Long> {

    List<FaceEmbedding> findByPerson(FacePerson person);
    List<FaceEmbedding> findByMedia(Media media);

    @Modifying
    void deleteByMedia(Media media);

    /**
     * Bulk-deletes every embedding of one person straight in SQL (no
     * per-entity Hibernate delete), so it can never trip over the
     * orphan-person DB trigger --- see FaceService.deletePerson().
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FaceEmbedding fe WHERE fe.person.id = :personId")
    int deleteAllByPersonId(@Param("personId") Long personId);
}