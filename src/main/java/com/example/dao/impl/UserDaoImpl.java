package com.example.dao.impl;

import com.example.dao.UserRepository;
import com.example.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * User Data Access Object Implementation
 * Provides data access operations for User entity
 */
@Component
public class UserDaoImpl {

    private final UserRepository userRepository;

    @Autowired
    public UserDaoImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Save a user entity
     * @param user the user to save
     * @return the saved user
     */
    public User save(User user) {
        return userRepository.save(user);
    }

    /**
     * Find user by ID
     * @param id the user ID
     * @return Optional containing User if found
     */
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Find all users
     * @return list of all users
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Delete user by ID
     * @param id the user ID to delete
     */
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * Check if user exists by ID
     * @param id the user ID
     * @return true if exists, false otherwise
     */
    public boolean existsById(Long id) {
        return userRepository.existsById(id);
    }

    /**
     * Find user by username
     * @param username the username
     * @return Optional containing User if found
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Find user by email
     * @param email the email
     * @return Optional containing User if found
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Check if user exists by username
     * @param username the username
     * @return true if exists, false otherwise
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Check if user exists by email
     * @param email the email
     * @return true if exists, false otherwise
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}

