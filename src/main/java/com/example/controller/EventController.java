package com.example.controller;

import com.example.ApplicationConstants;
import com.example.dto.EventCategoryDTO;
import com.example.dto.EventsDTO;
import com.example.dto.LocationsDTO;
import com.example.entity.EventCategory;
import com.example.entity.Locations;
import com.example.service.EventService;
import com.example.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApplicationConstants.EVENTS_ENDPOINT)
public class EventController {
    
    private final EventService eventService;
    private final LocationService locationService;

    @Autowired
    public EventController(EventService eventService, LocationService locationService) {
        this.eventService = eventService;
        this.locationService = locationService;
    }

    @GetMapping("/category")
    public ResponseEntity<Map<String, Object>> getAllCategories() {
        try {
            List<EventCategoryDTO> categories = eventService.getEventCategories();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", ApplicationConstants.SUCCESS);
            response.put("data", categories);
            response.put("count", categories.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to get categories: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllEvents() {
        try {
            List<EventsDTO> events = eventService.getEvents();

            // Collect all unique IDs
            Set<Long> locationIds = events.stream()
                    .map(EventsDTO::getLocationId)
                    .collect(Collectors.toSet());

            Set<Long> categoryIds = events.stream()
                    .map(EventsDTO::getCategoryId)
                    .collect(Collectors.toSet());

            // Fetch all locations and categories in bulk
            Map<Long, Locations> locationsMap = locationService.getLocationsById(locationIds);
            Map<Long, EventCategory> categoriesMap = eventService.getEventCategoryById(categoryIds);

            // Map the data
            for(EventsDTO event : events) {
                Locations location = locationsMap.get(event.getLocationId());
                if (location != null) {
                    event.setLocation(location.getCity() + ", " + location.getCountry());
                }

                EventCategory category = categoriesMap.get(event.getCategoryId());
                if (category != null) {
                    event.setCategory(category.getName());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", ApplicationConstants.SUCCESS);
            response.put("data", events);
            response.put("count", events.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to get events: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
