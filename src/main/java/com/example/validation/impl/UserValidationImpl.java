package com.example.validation.impl;

import com.example.dao.UserRepository;
import com.example.dto.UserDTO;
import com.example.validation.UserValidation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * User Validation Implementation
 * Implements business validation rules for User operations
 */
@Component
public class UserValidationImpl implements UserValidation {

    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";
    private static final Pattern emailPattern = Pattern.compile(EMAIL_PATTERN);

    private final UserRepository userRepository;

    @Autowired
    public UserValidationImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void validateForCreate(UserDTO userDto) {
        if (userDto == null) {
            throw new IllegalArgumentException("User data cannot be null");
        }

        validateBasicFields(userDto);

        // Check for duplicate username
        if (userRepository.existsByUsername(userDto.getUsername())) {
            throw new IllegalArgumentException("User already exists with username: " + userDto.getUsername());
        }

        // Check for duplicate email
        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new IllegalArgumentException("User already exists with email: " + userDto.getEmail());
        }
    }

    @Override
    public void validateForUpdate(UserDTO userDto) {
        if (userDto == null) {
            throw new IllegalArgumentException("User data cannot be null");
        }

        if (userDto.getId() == null) {
            throw new IllegalArgumentException("User ID is required for update");
        }

        validateBasicFields(userDto);

        // Check for duplicate username (excluding current user)
        if (userRepository.existsByUsername(userDto.getUsername())) {
            userRepository.findByUsername(userDto.getUsername()).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(userDto.getId())) {
                    throw new IllegalArgumentException("User already exists with username: " + userDto.getUsername());
                }
            });
        }

        // Check for duplicate email (excluding current user)
        if (userRepository.existsByEmail(userDto.getEmail())) {
            userRepository.findByEmail(userDto.getEmail()).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(userDto.getId())) {
                    throw new IllegalArgumentException("User already exists with email: " + userDto.getEmail());
                }
            });
        }
    }

    @Override
    public void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("User ID must be a positive number");
        }
    }

    /**
     * Validate basic user fields
     * @param userDto the user DTO to validate
     */
    private void validateBasicFields(UserDTO userDto) {
        if (userDto.getUsername() == null || userDto.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }

        if (userDto.getUsername().length() < 3 || userDto.getUsername().length() > 100) {
            throw new IllegalArgumentException("Username must be between 3 and 100 characters");
        }

        if (userDto.getEmail() == null || userDto.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (!emailPattern.matcher(userDto.getEmail()).matches()) {
            throw new IllegalArgumentException("Email format is invalid");
        }

        if (userDto.getFirstName() == null || userDto.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }

        if (userDto.getLastName() == null || userDto.getLastName().trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required");
        }
    }
}

