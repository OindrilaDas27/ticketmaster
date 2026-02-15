package com.example.service.impl;

import com.example.ApplicationConstants;
import com.example.dao.impl.EventsDaoImpl;
import com.example.dto.EventCategoryDTO;
import com.example.dto.EventsDTO;
import com.example.entity.EventCategory;
import com.example.entity.Events;
import com.example.entity.Locations;
import com.example.service.EventService;
import com.example.service.LocationService;
import com.example.util.ApplicationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class EventServiceImpl implements EventService {

    private final EventsDaoImpl eventsDao;
    private final LocationService locationService;

    @Autowired
    public EventServiceImpl(EventsDaoImpl eventsDao, LocationService locationService) {
        this.eventsDao = eventsDao;
        this.locationService = locationService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventCategoryDTO> getEventCategories() {
        return eventsDao.getEventCategories();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventsDTO> getEvents() {
        List<Events> events = eventsDao.getAllEvents();

        return events.stream()
                .map(ApplicationUtils::convertToEventsDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, EventCategory> getEventCategoryById(Set<Long> ids) {
        List<EventCategory> categories = eventsDao.getEventCategoryById(ids);
        return categories.stream()
                .collect(Collectors.toMap(EventCategory::getId, category -> category));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAllCategories() {
        List<EventCategoryDTO> categories = getEventCategories();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", ApplicationConstants.SUCCESS);
        response.put("data", categories);
        response.put("count", categories.size());

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAllEventsWithDetails() {
        List<EventsDTO> events = getEvents();

        Set<Long> locationIds = events.stream()
                .map(EventsDTO::getLocationId)
                .collect(Collectors.toSet());

        Set<Long> categoryIds = events.stream()
                .map(EventsDTO::getCategoryId)
                .collect(Collectors.toSet());

        Map<Long, Locations> locationsMap = locationService.getLocationsById(locationIds);
        Map<Long, EventCategory> categoriesMap = getEventCategoryById(categoryIds);

        for (EventsDTO event : events) {
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

        return response;
    }

    @Override
    public Map<String, Object> createEvent(EventsDTO eventRequestBody) {
        if(eventRequestBody == null) {
            throw new RuntimeException("Event request body cannot be null");
        }

        Events events = new Events();
        events.setName(eventRequestBody.getName());
        if(eventRequestBody.getDescription() != null) {
            events.setDescription(eventRequestBody.getDescription());
        }

        if(eventRequestBody.getDisplayPicture() != null) {
            events.setDisplayPicture(eventRequestBody.getDisplayPicture());
        }

        events.setHostedFrom(eventRequestBody.getHostedFrom());
        events.setHostedTo(eventRequestBody.getHostedTo());
        events.setTicketAmount(eventRequestBody.getTicketAmount());

        String category = eventRequestBody.getCategory();
        EventCategory eventCategory = eventsDao.getEventCategoryByName(category);
        events.setCategoryId(eventCategory.getId());

        String location = eventRequestBody.getLocation();
        Locations locations = locationService.getLocationsByCity(location);
        events.setLocationId(locations.getId());
        events.setVenue(eventRequestBody.getVenue());
        events.setCapacity(eventRequestBody.getCapacity());

        try {
            eventsDao.insertEvent(events);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Event created successfully");
            response.put("data", events);

            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, Object> buildErrorResponse(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", errorMessage);
        return response;
    }
}