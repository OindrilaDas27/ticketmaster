package com.example.dao;

import com.example.entity.Locations;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface LocationsRepository extends JpaRepository<Locations, Long> {
    
    @Query("SELECT l.city, l.id FROM Locations l ORDER BY l.city")
    List<Object[]> findAllCity();

    @Query("SELECT l FROM Locations l WHERE l.id IN :ids")
    List<Locations> findByIds(Set<Long> ids);
}
