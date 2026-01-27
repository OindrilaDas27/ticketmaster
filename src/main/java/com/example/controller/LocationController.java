package com.example.controller;

import com.example.ApplicationConstants;
import com.example.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(ApplicationConstants.LOCATION_ENDPOINT)
public class LocationController {
    
    private final LocationService locationService;

    @Autowired
    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getLocations() {
        try {
            Map<String, Long> locations = locationService.getLocations();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", ApplicationConstants.SUCCESS);
            response.put("data", locations);
            response.put("count", locations.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to get locations: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
