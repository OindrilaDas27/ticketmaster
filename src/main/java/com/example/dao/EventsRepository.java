package com.example.dao;

import com.example.dto.EventCategoryDTO;
import com.example.entity.EventCategory;
import com.example.entity.Events;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;
import java.util.Set;


public interface EventsRepository extends JpaRepository<Events, Long> {

    @Query("SELECT new com.example.dto.EventCategoryDTO(" +
            "c.id, " +
            "c.name, " +
            "COALESCE((SELECT COUNT(e.id) FROM Events e WHERE e.categoryId = c.id), 0L)) " +
            "FROM EventCategory c " +
            "ORDER BY c.name")
    List<EventCategoryDTO> getEventCategories();

    @Query("SELECT e FROM Events e WHERE e.status=1")
    List<Events> getAllEvents();

    @Query("SELECT c FROM EventCategory c WHERE c.id IN :ids")
    List<EventCategory> getEventCategoryById(Set <Long> ids);

    @Query("SELECT c FROM EventCategory c WHERE upper(c.name) = :name")
    EventCategory getEventCategoryByName(String name);

    Events save(Events event);
}