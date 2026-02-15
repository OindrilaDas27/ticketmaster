package com.example.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventsDTO {
    private Long id;

    private String name;

    private String displayPicture;

    private String description;

    private LocalDateTime hostedFrom;

    private LocalDateTime hostedTo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long categoryId;

    private Long locationId;

    private String venue;

    private BigDecimal ticketAmount;

    private Short status;

    private Integer capacity;

    private Integer ticketsBooked;

    private String category;

    private String location;
}
