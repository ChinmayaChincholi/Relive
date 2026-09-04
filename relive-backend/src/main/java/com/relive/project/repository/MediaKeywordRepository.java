package com.relive.project.repository;

import com.relive.project.entity.Domain;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MediaKeywordRepository extends JpaRepository<MediaKeyword, Long> {

    List<MediaKeyword> findByDomainAndKeyword(Domain domain, String keyword);

    List<MediaKeyword> findByDomainAndKeywordContainingIgnoreCase(Domain domain, String keyword);

    @Query("SELECT DISTINCT k.keyword FROM MediaKeyword k WHERE k.domain = :domain")
    List<String> findDistinctKeywordsByDomain(Domain domain);

    @Modifying
    void deleteByMedia(Media media);
}