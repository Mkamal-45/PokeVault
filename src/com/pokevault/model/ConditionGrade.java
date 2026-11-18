package com.pokevault.model;
import java.math.BigDecimal;
import java.math.RoundingMode;
/*
Represents the graded condition of a Pokemon card.
Encapsulates both the display/database label and the financial valuation multiplier
so pricing rules live in one single source of truth.
*/
public enum ConditionGrade {
    // Defined constants pairing readable labels with market multipliers
    PSA_10("PSA 10", new BigDecimal("3.50")),
    PSA_9("PSA 9", new BigDecimal("1.80")),
    NEAR_MINT("Near Mint", new BigDecimal("1.00")),
    LIGHTLY_PLAYED("Lightly Played", new BigDecimal("0.80")),
    MODERATELY_PLAYED("Moderately Played", new BigDecimal("0.60")),
    DAMAGED("Damaged", new BigDecimal("0.30"));
    private final String label;
    private final BigDecimal multiplier;
    // Enum cnstrctrs are implicitly (private); (fixed domain rules)
    ConditionGrade(String label, BigDecimal multiplier) {
        this.label = label;
        this.multiplier = multiplier;
    };

    public String getLabel() {
        return label;
    };

    public BigDecimal getMultiplier() {
        return multiplier;
    };

    /*
    Applies this condition's multiplier to a card's base market price.
    rounds to 2 decimal places using standard financial half-up rounding.
    */
    public BigDecimal calculateAdjustedValue(BigDecimal baseMarketPrice) {
        if (baseMarketPrice == null || baseMarketPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Base market price must be non-negative.");
        }
        return baseMarketPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    };
    /*
    Safely parses user console input or database strings into a valid ConditionGrade.
    Supports both enum names (e.g., "PSA_10") and readable labels (e.g., "PSA 10").
    */
    public static ConditionGrade fromString(String input) {
        if(input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Condition grade cannot be empty.");
        }

        String normalized =input.trim();
        for (ConditionGrade grade : ConditionGrade.values()) {
            if (grade.label.equalsIgnoreCase(normalized) || grade.name().equalsIgnoreCase(normalized)) {
                return grade;
            }
        }

        throw new IllegalArgumentException(
            "Invalid condition grade: '" + input + "'. Valid options: PSA 10, PSA 9, Near Mint, Lightly Played, Moderately Played, Damaged."
        );
    }

    //Temporary TEST BLCK (we will cmmnt out after testing)
    public static void main(String[] args) {
        System.out.println("Testing ConditionGrade");
        //Test multiplier calc ($100.00 base card in PSA 10 >>$350.00)
        ConditionGrade psa10 = ConditionGrade.fromString("psa 10");
        BigDecimal adjusted = psa10.calculateAdjustedValue(new BigDecimal("100.00"));
        System.out.println("PSA 10 on $100.00 card = $" + adjusted + " (Expected: $350.00)");
        //Damaged card multiplier ($50.00 base card >> $15.00)
        ConditionGrade damaged = ConditionGrade.fromString("Damaged");
        System.out.println("Damaged on $50.00 card = $" + damaged.calculateAdjustedValue(new BigDecimal("50.00")) + " (Expected: $15.00)");
        //Invalid input error handling (for demo mainly)
        try {
            ConditionGrade.fromString("PSA 99");
            System.out.println("ERROR: Should have rejected PSA 99!");
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected error: " + e.getMessage());
        }
    }
};