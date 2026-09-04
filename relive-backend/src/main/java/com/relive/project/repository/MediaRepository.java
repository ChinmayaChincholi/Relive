package com.relive.project.repository;

import com.relive.project.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {

    List<Media> findByStatus(String status);

    List<Media> findByStatusIn(List<String> statuses);

    long countByStatus(String status);

    Optional<Media> findByFileHash(String fileHash);

    List<Media> findByDateTakenBetween(LocalDateTime start, LocalDateTime end);

    // Matches on time-of-day only, across any date — dateTaken already
    // stores both, so this extracts just the HH:MM part via SQLite's
    // strftime rather than needing a separate Time treemap/table.
    @Query(value = "SELECT id FROM media WHERE date_taken IS NOT NULL " +
            "AND strftime('%H:%M', date_taken) BETWEEN :start AND :end", nativeQuery = true)
    List<Long> findIdsByTimeOfDayBetween(@Param("start") String start, @Param("end") String end);
}