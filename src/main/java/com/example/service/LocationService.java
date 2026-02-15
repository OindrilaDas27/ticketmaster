package com.example.service;

import com.example.entity.Locations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

public interface LocationService {
    Map<String, Long> getLocations();

    @Transactional(readOnly = true)
    Map<Long, Locations> getLocationsById(Set<Long> id);

    Locations getLocationsByCity(String city);
}
