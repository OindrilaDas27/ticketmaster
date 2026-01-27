package com.example.service.impl;

import com.example.ApplicationConstants;
import com.example.dao.impl.UserDaoImpl;
import com.example.dto.UserDTO;
import com.example.entity.User;
import com.example.service.UserService;
import com.example.util.ApplicationUtils;
import com.example.validation.UserValidation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * User Service Implementation
 * Implements business logic for User management operations
 */
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserDaoImpl userDao;
    private final UserValidation userValidation;

    @Autowired
    public UserServiceImpl(UserDaoImpl userDao, UserValidation userValidation) {
        this.userDao = userDao;
        this.userValidation = userValidation;
    }

    @Override
    public UserDTO createUser(UserDTO userDto) {
        // Validate user data
        userValidation.validateForCreate(userDto);

        // Convert DTO to Entity
        User user = ApplicationUtils.convertToEntity(userDto);

        // Save user
        User savedUser = userDao.save(user);

        // Convert Entity to DTO and return
        return ApplicationUtils.convertToDto(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        // Validate ID
        userValidation.validateId(id);

        // Find user by ID
        User user = userDao.findById(id)
                .orElseThrow(() -> new RuntimeException(ApplicationConstants.USER_NOT_FOUND + id));

        // Convert Entity to DTO and return
        return ApplicationUtils.convertToDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        // Get all users
        List<User> users = userDao.findAll();

        // Convert Entity list to DTO list and return
        return users.stream()
                .map(ApplicationUtils::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO updateUser(Long id, UserDTO userDto) {
        // Validate ID
        userValidation.validateId(id);

        // Find existing user
        User existingUser = userDao.findById(id)
                .orElseThrow(() -> new RuntimeException(ApplicationConstants.USER_NOT_FOUND + id));

        // Set ID in DTO for validation
        userDto.setId(id);

        // Validate user data
        userValidation.validateForUpdate(userDto);

        // Update entity with DTO data
        ApplicationUtils.updateEntityFromDto(existingUser, userDto);

        // Save updated user
        User updatedUser = userDao.save(existingUser);

        // Convert Entity to DTO and return
        return ApplicationUtils.convertToDto(updatedUser);
    }

    @Override
    public void deleteUser(Long id) {
        // Validate ID
        userValidation.validateId(id);

        // Check if user exists
        if (!userDao.existsById(id)) {
            throw new RuntimeException(ApplicationConstants.USER_NOT_FOUND + id);
        }

        // Delete user
        userDao.deleteById(id);
    }
}

