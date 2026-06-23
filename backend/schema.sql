-- =========================================================================
-- DATABASE SCHEMA: SYSTEM DATA INTEGRITY LAYER
-- App Name: مساعد المعلم (Teacher Assistant - Internal Use Only)
-- DBMS: PostgreSQL
-- =========================================================================

-- Enable required extensions if any
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Groups Table
CREATE TABLE IF NOT EXISTS groups (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    start_date DATE DEFAULT CURRENT_DATE NOT NULL, -- Represents the custom starting date of the group
    monthly_fee NUMERIC(10, 2) DEFAULT 0.00 NOT NULL,
    schedule_days VARCHAR(255),
    group_type VARCHAR(20) DEFAULT 'public' NOT NULL CHECK (group_type IN ('public', 'private')), -- Enum constraint at the DB level
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Index for searching groups quickly
CREATE INDEX IF NOT EXISTS idx_groups_created_at ON groups(created_at);

-- 2. Students Table
CREATE TABLE IF NOT EXISTS students (
    id SERIAL PRIMARY KEY,
    group_id INT, -- Associated group, nullable initially or refers to groups
    name VARCHAR(255) NOT NULL,
    parent_phone VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    -- Ensure cascade delete if a group is removed
    CONSTRAINT fk_student_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
);

-- Index for scanning student names and active status quickly
CREATE INDEX IF NOT EXISTS idx_students_active ON students(is_active);
CREATE INDEX IF NOT EXISTS idx_students_name ON students(name);
CREATE INDEX IF NOT EXISTS idx_students_group ON students(group_id);

-- 2. Monthly Payments Table
-- Keeps track of subscription status per month with strict UNIQUE constraint.
CREATE TABLE IF NOT EXISTS monthly_payments (
    id SERIAL PRIMARY KEY,
    student_id INT NOT NULL,
    month VARCHAR(7) NOT NULL, -- Target Month in format: "YYYY-MM"
    amount_charged NUMERIC(10, 2) DEFAULT 0.00 NOT NULL, -- Pro-rated amount or standard fee
    is_paid BOOLEAN DEFAULT FALSE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE, -- Capture exact local timestamp on cash collection
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    -- Cascade deletion rule: If a student profile is deleted, delete all financial history
    CONSTRAINT fk_student_payment FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    -- Strict constraint to prevent duplicate payment cards per student per month
    CONSTRAINT unique_student_monthly_payment UNIQUE (student_id, month)
);

-- Index for monthly status filtering (Paid vs Unpaid reports)
CREATE INDEX IF NOT EXISTS idx_payments_month_status ON monthly_payments(month, is_paid);

-- 3. Attendance Table
-- Records daily attendance with status constraint and date uniqueness
CREATE TABLE IF NOT EXISTS attendance (
    id SERIAL PRIMARY KEY,
    student_id INT NOT NULL,
    group_id INT, -- Associated group for group-specific sheets
    date DATE NOT NULL, -- Format: YYYY-MM-DD
    status VARCHAR(10) NOT NULL CHECK (status IN ('present', 'absent')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    -- Case cascade deletion rule
    CONSTRAINT fk_student_attendance FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    -- Prevent multiple attendance logging for the same student on the same date
    CONSTRAINT unique_student_daily_attendance UNIQUE (student_id, date)
);

-- Index for quick stats retrieval over range queries
CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendance(date);
CREATE INDEX IF NOT EXISTS idx_attendance_student ON attendance(student_id, date);

-- 4. Licenses Table
CREATE TABLE IF NOT EXISTS licenses (
    license_key TEXT PRIMARY KEY,
    is_used BOOLEAN DEFAULT FALSE NOT NULL,
    device_id TEXT,
    activation_token TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    activated_at TIMESTAMP,
    last_check TIMESTAMP
);

-- Index for scanning and verifying licenses quickly
CREATE INDEX IF NOT EXISTS idx_licenses_device ON licenses(device_id);

