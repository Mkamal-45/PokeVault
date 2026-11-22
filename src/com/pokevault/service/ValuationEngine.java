package com.pokevault.service;
import com.pokevault.dao.CardDAO;
import com.pokevault.dao.InventoryDAO;
import com.pokevault.db.DatabaseManager;
import com.pokevault.model.Card;
import com.pokevault.model.ConditionGrade;
import com.pokevault.model.InventoryItem;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/*
core financial calculation class for our app
aggregates condition-adjusted market values, cost basis, net profit/loss, ROI,
and identifies the highest-value card in the user's portfolio.
*/
public class ValuationEngine {
    private final InventoryDAO inventoryDAO;
    private static final Comparator<InventoryItem> BY_UNIT_VALUE_DESC =
            Comparator.comparing(InventoryItem::getUnitCurrentValue).reversed().thenComparing(item -> item.getCard().getName(), String.CASE_INSENSITIVE_ORDER);

    public ValuationEngine(InventoryDAO inventoryDAO) {
        if (inventoryDAO ==null) {throw new IllegalArgumentException("InventoryDAO cannot be null.");}
        this.inventoryDAO = inventoryDAO;
    }

    public record PortfolioSummary(
        int uniqueVaultEntries,
        int totalPhysicalCards,
        BigDecimal totalCostBasis,
        BigDecimal totalNetWorth,
        BigDecimal netProfitOrLoss,
        BigDecimal overallRoiPercentage,
        Optional<InventoryItem> mostValuableItem
    ) {}


    public PortfolioSummary generatePortfolioSummary() throws SQLException {
        List<InventoryItem> vaultItems =inventoryDAO.getAllInventoryItems();
        return calculateSummary(vaultItems);
    }

    /**
     * Pure calculation method operating on an ArrayList of InventoryItems.
     * Kept separate from database I/O so financial math is deterministic and easy to verify.
     */
    public PortfolioSummary calculateSummary(List<InventoryItem> items) {
        int uniqueEntries =items.size();
        int totalPhysicalCards =0;
        BigDecimal totalCostBasis =BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalNetWorth= BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        InventoryItem topCard= null;

        for (InventoryItem item : items) {
            totalPhysicalCards += item.getQuantity();
            totalCostBasis= totalCostBasis.add(item.getTotalCostBasis());
            totalNetWorth= totalNetWorth.add(item.getTotalCurrentValue());

            // Track the single card with the highest condition-adjusted unit market value
            if (topCard==null || item.getUnitCurrentValue().compareTo(topCard.getUnitCurrentValue()) > 0) {
                topCard=item;
            }
        }

        BigDecimal netProfitOrLoss =totalNetWorth
                .subtract(totalCostBasis)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal overallRoiPercentage;
        if (totalCostBasis.compareTo(BigDecimal.ZERO)== 0) {
            overallRoiPercentage =BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            overallRoiPercentage =netProfitOrLoss
                    .divide(totalCostBasis, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return new PortfolioSummary(
            uniqueEntries,
            totalPhysicalCards,
            totalCostBasis,
            totalNetWorth,
            netProfitOrLoss,
            overallRoiPercentage,
            Optional.ofNullable(topCard)
        );
    }

    public List<InventoryItem> getVaultSortedByUnitValue() throws SQLException {
        List<InventoryItem> sorted= new ArrayList<>(inventoryDAO.getAllInventoryItems());
        sorted.sort(BY_UNIT_VALUE_DESC);
        return sorted;
    }

    // test blck
    public static void main(String[] args) {
        System.out.println("Testing ValuationEngine Portfolio Analytics");
        String testDbPath ="data/test_valuation_engine.db";
        File testDbFile =new File(testDbPath);
        if (testDbFile.exists()) {
            testDbFile.delete();
        }

        try {
            DatabaseManager testDb =new DatabaseManager("jdbc:sqlite:" + testDbPath);
            testDb.initializeDatabase();

            CardDAO cardDAO =new CardDAO(testDb);
            InventoryDAO inventoryDAO =new InventoryDAO(testDb);
            ValuationEngine engine =new ValuationEngine(inventoryDAO);

            PortfolioSummary emptySummary =engine.generatePortfolioSummary();
            System.out.println("Empty Vault Net Worth: $" + emptySummary.totalNetWorth()
                    + " | Top Card Present: " + emptySummary.mostValuableItem().isPresent() + " (Expected: $0.00 | false)");

            Card charizard = cardDAO.insertCard(new Card("Charizard", "Base Set", "Fire", "Holo Rare", new BigDecimal("350.00")));
            Card blastoise = cardDAO.insertCard(new Card("Blastoise", "Base Set", "Water", "Holo Rare", new BigDecimal("180.00")));
            Card pikachu = cardDAO.insertCard(new Card("Pikachu", "Jungle", "Electric", "Common", new BigDecimal("4.50")));
            inventoryDAO.addInventoryItem(new InventoryItem(charizard, ConditionGrade.PSA_10, new BigDecimal("800.00"), 1));
            inventoryDAO.addInventoryItem(new InventoryItem(blastoise, ConditionGrade.LIGHTLY_PLAYED, new BigDecimal("150.00"), 2));
            inventoryDAO.addInventoryItem(new InventoryItem(pikachu, ConditionGrade.NEAR_MINT, BigDecimal.ZERO, 1));

            PortfolioSummary summary = engine.generatePortfolioSummary();
            System.out.println("Unique Entries: " + summary.uniqueVaultEntries() + " | Physical Cards: " + summary.totalPhysicalCards() + " (Expected: 3 | 4)");
            System.out.println("Total Cost Basis: $" + summary.totalCostBasis() + " (Expected: $1100.00)");
            System.out.println("Total Net Worth:  $" + summary.totalNetWorth() + " (Expected: $1517.50)");
            System.out.println("Net Profit/Loss:  $" + summary.netProfitOrLoss() + " (Expected: $417.50)");
            System.out.println("Overall ROI:      " + summary.overallRoiPercentage() + "% (Expected: 37.95%)");
            summary.mostValuableItem().ifPresent(top ->
                System.out.println("Most Valuable Card: " + top.getCard().getName() + " [" + top.getConditionGrade().getLabel() + "] = $" + top.getUnitCurrentValue())
            );

        } catch (SQLException e) {
            System.err.println("ValuationEngine test failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (testDbFile.exists()) {
                testDbFile.delete();
            }
        }




    };






};