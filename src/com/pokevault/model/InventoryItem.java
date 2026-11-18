package com.pokevault.model;
import java.math.BigDecimal;
import java.math.RoundingMode;
/*
Represents an owned entry in the user's personal vault inventory.
Combines a master Card reference with a ConditionGrade, quantity, and cost basis
to compute real-time valuation and Return on Investment
*/
public class InventoryItem {

    private int inventoryId;
    private Card card;                      //linked master catalog Card (provides card_id FK & base price)
    private ConditionGrade conditionGrade; 
    private BigDecimal purchasePrice;      
    private int quantity;                   //no. of copies owned in this condition

    /*
    cnstructor for new vault entries before our database assigns an inventory_id
    */
    public InventoryItem(Card card, ConditionGrade conditionGrade, BigDecimal purchasePrice, int quantity) {
        this(0, card, conditionGrade, purchasePrice, quantity);
    }

    /*
    cnstrctr for EXISTING vault entries loaded from db via JOIN query.
    */
    public InventoryItem(int inventoryId, Card card, ConditionGrade conditionGrade, BigDecimal purchasePrice, int quantity) {
        if (inventoryId < 0) {
            throw new IllegalArgumentException("Inventory ID cannot be negative.");
        }
        this.inventoryId = inventoryId;
        setCard(card);
        setConditionGrade(conditionGrade);
        setPurchasePrice(purchasePrice);
        setQuantity(quantity);
    }

    /*
    calculates the current market value of ONE copy of this card in its specific grade.
     */
    public BigDecimal getUnitCurrentValue() {
        return conditionGrade.calculateAdjustedValue(card.getBaseMarketPrice());
    }

    /*
    calculates the total current market value across all owned copies
     */
    public BigDecimal getTotalCurrentValue() {
        return getUnitCurrentValue()
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /*
    calculates the total amount originally spent on this stack
     */
    public BigDecimal getTotalCostBasis() {
        return purchasePrice
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }
    /*
    calculates net dollar profit or loss
     */
    public BigDecimal getNetProfitOrLoss() {
        return getTotalCurrentValue()
                .subtract(getTotalCostBasis())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
    calculates t (ROI) as a percentage
     */
    public BigDecimal getRoiPercentage() {
        BigDecimal totalCost = getTotalCostBasis();
        if (totalCost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return getNetProfitOrLoss()
                .divide(totalCost, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public int getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(int inventoryId) {
        if (inventoryId < 0) {
            throw new IllegalArgumentException("Inventory ID must be positive");
        }
        this.inventoryId = inventoryId;
    }

    public Card getCard() {
        return card;
    }

    public void setCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("Inventory item must reference a valid Card.");
        }
        this.card = card;
    }

    public ConditionGrade getConditionGrade() {
        return conditionGrade;
    }

    public void setConditionGrade(ConditionGrade conditionGrade) {
        if (conditionGrade == null) {
            throw new IllegalArgumentException("Condition grade cannot be null.");
        }
        this.conditionGrade = conditionGrade;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        if (purchasePrice == null || purchasePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Purchase price must be valid.");
        }
        this.purchasePrice = purchasePrice.setScale(2, RoundingMode.HALF_UP);
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be atleast 1. Remove the item instead if sold out.");
        }
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return String.format(
            "[Vault ID: %d] %s (%s) | Grade: %s | Qty: %d | Unit Val: $%s | Total Val: $%s | Net P/L: $%s (%s%%)",
            inventoryId,
            card.getName(),
            card.getSetName(),
            conditionGrade.getLabel(),
            quantity,
            getUnitCurrentValue(),
            getTotalCurrentValue(),
            getNetProfitOrLoss(),
            getRoiPercentage()
        );
    }

    //temp TEST
    public static void main(String[] args) {
        System.out.println("Testing InventoryItem Valuation & Validation");

        Card charizard = new Card(1, "Charizard", "Base Set", "Fire", "Holo Rare", new BigDecimal("200.00"));

        //Test PSA 9 (1.80x -> $360/each), Qty 2 ($720 total), Bought at $250/each ($500 cost)
        InventoryItem item = new InventoryItem(10, charizard, ConditionGrade.PSA_9, new BigDecimal("250.00"), 2);
        System.out.println(item);
        System.out.println("Expected Total Val: $720.00 | Net P/L: $220.00 | ROI: 44.00%");

        //Test zero purchase price edge case (pack-pulled/gifted card -> no divide-by-zero crash)
        InventoryItem freePull = new InventoryItem(charizard, ConditionGrade.NEAR_MINT, BigDecimal.ZERO, 1);
        System.out.println("Free Pull ROI: " + freePull.getRoiPercentage() + "% (Expected: 0.00%)");

        //Test invalid quantity validation
        try {
            new InventoryItem(charizard, ConditionGrade.NEAR_MINT, new BigDecimal("50.00"), 0);
            System.out.println("ERROR: Should have rejected 0 quantity!");
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected quantity error: " + e.getMessage());
        }
    }
};