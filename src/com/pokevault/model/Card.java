package com.pokevault.model;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
/*
Represents a master Pokemon TCG card in the reference catalog.
Maps directly to the (cards) table in SQLite.
*/
public class Card {
    private int cardId;                 //primary-key
    private String name;                //pkmn name:"Charizard"
    private String setName;             // "Base Set"
    private String cardType;            //"Fire","Water","Electric"
    private String rarity;              //"Holo Rare","Common"
    private BigDecimal baseMarketPrice; //base Near Mint market value in USD/CAD
    
    /*
    Constructor for NEW cards (e.g., parsed from CSV) before SQLite assigns a card_id.
     */
    public Card(String name, String setName, String cardType, String rarity, BigDecimal baseMarketPrice) {
        this(0, name, setName, cardType, rarity, baseMarketPrice);
    }

    /*
    full constructor for EXISTING cards from the potential SQLite database.
     */
    public Card(int cardId, String name, String setName, String cardType, String rarity, BigDecimal baseMarketPrice) {
        if (cardId < 0) {
            throw new IllegalArgumentException("Card ID cannot be negative.");
        }
        this.cardId = cardId;
        setName(name);
        setSetName(setName);
        setCardType(cardType);
        setRarity(rarity);
        setBaseMarketPrice(baseMarketPrice);
    }

    public int getCardId() {
        return cardId;
    }

    public void setCardId(int cardId) {
        if (cardId < 0) {
            throw new IllegalArgumentException("Card ID cannot be negative.");
        }
        this.cardId = cardId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Card name cannot be blank.");
        }
        this.name = name.trim();
    }

    public String getSetName() {
        return setName;
    }

    public void setSetName(String setName) {
        if (setName == null || setName.trim().isEmpty()) {
            throw new IllegalArgumentException("Set name cannot be empty.");
        }
        this.setName = setName.trim();
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        if (cardType == null || cardType.trim().isEmpty()) {
            throw new IllegalArgumentException("Card type cannot be blank.");
        }
        this.cardType = cardType.trim();
    }

    public String getRarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        if (rarity == null || rarity.trim().isEmpty()) {
            throw new IllegalArgumentException("Card rarity cannot be blank.");
        }
        this.rarity = rarity.trim();
    }

    public BigDecimal getBaseMarketPrice() {
        return baseMarketPrice;
    }

    public void setBaseMarketPrice(BigDecimal baseMarketPrice) {
        if (baseMarketPrice == null || baseMarketPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Base market price must be valid number.");
        }
        // Normalize all prices to 2 decimal places for consistent financial display
        this.baseMarketPrice = baseMarketPrice.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return String.format("[ID: %d] %s (%s) | Type: %s | Rarity: %s | Base Price: $%s",
                cardId, name, setName, cardType, rarity, baseMarketPrice);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        //two cards are logically the same catalog entry if they share name, set, and rarity
        return Objects.equals(name.toLowerCase(), card.name.toLowerCase())
                && Objects.equals(setName.toLowerCase(), card.setName.toLowerCase())
                && Objects.equals(rarity.toLowerCase(), card.rarity.toLowerCase());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase(), setName.toLowerCase(), rarity.toLowerCase());
    }
    //temp TEST BLOCK!!
    public static void main(String[] args) {
        System.out.println("Testing Card Domain Model");

        // 1. Test creating a new card from CSV (no DB ID yet!>>defaults to 0)
        Card csvCard = new Card("Charizard", "Base Set", "Fire", "Holo Rare", new BigDecimal("350.5"));
        System.out.println("CSV Card: " + csvCard);

        //test hydrating a card from Db with an ID and verifying equality
        Card dbCard = new Card(1, "charizard", "Base Set", "Fire", "Holo Rare", new BigDecimal("350.50"));
        System.out.println("DB Card:  " + dbCard);
        System.out.println("Logically equal? " + csvCard.equals(dbCard) + " (Expected: true)");

        //test fail-fast validation for empty price n name
        try {
            new Card("Pikachu", "Jungle", "Electric", "Common", new BigDecimal("-5.00"));
            System.out.println("ERROR: Should have rejected negative price!");
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected price error: " + e.getMessage());
        }

        try {
            new Card("   ", "Jungle", "Electric", "Common", new BigDecimal("2.00"));
            System.out.println("ERROR: Should have rejected blank name!");
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected name error: " + e.getMessage());
        }
    }
};