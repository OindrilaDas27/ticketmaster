package com.example.dao;

import com.example.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * User Repository Interface
 * Extends JpaRepository to provide CRUD operations for User entity
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username
     * @param username the username to search for
     * @return Optional containing User if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email
     * @param email the email to search for
     * @return Optional containing User if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if user exists by username
     * @param username the username to check
     * @return true if user exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Check if user exists by email
     * @param email the email to check
     * @return true if user exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Approach 1: Using JPQL (Java Persistence Query Language)
     * Find all users where id is not equal to a specific value
     * 
     * @param id the id to exclude
     * @return List of users where id != parameter
     */
    @Query("SELECT u FROM User u WHERE u.id != :id")
    List<User> findAllByIdNot(@Param("id") Long id);

    /**
     * Approach 2: Using JPQL - Find all users where id != 0
     * Direct query without parameters
     * 
     * @return List of users where id != 0
     */
    @Query("SELECT u FROM User u WHERE u.id != 0")
    List<User> findAllWhereIdNotZero();

    /**
     * Approach 3: Using Method Naming Convention
     * Spring Data JPA automatically generates query from method name
     * 
     * @param id the id to exclude
     * @return List of users where id != parameter
     */
    List<User> findByIdNot(Long id);

    /**
     * Approach 4: Using Native SQL Query
     * Use native SQL when you need database-specific features
     * Note: nativeQuery = true means it's raw SQL, not JPQL
     * 
     * @return List of users where id != 0
     */
    @Query(value = "SELECT * FROM users WHERE id != 0", nativeQuery = true)
    List<User> findAllUsersWhereIdNotZeroNative();

    /**
     * Approach 5: Using Native SQL with Parameters
     * 
     * @param id the id to exclude
     * @return List of users where id != parameter
     */
    @Query(value = "SELECT * FROM users WHERE id != :id", nativeQuery = true)
    List<User> findAllUsersWhereIdNot(@Param("id") Long id);

    /**
     * Approach 6: Selecting Specific Fields (Projection)
     * Returns only username and email fields
     * 
     * @param id the id to exclude
     * @return List of Object arrays containing username and email
     */
    @Query("SELECT u.username, u.email FROM User u WHERE u.id != :id")
    List<Object[]> findUsernameAndEmailByIdNot(@Param("id") Long id);

    /**
     * Approach 7: Complex Query Example
     * Find users where id != 0 AND username starts with a prefix
     * 
     * @param id the id to exclude
     * @param usernamePrefix the username prefix
     * @return List of users matching criteria
     */
    @Query("SELECT u FROM User u WHERE u.id != :id AND u.username LIKE :usernamePrefix%")
    List<User> findByIdNotAndUsernameStartingWith(
            @Param("id") Long id, 
            @Param("usernamePrefix") String usernamePrefix);
}

