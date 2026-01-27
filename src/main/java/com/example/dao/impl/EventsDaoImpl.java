package com.example.dao.impl;

import com.example.dao.EventsRepository;
import com.example.dto.EventCategoryDTO;
import com.example.entity.EventCategory;
import com.example.entity.Events;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;


@Component
public class EventsDaoImpl {
    
    private final EventsRepository eventsRepository;

    @Autowired
    public EventsDaoImpl(EventsRepository eventsRepository) {
        this.eventsRepository = eventsRepository;
    }

    public List<EventCategoryDTO> getEventCategories() {
        return eventsRepository.getEventCategories();
    }

    public List<Events> getAllEvents() {
        return eventsRepository.getAllEvents();
    }

    public List<EventCategory> getEventCategoryById(Set<Long> ids) {
        return eventsRepository.getEventCategoryById(ids);
    }
}