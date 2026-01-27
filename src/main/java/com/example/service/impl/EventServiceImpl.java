package com.example.service.impl;

import com.example.dao.impl.EventsDaoImpl;
import com.example.dto.EventCategoryDTO;
import com.example.dto.EventsDTO;
import com.example.entity.EventCategory;
import com.example.entity.Events;
import com.example.service.EventService;
import com.example.util.ApplicationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class EventServiceImpl implements EventService {
    private final EventsDaoImpl eventsDao;

    public EventServiceImpl(EventsDaoImpl eventsDao) {
        this.eventsDao = eventsDao;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventCategoryDTO> getEventCategories() {
        return eventsDao.getEventCategories();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventsDTO> getEvents() {
        List<Events> events =  eventsDao.getAllEvents();

        return events.stream()
                .map(ApplicationUtils::convertToEventsDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public Map<Long, EventCategory> getEventCategoryById(Set<Long> ids) {
        List<EventCategory> categories = eventsDao.getEventCategoryById(ids);
        return categories.stream()
                .collect(Collectors.toMap(EventCategory::getId, category -> category));
    }
}