# Ticketmaster User Management API

A production-ready Spring Boot REST API application for managing users, built with clean architecture and industry best practices.

## 🏗️ Architecture

This application follows a **layered architecture** pattern:

- **Controller Layer**: Handles HTTP requests and responses
- **Service Layer**: Contains business logic
- **DAO/Repository Layer**: Handles data access operations
- **Entity Layer**: Database entities (JPA)
- **DTO Layer**: Data Transfer Objects for API communication
- **Validation Layer**: Business validation rules
- **Utility Layer**: Common utility methods
- **Configuration Layer**: Application configuration

## 📋 Prerequisites

Before running the application, ensure you have the following installed:

- **Java 17** or higher
- **Maven 3.6+**
- **PostgreSQL 12+** (running and accessible)
- **Environment Variables** configured (see below)

## 🔧 Environment Variables

The application uses the following environment variables for database configuration:

| Variable | Description | Default Value |
|----------|-------------|---------------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `ticketmaster_db` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |

### Setting Environment Variables

#### Linux/macOS:
```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=ticketmaster_db
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
```

#### Windows (Command Prompt):
```cmd
set DB_HOST=localhost
set DB_PORT=5432
set DB_NAME=ticketmaster_db
set DB_USERNAME=postgres
set DB_PASSWORD=your_password
```

#### Windows (PowerShell):
```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="ticketmaster_db"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your_password"
```

## 🗄️ Database Setup

1. Create a PostgreSQL database:
```sql
CREATE DATABASE ticketmaster_db;
```

2. The application will automatically create the `users` table and insert sample data on startup using `data.sql`.

## 🚀 Running the Application

### Method 1: Using Maven Spring Boot Plugin

```bash
mvn spring-boot:run
```

### Method 2: Build and Run JAR

```bash
# Build the application
mvn clean package

# Run the JAR file
java -jar target/ticketmaster-1.0.0.jar
```

### Method 3: Using IDE

Run the `Application.java` class directly from your IDE (IntelliJ IDEA, Eclipse, etc.).

## 🌐 API Base URL

Once the application is running, the API will be available at:

```
http://localhost:8080/api
```

## 📡 API Endpoints

### 1. Create User
**POST** `/api/users`

**Request Body:**
```json
{
  "username": "john_doe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1-555-0101"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phoneNumber": "+1-555-0101"
  }'
```

### 2. Get User by ID
**GET** `/api/users/{id}`

**Example cURL:**
```bash
curl -X GET http://localhost:8080/api/users/1
```

### 3. Get All Users
**GET** `/api/users`

**Example cURL:**
```bash
curl -X GET http://localhost:8080/api/users
```

### 4. Update User
**PUT** `/api/users/{id}`

**Request Body:**
```json
{
  "username": "john_doe_updated",
  "email": "john.doe.updated@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1-555-0101"
}
```

**Example cURL:**
```bash
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe_updated",
    "email": "john.doe.updated@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phoneNumber": "+1-555-0101"
  }'
```

### 5. Delete User
**DELETE** `/api/users/{id}`

**Example cURL:**
```bash
curl -X DELETE http://localhost:8080/api/users/1
```

## 📝 Response Format

All API responses follow a consistent format:

**Success Response:**
```json
{
  "status": "success",
  "message": "Success message",
  "data": { ... }
}
```

**Error Response:**
```json
{
  "status": "error",
  "message": "Error message"
}
```

## ✅ Validation Rules

- **Username**: Required, 3-100 characters, must be unique
- **Email**: Required, valid email format, must be unique
- **First Name**: Required, max 100 characters
- **Last Name**: Required, max 100 characters
- **Phone Number**: Optional, max 20 characters

## 🛠️ Technology Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring Data JPA**
- **Hibernate**
- **PostgreSQL**
- **Maven**
- **Jakarta Validation**

## 📁 Project Structure

```
src/main/java/com/example/
├── Application.java
├── ApplicationConstants.java
├── configuration/
│   └── ApplicationConfiguration.java
├── controller/
│   └── UserController.java
├── service/
│   ├── UserService.java
│   └── impl/
│       └── UserServiceImpl.java
├── dao/
│   ├── UserRepository.java
│   └── impl/
│       └── UserDaoImpl.java
├── dto/
│   └── UserDto.java
├── entity/
│   └── User.java
├── validation/
│   ├── UserValidation.java
│   └── impl/
│       └── UserValidationImpl.java
└── util/
    └── ApplicationUtils.java
```

## 🧪 Testing the API

You can test the API using:
- **cURL** (examples provided above)
- **Postman**
- **Insomnia**
- **Any HTTP client**

## 🔍 Logging

The application logs SQL queries and other debug information. Check the console output for details.

## 📄 License

This project is for demonstration purposes.

