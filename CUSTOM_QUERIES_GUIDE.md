# Custom Queries in Spring Data JPA - Guide

This guide shows different ways to write custom queries in Spring Data JPA, specifically for queries like `SELECT * FROM users WHERE id != 0`.

## 📚 Approaches Overview

There are **7 different approaches** to write custom queries, each with different use cases:

---

## 1️⃣ **JPQL with Parameters** (Recommended for most cases)

**Best for:** Type-safe queries that work across different databases

```java
@Query("SELECT u FROM User u WHERE u.id != :id")
List<User> findAllByIdNot(@Param("id") Long id);
```

**Usage:**
```java
List<User> users = userRepository.findAllByIdNot(0L);
```

**Notes:**
- `u` is an alias for the `User` entity
- `:id` is a named parameter (use `@Param` to bind)
- Works with entity names, not table names

---

## 2️⃣ **JPQL without Parameters** (Hardcoded values)

**Best for:** Simple queries with fixed values

```java
@Query("SELECT u FROM User u WHERE u.id != 0")
List<User> findAllWhereIdNotZero();
```

**Usage:**
```java
List<User> users = userRepository.findAllWhereIdNotZero();
```

**Note:** Less flexible but cleaner for fixed queries

---

## 3️⃣ **Method Naming Convention** (Simplest)

**Best for:** Simple queries that follow naming patterns

```java
List<User> findByIdNot(Long id);
```

**Usage:**
```java
List<User> users = userRepository.findByIdNot(0L);
```

**Supported Keywords:**
- `And`, `Or`
- `Is`, `Equals`
- `Not`, `IsNot`
- `Between`, `LessThan`, `GreaterThan`
- `Like`, `Containing`, `StartingWith`, `EndingWith`
- `In`, `NotIn`
- `Null`, `NotNull`

**More Examples:**
```java
List<User> findByUsernameContaining(String part);
List<User> findByEmailLike(String pattern);
List<User> findByIdIn(List<Long> ids);
List<User> findByFirstNameAndLastName(String first, String last);
```

---

## 4️⃣ **Native SQL Query** (Direct SQL)

**Best for:** Database-specific features or complex SQL

```java
@Query(value = "SELECT * FROM users WHERE id != 0", nativeQuery = true)
List<User> findAllUsersWhereIdNotZeroNative();
```

**Usage:**
```java
List<User> users = userRepository.findAllUsersWhereIdNotZeroNative();
```

**Notes:**
- Uses actual table/column names (`users`, not `User`)
- `nativeQuery = true` means raw SQL
- Less portable (database-specific)

---

## 5️⃣ **Native SQL with Parameters**

**Best for:** Native SQL with dynamic values

```java
@Query(value = "SELECT * FROM users WHERE id != :id", nativeQuery = true)
List<User> findAllUsersWhereIdNot(@Param("id") Long id);
```

**Usage:**
```java
List<User> users = userRepository.findAllUsersWhereIdNot(0L);
```

---

## 6️⃣ **Projection Queries** (Select specific fields)

**Best for:** When you only need certain fields (performance optimization)

```java
@Query("SELECT u.username, u.email FROM User u WHERE u.id != :id")
List<Object[]> findUsernameAndEmailByIdNot(@Param("id") Long id);
```

**Usage:**
```java
List<Object[]> results = userRepository.findUsernameAndEmailByIdNot(0L);
for (Object[] row : results) {
    String username = (String) row[0];
    String email = (String) row[1];
    System.out.println(username + " - " + email);
}
```

**Alternative: Create a DTO Projection:**
```java
public interface UserProjection {
    String getUsername();
    String getEmail();
}

@Query("SELECT u.username as username, u.email as email FROM User u WHERE u.id != :id")
List<UserProjection> findUsernameAndEmailByIdNot(@Param("id") Long id);
```

---

## 7️⃣ **Complex Queries** (Multiple conditions)

**Best for:** Queries with multiple WHERE conditions

```java
@Query("SELECT u FROM User u WHERE u.id != :id AND u.username LIKE :usernamePrefix%")
List<User> findByIdNotAndUsernameStartingWith(
        @Param("id") Long id, 
        @Param("usernamePrefix") String usernamePrefix);
```

**Usage:**
```java
List<User> users = userRepository.findByIdNotAndUsernameStartingWith(0L, "john");
```

---

## 🔍 Quick Comparison

| Approach | When to Use | Pros | Cons |
|----------|-------------|------|------|
| **JPQL with @Query** | Most cases | Type-safe, portable, readable | More verbose |
| **Method Naming** | Simple queries | Very concise, no SQL needed | Limited to simple patterns |
| **Native SQL** | Complex/DB-specific | Full SQL power, familiar syntax | Not portable, less type-safe |
| **Projection** | Performance optimization | Returns only needed data | More complex result handling |

---

## 📝 Your Specific Example

You asked: **"SELECT name FROM users WHERE id != 0"**

Since the `User` entity has `firstName` and `lastName` (not `name`), here are adapted examples:

### Option A: Get all users (excluding id = 0)
```java
@Query("SELECT u FROM User u WHERE u.id != 0")
List<User> findAllWhereIdNotZero();
```

### Option B: Get only first names
```java
@Query("SELECT u.firstName FROM User u WHERE u.id != 0")
List<String> findFirstNamesWhereIdNotZero();
```

### Option C: Get first name and last name
```java
@Query("SELECT u.firstName, u.lastName FROM User u WHERE u.id != 0")
List<Object[]> findNamesWhereIdNotZero();
```

### Option D: Using native SQL
```java
@Query(value = "SELECT first_name, last_name FROM users WHERE id != 0", nativeQuery = true)
List<Object[]> findNamesWhereIdNotZeroNative();
```

---

## 🚀 How to Use in Your Code

1. **Add the method to `UserRepository.java`** (already done ✅)

2. **Use it in `UserDaoImpl.java`:**
```java
public List<User> findAllByIdNot(Long id) {
    return userRepository.findAllByIdNot(id);
}
```

3. **Use it in `UserServiceImpl.java`:**
```java
public List<UserDto> getAllUsersExceptId(Long excludeId) {
    List<User> users = userDao.findAllByIdNot(excludeId);
    return users.stream()
        .map(ApplicationUtils::convertToDto)
        .collect(Collectors.toList());
}
```

4. **Add endpoint in `UserController.java`:**
```java
@GetMapping("/exclude/{id}")
public ResponseEntity<Map<String, Object>> getAllUsersExcludingId(@PathVariable Long id) {
    List<UserDto> users = userService.getAllUsersExceptId(id);
    // ... return response
}
```

---

## 💡 Best Practices

1. **Prefer JPQL over Native SQL** (more portable)
2. **Use method naming** for simple queries (cleaner code)
3. **Use `@Param`** for named parameters (more readable)
4. **Use projections** when you only need specific fields (better performance)
5. **Add `@Transactional(readOnly = true)`** for read-only queries in service layer

---

## 🔗 Additional Resources

- [Spring Data JPA Query Methods](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.query-methods)
- [JPQL Language Reference](https://docs.oracle.com/javaee/7/tutorial/persistence-querylanguage.htm)
- [Query Annotation Documentation](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.query-methods.at-query)

