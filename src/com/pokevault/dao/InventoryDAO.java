package com.pokevault.dao;
import com.pokevault.db.DatabaseManager;
import com.pokevault.model.Card;
import com.pokevault.model.ConditionGrade;
import com.pokevault.model.InventoryItem;
import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
data access object for the personal inventory table.
*/
public class InventoryDAO {
    private final DatabaseManager dbManager;
    public InventoryDAO(DatabaseManager dbManager) {
        if (dbManager==null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null.");
        }
        this.dbManager= dbManager;
    }

    public InventoryItem addInventoryItem(InventoryItem item) throws SQLException {
        String sql="""
            INSERT INTO inventory (card_id, condition_grade, purchase_price, quantity)
            VALUES (?,?,?,?);
            """;

        try (Connection conn =dbManager.getConnection();
            PreparedStatement pstmt=conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, item.getCard().getCardId());
            pstmt.setString(2, item.getConditionGrade().getLabel());
            pstmt.setDouble(3, item.getPurchasePrice().doubleValue());
            pstmt.setInt(4, item.getQuantity());

            int affectedRows =pstmt.executeUpdate();
            if (affectedRows==0) {
                throw new SQLException("Adding inventory item failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    item.setInventoryId(generatedKeys.getInt(1));
                }
            }
            return item;
        }
    }

    public List<InventoryItem> getAllInventoryItems() throws SQLException {
        String sql = """
            SELECT
                i.inventory_id,
                i.condition_grade,
                i.purchase_price,
                i.quantity,
                c.card_id,
                c.name,
                c.set_name,
                c.card_type,
                c.rarity,
                c.base_market_price
            FROM inventory i
            INNER JOIN cards c ON i.card_id = c.card_id
            ORDER BY i.inventory_id ASC;
            """;

        List<InventoryItem> vaultItems = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()){
                vaultItems.add(mapRowToInventoryItem(rs));
            }
        }
        return vaultItems;
    }

    public Optional<InventoryItem> findById(int inventoryId) throws SQLException {
        String sql = """
            SELECT
                i.inventory_id,
                i.condition_grade,
                i.purchase_price,
                i.quantity,
                c.card_id,
                c.name,
                c.set_name,
                c.card_type,
                c.rarity,
                c.base_market_price
            FROM inventory i
            INNER JOIN cards c ON i.card_id = c.card_id
            WHERE i.inventory_id = ?;
            """;

        try (Connection conn=dbManager.getConnection();
            PreparedStatement pstmt= conn.prepareStatement(sql)) {

            pstmt.setInt(1,inventoryId);
            try (ResultSet rs= pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToInventoryItem(rs));
                }
            }
        }
        return Optional.empty();
    }


    public boolean updateConditionGrade(int inventoryId, ConditionGrade newGrade) throws SQLException {
        if (newGrade== null) {
            throw new IllegalArgumentException("New condition grade cannot be null.");
        }

        String sql= "UPDATE inventory SET condition_grade = ? WHERE inventory_id = ?;";

        try (Connection conn = dbManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newGrade.getLabel());
            pstmt.setInt(2, inventoryId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean updateQuantity(int inventoryId, int newQuantity) throws SQLException {
        if (newQuantity<= 0){
            throw new IllegalArgumentException("Quantity must be at least 1. Use removeInventoryItem() if sold out.");
        }

        String sql ="UPDATE inventory SET quantity = ? WHERE inventory_id = ?;";

        try (Connection conn =dbManager.getConnection();
             PreparedStatement pstmt =conn.prepareStatement(sql)) {

            pstmt.setInt(1, newQuantity);
            pstmt.setInt(2, inventoryId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean removeInventoryItem(int inventoryId) throws SQLException {
        String sql="DELETE FROM inventory WHERE inventory_id = ?;";

        try (Connection conn= dbManager.getConnection();
            PreparedStatement pstmt= conn.prepareStatement(sql)) {

            pstmt.setInt(1, inventoryId);
            return pstmt.executeUpdate() > 0;
        }
    }

    private InventoryItem mapRowToInventoryItem(ResultSet rs) throws SQLException {
        Card card = new Card(
            rs.getInt("card_id"),
            rs.getString("name"),
            rs.getString("set_name"),
            rs.getString("card_type"),
            rs.getString("rarity"),
            BigDecimal.valueOf(rs.getDouble("base_market_price"))
        );

        ConditionGrade grade= ConditionGrade.fromString(rs.getString("condition_grade"));

        return new InventoryItem(
            rs.getInt("inventory_id"),
            card,
            grade,
            BigDecimal.valueOf(rs.getDouble("purchase_price")),
            rs.getInt("quantity")
        );
    }

    //test blck
    public static void main(String[] args) {
        System.out.println("Testing InventoryDAO CRUD, JOINs & Foreign Keys");
        String testDbPath = "data/test_inventory_dao.db";
        File testDbFile = new File(testDbPath);
        if (testDbFile.exists()) {
            testDbFile.delete();
        }

        try {
            DatabaseManager testDb = new DatabaseManager("jdbc:sqlite:" + testDbPath);
            testDb.initializeDatabase();

            CardDAO cardDAO = new CardDAO(testDb);
            InventoryDAO inventoryDAO = new InventoryDAO(testDb);
            Card lugia = cardDAO.insertCard(
                new Card("Lugia", "Neo Genesis", "Colorless", "Holo Rare", new BigDecimal("200.00"))
            );

            InventoryItem vaultEntry = inventoryDAO.addInventoryItem(
                new InventoryItem(lugia, ConditionGrade.NEAR_MINT, new BigDecimal("150.00"), 1)
            );
            System.out.println("Added to vault: " + vaultEntry);

            inventoryDAO.updateConditionGrade(vaultEntry.getInventoryId(), ConditionGrade.PSA_10);
            List<InventoryItem> items = inventoryDAO.getAllInventoryItems();
            System.out.println("After grading upgrade (PSA 10): " + items.get(0));

            try {
                Card ghostCard = new Card(9999, "MissingNo", "Glitch", "Bird", "Secret", new BigDecimal("10.00"));
                inventoryDAO.addInventoryItem(new InventoryItem(ghostCard, ConditionGrade.NEAR_MINT, new BigDecimal("5.00"), 1));
                System.out.println("ERROR: Should have rejected non-existent card_id 9999!");
            } catch (SQLException fkEx) {
                System.out.println("Caught expected SQL Foreign Key error: " + fkEx.getMessage());
            }

            boolean removed = inventoryDAO.removeInventoryItem(vaultEntry.getInventoryId());
            System.out.println("Removed sold card: " + removed + " | Remaining vault items: " + inventoryDAO.getAllInventoryItems().size());

        } catch (SQLException e) {
            System.err.println("InventoryDAO test failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (testDbFile.exists()) {
                testDbFile.delete();
            }
        }

    };




};