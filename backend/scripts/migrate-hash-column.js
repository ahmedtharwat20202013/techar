const { Pool } = require('pg');
const crypto = require('crypto');
const path = require('path');

// Try loading env from root or backend folder
require('dotenv').config({ path: path.join(__dirname, '../.env') });
require('dotenv').config({ path: path.join(__dirname, '../../.env') });

const connectionString = process.env.DATABASE_URL;
const LICENSE_ENCRYPTION_KEY = process.env.LICENSE_ENCRYPTION_KEY;

if (!connectionString) {
    console.error('[FATAL] DATABASE_URL is missing. Check your environment configuration.');
    process.exit(1);
}

if (!LICENSE_ENCRYPTION_KEY) {
    console.error('[FATAL] LICENSE_ENCRYPTION_KEY is missing. Check your environment configuration.');
    process.exit(1);
}

const pool = new Pool({
    connectionString,
    ssl: { rejectUnauthorized: false }
});

function decryptLicenseKey(encrypted, masterKey = LICENSE_ENCRYPTION_KEY) {
    try {
        const parts = encrypted.split(':');
        if (parts.length < 3) return null;
        const iv = Buffer.from(parts[0], 'hex');
        const authTag = Buffer.from(parts[1], 'hex');
        const encryptedText = parts[2];
        const decipher = crypto.createDecipheriv('aes-256-gcm', Buffer.from(masterKey, 'hex'), iv);
        decipher.setAuthTag(authTag);
        let decrypted = decipher.update(encryptedText, 'hex', 'utf8');
        decrypted += decipher.final('utf8');
        return decrypted;
    } catch (e) {
        return null;
    }
}

function getLicenseKeyHash(rawKey) {
    return crypto.createHash('sha256').update(rawKey).digest('hex');
}

async function run() {
    console.log('[Migration] Starting migration...');
    const client = await pool.connect();
    try {
        // Ensure column exists
        console.log('[Migration] Verifying schema column and index...');
        await client.query('ALTER TABLE licenses ADD COLUMN IF NOT EXISTS license_key_hash TEXT');
        await client.query('CREATE INDEX IF NOT EXISTS idx_licenses_hash ON licenses(license_key_hash)');
        
        // Fetch rows missing hash
        const { rows } = await client.query('SELECT license_key FROM licenses WHERE license_key_hash IS NULL');
        console.log(`[Migration] Found ${rows.length} licenses needing hash migration.`);
        
        let migratedCount = 0;
        for (const row of rows) {
            const rawKey = decryptLicenseKey(row.license_key);
            if (rawKey) {
                const hash = getLicenseKeyHash(rawKey);
                await client.query(
                    'UPDATE licenses SET license_key_hash = $1 WHERE license_key = $2',
                    [hash, row.license_key]
                );
                migratedCount++;
            } else {
                // If it can't be decrypted, maybe it's legacy plaintext?
                const hash = getLicenseKeyHash(row.license_key);
                await client.query(
                    'UPDATE licenses SET license_key_hash = $1 WHERE license_key = $2',
                    [hash, row.license_key]
                );
                migratedCount++;
            }
        }
        
        console.log(`[Migration] Successfully migrated ${migratedCount}/${rows.length} licenses.`);
    } catch (err) {
        console.error('[Migration] Failed:', err);
    } finally {
        client.release();
        await pool.end();
    }
}

run();
