package com.relive.project.repository;

import com.relive.project.entity.Media;
import com.relive.project.entity.MediaObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface MediaObjectRepository extends JpaRepository<MediaObject, Long> {

    List<MediaObject> findByObjectName(String objectName);

    List<MediaObject> findByObjectNameContainingIgnoreCaseAndMedia_User_Email(
            String objectName,
            String email
    );

    @Modifying
    void deleteByMedia(Media media);
}