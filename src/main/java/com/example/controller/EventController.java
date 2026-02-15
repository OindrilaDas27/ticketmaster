package com.example.controller;

import com.example.ApplicationConstants;
import com.example.dto.EventCategoryDTO;
import com.example.dto.EventsDTO;
import com.example.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(ApplicationConstants.EVENTS_ENDPOINT)
public class EventController {

    private final EventService eventService;

    @Autowired
    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/category")
    public ResponseEntity<Map<String, Object>> getAllCategories() {
        try {
            Map<String, Object> response = eventService.getAllCategories();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = eventService.buildErrorResponse(
                    "Failed to get categories: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllEvents() {
        try {
            Map<String, Object> response = eventService.getAllEventsWithDetails();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = eventService.buildErrorResponse(
                    "Failed to get events: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createEvent(@RequestBody EventsDTO eventRequestBody) {
        try {
            Map<String, Object> response = eventService.createEvent(eventRequestBody);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = eventService.buildErrorResponse(
                    "Failed to create event: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}