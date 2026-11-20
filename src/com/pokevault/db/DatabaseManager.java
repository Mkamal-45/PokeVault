package com.pokevault.db;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
/*
manages SQLite JDBC connections and initializes the relational database schema.
centralizes database configuration and enforces foreign key integrity on every connection.
*/
public class DatabaseManager {
    private static final String DEFAULT_DB_URL = "jdbc:sqlite:data/pokevault.db";
    private final String dbUrl;
    public DatabaseManager(){
        this(DEFAULT_DB_URL);
    }
    //this constructor allows tests to point to an isolated test database
    public DatabaseManager(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    public Connection getConnection() throws SQLException {
        Connection conn =DriverManager.getConnection(dbUrl);
        try (Statement stmt =conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys =ON;");
        }
        return conn;
    }

    public void initializeDatabase() throws SQLException {
        //stores reference Pokémon cards imported from CSV
        String createCardsTableSql="""
            CREATE TABLE IF NOT EXISTS cards (
                card_id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                set_name TEXT NOT NULL,
                card_type TEXT NOT NULL,
                rarity TEXT NOT NULL,
                base_market_price REAL NOT NULL CHECK (base_market_price >= 0),
                UNIQUE(name, set_name, rarity)
            );
            """;

        String createInventoryTableSql="""
            CREATE TABLE IF NOT EXISTS inventory (
                inventory_id INTEGER PRIMARY KEY AUTOINCREMENT,
                card_id INTEGER NOT NULL,
                condition_grade TEXT NOT NULL,
                purchase_price REAL NOT NULL CHECK (purchase_price>= 0),
                quantity INTEGER NOT NULL CHECK (quantity > 0),
                FOREIGN KEY (card_id) REFERENCES cards(card_id) ON DELETE RESTRICT
            );
            """;

        try (Connection conn=getConnection();
            Statement stmt= conn.createStatement()) {
            stmt.execute(createCardsTableSql);
            stmt.execute(createInventoryTableSql);
        }
    }

    //test blck
    public static void main(String[] args) {
        System.out.println("=== Testing DatabaseManager & SQLite Constraints ===");
        DatabaseManager dbManager = new DatabaseManager();

        try {
            //initialize schema 
            dbManager.initializeDatabase();
            System.out.println("Database and tables initialized successfully!");

            try (Connection conn =dbManager.getConnection();
                Statement stmt =conn.createStatement()) {

                // verify pragma foreign_keys is active(1=on, 0 =off)
                ResultSet rs =stmt.executeQuery("PRAGMA foreign_keys;");
                if (rs.next()){
                    System.out.println("Foreign Key enforcement active: " + (rs.getInt(1) == 1) + " (Expected: true)");
                }
                try {
                    stmt.executeUpdate(
                        "INSERT INTO inventory (card_id, condition_grade, purchase_price, quantity) " +
                        "VALUES (9999, 'PSA 10', 100.00, 1);"
                    );
                    System.out.println("ERROR: Foreign key constraint did NOT block invalid card_id!");
                } catch (SQLException fkEx) {
                    System.out.println("Caught expected SQL Foreign Key error: " + fkEx.getMessage());
                }
            }
        } catch(SQLException e) {
            System.err.println("Database test failed: " + e.getMessage());
            e.printStackTrace();
    
        }

    };


};
