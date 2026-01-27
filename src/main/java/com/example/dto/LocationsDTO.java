package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationsDTO {
    private Long id;
    private String city;
    private String country;
    private String state;
    private String pincode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
