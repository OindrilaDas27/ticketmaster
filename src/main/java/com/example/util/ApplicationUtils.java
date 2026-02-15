package com.example.util;

import com.example.dto.EventsDTO;
import com.example.dto.UserDTO;
import com.example.entity.Events;
import com.example.entity.User;

/**
 * Application Utility Class
 * Contains utility methods for common operations like DTO-Entity mapping
 */
public class ApplicationUtils {

    private ApplicationUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Convert User entity to UserDto
     * @param user the User entity
     * @return UserDto object
     */
    public static UserDTO convertToDto(User user) {
        if (user == null) {
            return null;
        }

        UserDTO userDto = new UserDTO();
        userDto.setId(user.getId());
        userDto.setUsername(user.getUsername());
        userDto.setEmail(user.getEmail());
        userDto.setFirstName(user.getFirstName());
        userDto.setLastName(user.getLastName());
        userDto.setPhoneNumber(user.getPhoneNumber());
        userDto.setCreatedAt(user.getCreatedAt());
        userDto.setUpdatedAt(user.getUpdatedAt());

        return userDto;
    }

    /**
     * Convert UserDto to User entity
     * @param userDto the UserDto object
     * @return User entity
     */
    public static User convertToEntity(UserDTO userDto) {
        if (userDto == null) {
            return null;
        }

        User user = new User();
        user.setId(userDto.getId());
        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setPhoneNumber(userDto.getPhoneNumber());

        return user;
    }

    /**
     * Update User entity with UserDto data
     * @param user the existing User entity to update
     * @param userDto the UserDto with new data
     */
    public static void updateEntityFromDto(User user, UserDTO userDto) {
        if (user == null || userDto == null) {
            return;
        }

        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setPhoneNumber(userDto.getPhoneNumber());
    }

    public static EventsDTO convertToEventsDTO(Events events) {
        if (events == null) {
            return null;
        }

        EventsDTO eventsDTO = new EventsDTO();
        eventsDTO.setId(events.getId());
        eventsDTO.setDisplayPicture(events.getDisplayPicture());
        eventsDTO.setName(events.getName());
        eventsDTO.setVenue(events.getVenue());
        eventsDTO.setCapacity(events.getCapacity());
        eventsDTO.setDescription(events.getDescription());
        eventsDTO.setCreatedAt(events.getCreatedAt());
        eventsDTO.setUpdatedAt(events.getUpdatedAt());
        eventsDTO.setCategoryId(events.getCategoryId());
        eventsDTO.setStatus(events.getStatus());
        eventsDTO.setHostedFrom(events.getHostedFrom());
        eventsDTO.setHostedTo(events.getHostedTo());
        eventsDTO.setLocationId(events.getLocationId());
        eventsDTO.setTicketsBooked(events.getTicketsBooked());
        eventsDTO.setTicketAmount(events.getTicketAmount());

        return eventsDTO;
    }
}

