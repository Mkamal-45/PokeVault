package com.pokevault.service;
import com.pokevault.dao.CardDAO;
import com.pokevault.db.DatabaseManager;
import com.pokevault.model.Card;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/*
business service for querying, filtering, and sorting the master pkmn card catalog.
uses Java Collections(arrayList) and Comparators to keep sorting/search rules decoupled from SQL and UI.
*/
public class CatalogService {
    private final CardDAO cardDAO;
    private static final Comparator<Card> BY_PRICE_ASC =
            Comparator.comparing(Card::getBaseMarketPrice).thenComparing(Card::getName, String.CASE_INSENSITIVE_ORDER);
    
    private static final Comparator<Card> BY_PRICE_DESC =
            Comparator.comparing(Card::getBaseMarketPrice).reversed().thenComparing(Card::getName, String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<Card> BY_TYPE_THEN_PRICE_DESC =
            Comparator.comparing(Card::getCardType, String.CASE_INSENSITIVE_ORDER).thenComparing(Comparator.comparing(Card::getBaseMarketPrice).reversed()).thenComparing(Card::getName, String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<Card> BY_NAME_ASC =
            Comparator.comparing(Card::getName, String.CASE_INSENSITIVE_ORDER);

    public CatalogService(CardDAO cardDAO) {
        if (cardDAO == null) {
            throw new IllegalArgumentException("CardDAO cannot be null.");
        }
        this.cardDAO = cardDAO;
    }

    public List<Card> getAllCards() throws SQLException {
        return cardDAO.getAllCards();
    }

    public Optional<Card> getCardById(int cardId) throws SQLException {
        if (cardId<= 0) {
            throw new IllegalArgumentException("Card ID must be a positive integer.");
        }
        return cardDAO.findById(cardId);
    }

    public List<Card> searchByName(String keyword) throws SQLException {
        if (keyword==null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Search keyword cannot be blank.");
        }

        String normalizedKeyword= keyword.trim().toLowerCase();
        List<Card> allCards= cardDAO.getAllCards();
        List<Card> matches= new ArrayList<>();

        for (Card card : allCards) {
            if (card.getName().toLowerCase().contains(normalizedKeyword)) {
                matches.add(card);
            }
        }
        return matches;
    }

    public List<Card> filterByType(String cardType) throws SQLException {
        if (cardType==null || cardType.trim().isEmpty()) {
            throw new IllegalArgumentException("Card type filter cannot be blank.");
        }

        String normalizedType= cardType.trim();
        List<Card> allCards= cardDAO.getAllCards();
        List<Card> filtered= new ArrayList<>();

        for (Card card: allCards) {
            if (card.getCardType().equalsIgnoreCase(normalizedType)) {
                filtered.add(card);
            }
        }

        filtered.sort(BY_PRICE_DESC);
        return filtered;
    }

    public List<Card> sortByPriceAscending(List<Card> cards) {
        List<Card> sorted= new ArrayList<>(cards);
        sorted.sort(BY_PRICE_ASC);
        return sorted;
    }

    public List<Card> sortByPriceDescending(List<Card> cards) {
        List<Card> sorted= new ArrayList<>(cards);
        sorted.sort(BY_PRICE_DESC);
        return sorted;
    }

    public List<Card> sortByTypeThenPrice(List<Card> cards) {
        List<Card> sorted= new ArrayList<>(cards);
        sorted.sort(BY_TYPE_THEN_PRICE_DESC);
        return sorted;
    }

    public List<Card> sortByName(List<Card> cards) {
        List<Card> sorted = new ArrayList<>(cards);
        sorted.sort(BY_NAME_ASC);
        return sorted;
    }

    // test blck
    public static void main(String[] args) {
        System.out.println("Testing CatalogService (collections & comparators)");
        String testDbPath= "data/test_catalog_service.db";
        File testDbFile= new File(testDbPath);
        if (testDbFile.exists()) {
            testDbFile.delete();
        }

        try {
            DatabaseManager testDb= new DatabaseManager("jdbc:sqlite:"+testDbPath);
            testDb.initializeDatabase();

            CardDAO cardDAO= new CardDAO(testDb);
            CsvImportService importer= new CsvImportService(cardDAO);
            importer.importCardsFromCsv("data/master_cards.csv");

            CatalogService catalogService= new CatalogService(cardDAO);
            List<Card> vmaxMatches= catalogService.searchByName("vmax");
            System.out.println("Search 'vmax' count: " + vmaxMatches.size() + " (Expected: 2)");
            List<Card> waterCards= catalogService.filterByType("water");
            System.out.println("Water cards filtered & sorted:");
            waterCards.forEach(c -> System.out.println("  " + c));
            List<Card> byHighestPrice= catalogService.sortByPriceDescending(catalogService.getAllCards());
            System.out.println("Most expensive catalog card: " + byHighestPrice.get(0).getName()
                    + " ($" + byHighestPrice.get(0).getBaseMarketPrice() + ") (Expected: Umbreon VMAX $620.00)");
            System.out.println("Least expensive catalog card: " + byHighestPrice.get(byHighestPrice.size() - 1).getName()
                    + " ($" + byHighestPrice.get(byHighestPrice.size() - 1).getBaseMarketPrice() + ") (Expected: Pikachu $4.50)");


            try {
                catalogService.searchByName("   ");
                System.out.println("ERROR: Should have rejected blank search!");
            } catch (IllegalArgumentException e) {
                System.out.println("Caught expected blank search error: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("CatalogService test failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (testDbFile.exists()) {
                testDbFile.delete();
            }
        }







    };














};