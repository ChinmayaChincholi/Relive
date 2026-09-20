package com.relive.project.repository;

import com.relive.project.entity.FacePerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FacePersonRepository extends JpaRepository<FacePerson, Long> {
    List<FacePerson> findByNameIgnoreCase(String name);

    /**
     * Bulk-deletes one person row straight in SQL. Unlike delete(entity),
     * this does NOT require exactly one row to be affected, so it is safe
     * even when trg_face_embedding_delete_orphan_person has already removed
     * the row --- see FaceService.deletePerson().
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FacePerson p WHERE p.id = :personId")
    int deletePersonById(@Param("personId") Long personId);
}