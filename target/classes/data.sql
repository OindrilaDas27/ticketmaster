-- Create users table if it doesn't exist
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample data
INSERT INTO users (username, email, first_name, last_name, phone_number) VALUES
('john_doe', 'john.doe@example.com', 'John', 'Doe', '+1-555-0101'),
('jane_smith', 'jane.smith@example.com', 'Jane', 'Smith', '+1-555-0102'),
('bob_johnson', 'bob.johnson@example.com', 'Bob', 'Johnson', '+1-555-0103')
ON CONFLICT (username) DO NOTHING;

