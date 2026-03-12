package com.relive.project.repository;

import com.relive.project.entity.MediaObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaObjectRepository extends JpaRepository<MediaObject, Long> {

    List<MediaObject> findByObjectName(String objectName);
    List<MediaObject> findByObjectNameContainingIgnoreCaseAndMedia_User_Email(
            String objectName,
            String email
    );}
