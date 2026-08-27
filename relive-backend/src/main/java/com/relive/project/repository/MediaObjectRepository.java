package com.relive.project.repository;

import com.relive.project.entity.Media;
import com.relive.project.entity.MediaObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MediaObjectRepository extends JpaRepository<MediaObject, Long> {

    List<MediaObject> findByObjectName(String objectName);

    List<MediaObject> findByObjectNameContainingIgnoreCase(String objectName);

    @Query("SELECT DISTINCT o.objectName FROM MediaObject o")
    List<String> findDistinctObjectNames();

    @Modifying
    void deleteByMedia(Media media);
}