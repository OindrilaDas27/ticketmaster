package com.example.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
public class Events {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "display_picture", length = 512)
    private String displayPicture;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "hosted_on", nullable = false)
    private LocalDateTime hostedOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "venue", nullable = false, length = 255)
    private String venue;

    @Column(name = "ticket_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal ticketAmount;

    @Column(name = "status", nullable = false)
    private Short status = 1;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "tickets_booked", nullable = false)
    private Integer ticketsBooked = 0;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
