package com.example.service;

import com.example.dto.UserDTO;
import java.util.List;

/**
 * User Service Interface
 * Defines business operations for User management
 */
public interface UserService {

    /**
     * Create a new user
     * @param userDto the user DTO containing user data
     * @return the created user DTO
     */
    UserDTO createUser(UserDTO userDto);

    /**
     * Get user by ID
     * @param id the user ID
     * @return the user DTO
     * @throws RuntimeException if user not found
     */
    UserDTO getUserById(Long id);

    /**
     * Get all users
     * @return list of all user DTOs
     */
    List<UserDTO> getAllUsers();

    /**
     * Update an existing user
     * @param id the user ID
     * @param userDto the user DTO with updated data
     * @return the updated user DTO
     * @throws RuntimeException if user not found
     */
    UserDTO updateUser(Long id, UserDTO userDto);

    /**
     * Delete a user by ID
     * @param id the user ID to delete
     * @throws RuntimeException if user not found
     */
    void deleteUser(Long id);
}

