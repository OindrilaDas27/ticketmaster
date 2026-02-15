package com.example.service;

import com.example.dto.EventCategoryDTO;
import com.example.dto.EventsDTO;
import com.example.entity.EventCategory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface EventService {


    List<EventCategoryDTO> getEventCategories();

    List<EventsDTO> getEvents();

    @Transactional(readOnly = true)
    Map<Long, EventCategory> getEventCategoryById(Set<Long> ids);

    Map<String, Object> getAllCategories();

    Map<String, Object> getAllEventsWithDetails();


    Map<String, Object> createEvent(EventsDTO eventRequestBody);

    Map<String, Object> buildErrorResponse(String errorMessage);
}