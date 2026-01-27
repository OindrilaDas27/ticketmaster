package com.example;

/**
 * Application-wide constants
 * Centralized location for all constant values used across the application
 */
public class ApplicationConstants {

    private ApplicationConstants() {
        // Private constructor to prevent instantiation
    }

    // API Endpoints
    public static final String API_BASE_PATH = "/api";
    public static final String USER_ENDPOINT = "/users";
    public static final String EVENTS_ENDPOINT = "/events";
    public static final String LOCATION_ENDPOINT = "/locations";

    // Error Messages
    public static final String USER_NOT_FOUND = "User not found with id: ";
    public static final String USER_NOT_FOUND_USERNAME = "User not found with username: ";
    public static final String USER_ALREADY_EXISTS = "User already exists with username: ";
    public static final String EMAIL_ALREADY_EXISTS = "User already exists with email: ";
    public static final String INVALID_USER_DATA = "Invalid user data provided";

    // Validation Messages
    public static final String USERNAME_REQUIRED = "Username is required";
    public static final String EMAIL_REQUIRED = "Email is required";
    public static final String FIRST_NAME_REQUIRED = "First name is required";
    public static final String LAST_NAME_REQUIRED = "Last name is required";
    public static final String EMAIL_INVALID_FORMAT = "Email format is invalid";
    public static final String USERNAME_MIN_LENGTH = "Username must be at least 3 characters";
    public static final String USERNAME_MAX_LENGTH = "Username must not exceed 100 characters";

    // HTTP Status Messages
    public static final String SUCCESS = "Success";
    public static final String CREATED = "Created successfully";
    public static final String UPDATED = "Updated successfully";
    public static final String DELETED = "Deleted successfully";
}

