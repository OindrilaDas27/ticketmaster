package com.example.validation;

import com.example.dto.UserDTO;

/**
 * User Validation Interface
 * Defines validation methods for User operations
 */
public interface UserValidation {

    /**
     * Validate user data for creation
     * @param userDto the user DTO to validate
     * @throws IllegalArgumentException if validation fails
     */
    void validateForCreate(UserDTO userDto);

    /**
     * Validate user data for update
     * @param userDto the user DTO to validate
     * @throws IllegalArgumentException if validation fails
     */
    void validateForUpdate(UserDTO userDto);

    /**
     * Validate user ID
     * @param id the user ID to validate
     * @throws IllegalArgumentException if validation fails
     */
    void validateId(Long id);
}

