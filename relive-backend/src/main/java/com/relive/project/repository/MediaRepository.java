package com.relive.project.repository;

import com.relive.project.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaRepository extends JpaRepository<Media, Long> {

    List<Media> findByUser_Email(String email);

    List<Media> findByUser_EmailAndStatus(String email, String status);

    long countByUser_Email(String email);

    long countByUser_EmailAndStatus(String email, String status);

    Optional<Media> findByFileHashAndUser_Email(String fileHash, String email);

    List<Media> findByStatusIn(List<String> statuses);

    @Query("SELECT DISTINCT m.location FROM Media m WHERE m.user.email = :email AND m.location IS NOT NULL")
    List<String> findDistinctLocationsByUserEmail(@Param("email") String email);

    @Query("""
        SELECT DISTINCT m FROM Media m
        JOIN MediaObject o ON m.id = o.media.id
        WHERE LOWER(o.objectName) LIKE LOWER(CONCAT('%', :objectName, '%'))
        AND FUNCTION('YEAR', COALESCE(m.dateTaken, m.uploadedAt)) = :year
        AND m.user.email = :email
        AND m.status = 'COMPLETED'
        """)
    List<Media> searchByObjectAndYear(
            @Param("objectName") String objectName,
            @Param("year") int year,
            @Param("email") String email
    );

    @Query("""
        SELECT DISTINCT m FROM Media m
        JOIN MediaObject o ON m.id = o.media.id
        WHERE (:year IS NULL OR FUNCTION('YEAR', COALESCE(m.dateTaken, m.uploadedAt)) = :year)
        AND o.objectName IN :objects
        AND m.user.email = :email
        AND m.status = 'COMPLETED'
        """)
    List<Media> searchByObjectsAndYear(
            @Param("objects") List<String> objects,
            @Param("year") Integer year,
            @Param("email") String email
    );
}