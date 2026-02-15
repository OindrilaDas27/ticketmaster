package com.example.service.impl;

import com.example.dao.LocationsRepository;
import com.example.dao.impl.LocationsDaoImpl;
import com.example.entity.Locations;
import com.example.service.LocationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class LocationServiceImpl implements LocationService {
    private final LocationsDaoImpl locationsDao;

    public LocationServiceImpl(LocationsDaoImpl locationsDao) {
        this.locationsDao = locationsDao;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getLocations() {
        return locationsDao.getAllLocations();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Locations> getLocationsById(Set<Long> ids) {
        List<Locations> locations = locationsDao.getLocationsById(ids);
        return locations.stream()
                .collect(Collectors.toMap(Locations::getId, location -> location));
    }

    @Override
    public Locations getLocationsByCity(String city) {
        return locationsDao.getLocationsByCity(city);
    }
}
