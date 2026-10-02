package za.hack.remit.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    // Runs an in-memory H2 database (no SQL files or database installation needed)
    private static final String URL = "jdbc:h2:mem:moneyhome;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void initDatabase() {
        String createTablesSql = """
            CREATE TABLE IF NOT EXISTS users (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                phone_number VARCHAR(20) UNIQUE NOT NULL,
                pin_hash VARCHAR(255),
                preferred_language VARCHAR(5) DEFAULT 'en',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS transfers (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                reference VARCHAR(20) UNIQUE NOT NULL,
                session_id VARCHAR(64),
                sender_phone VARCHAR(20) NOT NULL,
                recipient_phone VARCHAR(20) NOT NULL,
                recipient_name VARCHAR(100) NOT NULL,
                amount_zar DECIMAL(12, 2) NOT NULL,
                fee_zar DECIMAL(12, 2) NOT NULL,
                total_zar DECIMAL(12, 2) NOT NULL,
                receive_usd DECIMAL(12, 2) NOT NULL,
                exchange_rate DECIMAL(10, 4) NOT NULL,
                status VARCHAR(30) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS ussd_sessions (
                session_id VARCHAR(64) PRIMARY KEY,
                phone_number VARCHAR(20) NOT NULL,
                current_screen_id VARCHAR(50) NOT NULL,
                session_data VARCHAR(1000),
                last_accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createTablesSql);
            System.out.println("✅ Database tables created successfully!");
        } catch (Exception e) {
            System.err.println("❌ Database setup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}