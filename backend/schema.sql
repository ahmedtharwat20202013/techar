-- Enable UUID extension (Neon supports this)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Drop old tables if migrating from old system
DROP TABLE IF EXISTS validation_logs CASCADE;
DROP TABLE IF EXISTS abuse_logs CASCADE;
DROP TABLE IF EXISTS licenses CASCADE;
DROP TABLE IF EXISTS admin_sessions CASCADE;
DROP TABLE IF EXISTS admins CASCADE;

-- Admins table
CREATE TABLE IF NOT EXISTS admins (
    id SERIAL PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Licenses table (Neon compatible)
CREATE TABLE IF NOT EXISTS licenses (
    id SERIAL PRIMARY KEY,
    license_key TEXT NOT NULL UNIQUE,
    license_key_hash TEXT,
    is_used BOOLEAN DEFAULT FALSE,
    device_id TEXT,
    device_fingerprint TEXT,
    activation_token TEXT,
    user_name TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP,
    last_check TIMESTAMP,
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    max_activations INTEGER DEFAULT 1,
    current_activations INTEGER DEFAULT 0
);

-- Validation logs
CREATE TABLE IF NOT EXISTS validation_logs (
    id SERIAL PRIMARY KEY,
    license_key TEXT,
    device_id TEXT,
    ip_address TEXT NOT NULL,
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    success BOOLEAN NOT NULL,
    error_reason TEXT
);

-- Abuse logs
CREATE TABLE IF NOT EXISTS abuse_logs (
    id SERIAL PRIMARY KEY,
    license_key TEXT,
    device_id TEXT,
    reason TEXT,
    ip_address TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_licenses_key ON licenses(license_key);
CREATE INDEX IF NOT EXISTS idx_licenses_device ON licenses(device_id);
CREATE INDEX IF NOT EXISTS idx_licenses_hash ON licenses(license_key_hash);
CREATE INDEX IF NOT EXISTS idx_logs_key ON validation_logs(license_key);
CREATE INDEX IF NOT EXISTS idx_logs_attempted ON validation_logs(attempted_at);
