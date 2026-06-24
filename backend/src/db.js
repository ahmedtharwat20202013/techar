const { Pool } = require('pg');
require('dotenv').config();

// Initialize the database connection pool using environment variables
const pool = new Pool({
    connectionString: process.env.DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/teacher_assistant',
    max: 20, // Max concurrent connections in pool
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 2000,
});

pool.on('error', (err, client) => {
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
