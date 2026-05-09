package com.relive.project.repository;

import com.relive.project.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {

    List<Media> findByStatus(String status);

    List<Media> findByStatusIn(List<String> statuses);

    long countByStatus(String status);

    Optional<Media> findByFileHash(String fileHash);

    @Query("SELECT DISTINCT m.location FROM Media m WHERE m.location IS NOT NULL")
    List<String> findDistinctLocations();

    @Query("""
        SELECT DISTINCT m FROM Media m
        JOIN MediaObject o ON m.id = o.media.id
        WHERE LOWER(o.objectName) LIKE LOWER(CONCAT('%', :objectName, '%'))
        AND FUNCTION('strftime', '%Y', COALESCE(m.dateTaken, m.uploadedAt)) = CAST(:year AS string)
        AND m.status = 'COMPLETED'
        """)
    List<Media> searchByObjectAndYear(
            @Param("objectName") String objectName,
            @Param("year") int year
    );

    @Query("""
        SELECT DISTINCT m FROM Media m
        JOIN MediaObject o ON m.id = o.media.id
        WHERE (:year IS NULL OR FUNCTION('strftime', '%Y', COALESCE(m.dateTaken, m.uploadedAt)) = CAST(:year AS string))
        AND o.objectName IN :objects
        AND m.status = 'COMPLETED'
        """)
    List<Media> searchByObjectsAndYear(
            @Param("objects") List<String> objects,
            @Param("year") Integer year
    );
}