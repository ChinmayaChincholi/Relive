package com.relive.project.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MediaResponseDTO {

    private Long id;

    private String fileName;

    private String status;

    private LocalDateTime dateTaken;

    private String location;

}