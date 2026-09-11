package com.relive.project.repository;

import com.relive.project.entity.Location;
import com.relive.project.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByLocationName(String locationName);

    @Query("SELECT DISTINCT l.locationName FROM Location l")
    List<String> findDistinctLocationNames();

    @Modifying
    void deleteByMedia(Media media);
}