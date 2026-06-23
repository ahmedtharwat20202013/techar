const { Pool } = require('pg');
require('dotenv').config();

const connectionString = process.env.DATABASE_URL;
if (!connectionString) {
    console.error('[FATAL] DATABASE_URL environment variable is required');
    process.exit(1);
}

const isProduction = process.env.NODE_ENV === 'production';

// Neon PostgreSQL specific configuration
const pool = new Pool({
    connectionString,
    max: 20,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 10000,
    ssl: (connectionString.includes('neon.tech') || isProduction) ? {
        rejectUnauthorized: false // Set to false to allow serverless driver certificates comfortably
    } : (connectionString.includes('sslmode=require') ? { rejectUnauthorized: false } : false)
});

pool.on('error', (err) => {
    console.error('Unexpected error on idle database client', err);
});

module.exports = {
    /**
     * Standard query helper
     */
    query: (text, params) => pool.query(text, params),

    /**
     * Executes helper in a secure atomic transaction context.
     * Guaranteed rollbacks on failure to shield systems from partial/broken state.
     */
    executeTransaction: async (callback) => {
        const client = await pool.connect();
        try {
            await client.query('BEGIN');
            const result = await callback(client);
            await client.query('COMMIT');
            return result;
        } catch (e) {
            await client.query('ROLLBACK');
            throw e;
        } finally {
            client.release();
        }
    },
    closePool: () => pool.end()
};
