package com.example.dao.impl;

import com.example.dao.LocationsRepository;
import com.example.entity.Locations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Locations Data Access Object Implementation
 * Provides data access operations for Locations entities
 */
@Component
public class LocationsDaoImpl {
    
    private final LocationsRepository locationsRepository;

    @Autowired
    public LocationsDaoImpl(LocationsRepository locationsRepository) {
        this.locationsRepository = locationsRepository;
    }

    /**
     * Get all locations as a Map (city -> id)
     * @return Map where key is city name and value is location id
     */
    public Map<String, Long> getAllLocations() {
        List<Object[]> results = locationsRepository.findAllCity();
        Map<String, Long> locationMap = new HashMap<>();
        
        for (Object[] row : results) {
            String city = (String) row[0];
            Long id = (Long) row[1];
            locationMap.put(city, id);
        }
        
        return locationMap;
    }

    public List<Locations> getLocationsById(Set<Long> ids) {
        return locationsRepository.findByIds(ids);
    }
}
