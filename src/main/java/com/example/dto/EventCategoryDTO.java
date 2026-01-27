package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventCategoryDTO {
    private Long id;
    private String categoryName;
    private Long eventCount;
}
