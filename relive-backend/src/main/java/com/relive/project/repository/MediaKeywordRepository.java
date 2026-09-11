package com.relive.project.repository;

import com.relive.project.entity.Media;
import com.relive.project.entity.MediaKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface MediaKeywordRepository extends JpaRepository<MediaKeyword, Long> {
    List<MediaKeyword> findByKeyword(String keyword);

    List<MediaKeyword> findByKeywordContainingIgnoreCase(String keyword);

    @Query("SELECT DISTINCT k.keyword FROM MediaKeyword k")
    List<String> findDistinctKeywords();

    @Modifying
    void deleteByMedia(Media media);
}