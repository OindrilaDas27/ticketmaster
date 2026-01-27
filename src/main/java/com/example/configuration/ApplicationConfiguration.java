package com.example.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Application Configuration
 * Contains Spring Boot configuration settings
 */
@Configuration
@EnableJpaAuditing
public class ApplicationConfiguration {
    // Configuration class for application-wide settings
    // Currently enables JPA auditing for automatic timestamp management
}

