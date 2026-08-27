package com.relive.project.repository;

import com.relive.project.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {

    List<Media> findByStatus(String status);

    List<Media> findByStatusIn(List<String> statuses);

    long countByStatus(String status);

    Optional<Media> findByFileHash(String fileHash);

    @Query("SELECT DISTINCT m.location FROM Media m WHERE m.location IS NOT NULL")
    List<String> findDistinctLocations();
}