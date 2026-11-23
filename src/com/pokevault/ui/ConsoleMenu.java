package com.pokevault.ui;
import com.pokevault.dao.InventoryDAO;
import com.pokevault.model.Card;
import com.pokevault.model.ConditionGrade;
import com.pokevault.model.InventoryItem;
import com.pokevault.service.CatalogService;
import com.pokevault.service.CsvImportService;
import com.pokevault.service.CsvImportService.ImportSummary;
import com.pokevault.service.ValuationEngine;
import com.pokevault.service.ValuationEngine.PortfolioSummary;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/*
interactive cmd line Interface (CLI) for PokéVault.
it andles user prompts, input sanitization, formatted console tables,
and recovery from validation or SQLite constraint errors.
*/
public class ConsoleMenu {
    private final CsvImportService csvImportService;
    private final CatalogService catalogService;
    private final InventoryDAO inventoryDAO;
    private final ValuationEngine valuationEngine;
    private final Scanner scanner;

    public ConsoleMenu(
            CsvImportService csvImportService,
            CatalogService catalogService,
            InventoryDAO inventoryDAO,
            ValuationEngine valuationEngine) {
        this.csvImportService =csvImportService;
        this.catalogService =catalogService;
        this.inventoryDAO =inventoryDAO;
        this.valuationEngine =valuationEngine;
        this.scanner =new Scanner(System.in);
    }

    public void start() {
        printBanner();
        boolean running =true;

        while (running) {
            printMainMenu();
            String choice =scanner.nextLine().trim();

            switch (choice) {
                case "1" -> handleCsvImport();
                case "2" -> handleCatalogMenu();
                case "3" -> handleViewVault();
                case "4" -> handleAddCardToVault();
                case "5" -> handleUpdateCardGrade();
                case "6" -> handleUpdateCardQuantity();
                case "7" -> handleRemoveSoldCard();
                case "8" -> handleViewPortfolioValuation();
                case "0" -> {
                    System.out.println("\nSaving vault state and exiting PokéVault. Keep collecting!!");
                    running = false;
                }
                default -> System.out.println("\n[!] Invalid option '" + choice + "'. Please enter a number from 0 to 8.");
            }
        }
        scanner.close();
    }

    private void printBanner() {
        System.out.println("====================================================================");
        System.out.println("          POKÉVAULT - A Pokémon Card Portfolio Manager          ");
        System.out.println("====================================================================");
    }

    private void printMainMenu() {
        System.out.println("\n MAIN MENU - Select an option:");
        System.out.println("1. Batch Import Master Cards from CSV (File I/O -> SQLite)");
        System.out.println("2. Browse, Search & Sort Master Card Catalog");
        System.out.println("3. View Personal Vault Inventory");
        System.out.println("4. Add Card to Personal Vault");
        System.out.println("5. Update Card Condition Grade in Vault");
        System.out.println("6. Update Card Quantity in Vault");
        System.out.println("7. Remove Sold Card from Vault");
        System.out.println("8. View Portfolio Financial Breakdown (Net Worth & ROI)");
        System.out.println("0. Exit");
        System.out.print("Select an option: ");
    }

    private void handleCsvImport() {
        System.out.print("\nEnter CSV file path (press Enter for default 'data/master_cards.csv'): ");
        String pathInput =scanner.nextLine().trim();
        if (pathInput.isEmpty()) {pathInput = "data/master_cards.csv";}

        try {
            ImportSummary summary =csvImportService.importCardsFromCsv(pathInput);
            System.out.println("\n[Success] " + summary);
            if (!summary.rowErrors().isEmpty()) {
                System.out.println("Row Warnings:");
                summary.rowErrors().forEach(err -> System.out.println("  - " + err));
            }
        } catch (IOException | SQLException | IllegalArgumentException e) {
            System.out.println("\n[Import Error] " + e.getMessage());
        }
    }

    private void handleCatalogMenu() {
        System.out.println("\n MASTER CATALOG OPTIONS - Select a view:  ");
        System.out.println("1. View All Cards (Default Catalog Order)");
        System.out.println("2. Sort by Market Price (Highest -> Lowest)");
        System.out.println("3. Sort by Market Price (Lowest -> Highest)");
        System.out.println("4. Sort by Pokémon Type, then Market Price");
        System.out.println("5. Search Cards by Name");
        System.out.println("6. Filter Cards by Pokémon Type (e.g., Fire, Water, Electric)");
        System.out.print("Select catalog view: ");
        String subChoice = scanner.nextLine().trim();

        try {
            List<Card> results;
            switch (subChoice) {
                case "1" -> results= catalogService.getAllCards();
                case "2" -> results= catalogService.sortByPriceDescending(catalogService.getAllCards());
                case "3" -> results= catalogService.sortByPriceAscending(catalogService.getAllCards());
                case "4" -> results= catalogService.sortByTypeThenPrice(catalogService.getAllCards());
                case "5" -> {
                    System.out.print("Enter card name keyword: ");
                    results = catalogService.searchByName(scanner.nextLine());
                }
                case "6" -> {
                    System.out.print("Enter Pokémon type: ");
                    results = catalogService.filterByType(scanner.nextLine());
                }
                default -> {
                    System.out.println("[!] Invalid catalog option.");
                    return;
                }
            }
            printCatalogTable(results);
        } catch (SQLException | IllegalArgumentException e) {
            System.out.println("\n[Catalog Error] " + e.getMessage());
        }
    }

    private void printCatalogTable(List<Card> cards) {
        if (cards.isEmpty()) {
            System.out.println("\nNo cards found matching your criteria. (Tip: Run Option 1 first to import CSV cards.)");
            return;
        }

        System.out.println("\n----------------------------------------------------------------------------------------");
        System.out.printf("%-5s | %-20s | %-18s | %-11s | %-14s | %-12s%n",
                "ID", "Card Name", "Set", "Type", "Rarity", "Base Price");
        System.out.println("----------------------------------------------------------------------------------------");
        for (Card c : cards) {
            System.out.printf("%-5d | %-20s | %-18s | %-11s | %-14s | $%-11s%n",
                    c.getCardId(), c.getName(), c.getSetName(), c.getCardType(), c.getRarity(), c.getBaseMarketPrice());
        }
        System.out.println("----------------------------------------------------------------------------------------");


    };


    private void handleViewVault() {
        try {
            List<InventoryItem> items = inventoryDAO.getAllInventoryItems();
            if (items.isEmpty()) {
                System.out.println("\nYour personal vault is currently empty. Use Option 4 to add cards!");
                return;
            }

            System.out.println("\n===================================================================================================================");
            System.out.printf("%-8s | %-18s | %-17s | %-4s | %-10s | %-11s | %-11s | %-10s%n",
                    "Vault ID", "Card Name", "Condition", "Qty", "Unit Val", "Total Val", "Total Cost", "ROI %");
            System.out.println("===================================================================================================================");
            for (InventoryItem item : items) {
                System.out.printf("%-8d | %-18s | %-17s | %-4d | $%-9s | $%-10s | $%-10s | %s%%%n",
                        item.getInventoryId(),
                        item.getCard().getName(),
                        item.getConditionGrade().getLabel(),
                        item.getQuantity(),
                        item.getUnitCurrentValue(),
                        item.getTotalCurrentValue(),
                        item.getTotalCostBasis(),
                        item.getRoiPercentage());
            }
            System.out.println("===================================================================================================================");
        } catch (SQLException e) {
            System.out.println("\n[Database Error] Could not load vault: " + e.getMessage());
        }
    }


    private void handleAddCardToVault() {
        try {
            int cardId = promptForInt("Enter Master Catalog Card ID: ");
            Card targetCard = catalogService.getCardById(cardId).orElse(new Card(cardId, "Unknown", "Unknown", "Unknown", "Unknown", BigDecimal.ZERO));

            System.out.println("Valid Condition Grades: PSA 10, PSA 9, Near Mint, Lightly Played, Moderately Played, Damaged");
            System.out.print("Enter Condition Grade:    ");
            ConditionGrade grade = ConditionGrade.fromString(scanner.nextLine());

            BigDecimal purchasePrice = promptForBigDecimal("Enter Purchase Price per card ($):  ");
            int quantity = promptForInt("Enter Quantity owned:  ");

            InventoryItem newItem = new InventoryItem(targetCard, grade, purchasePrice, quantity);
            InventoryItem savedItem = inventoryDAO.addInventoryItem(newItem);

            System.out.println("\n[Success] Added to vault -> " + savedItem);

        } catch (IllegalArgumentException e) {
            System.out.println("\n[Validation Error] " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("\n[SQL Constraint Error] Database rejected entry: " + e.getMessage());
        }

    };


    private void handleUpdateCardGrade() {
        try {
            int vaultId =promptForInt("Enter Vault ID to update: ");
            System.out.println("Valid Condition Grades: PSA 10, PSA 9, Near Mint, Lightly Played, Moderately Played, Damaged");
            System.out.print("Enter New Condition Grade: ");
            ConditionGrade newGrade =ConditionGrade.fromString(scanner.nextLine());

            boolean updated =inventoryDAO.updateConditionGrade(vaultId, newGrade);
            if (updated) {
                System.out.println("\n[Success] Vault ID " + vaultId + " updated to " + newGrade.getLabel() + ".");
            } else {
                System.out.println("\n[Not Found] No vault entry exists with Vault ID " + vaultId + ".");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("\n[Validation Error] " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("\n[Database Error] " + e.getMessage());
        }

    };

    private void handleUpdateCardQuantity() {
        try {
            int vaultId =promptForInt("Enter Vault ID to update: ");
            int newQuantity =promptForInt("Enter new quantity (must be >= 1): ");

            boolean updated =inventoryDAO.updateQuantity(vaultId, newQuantity);
            if (updated) {
                System.out.println("\n[Success] Vault ID " + vaultId + " quantity updated to " + newQuantity + ".");
            }else {
                System.out.println("\n[Not Found] No vault entry exists with Vault ID " + vaultId + ".");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("\n[Validation Error] " + e.getMessage());
        }catch (SQLException e) {
            System.out.println("\n[Database Error] " + e.getMessage());
        }



    };


    private void handleRemoveSoldCard() {
        try {
            int vaultId =promptForInt("Enter Vault ID of sold card to remove: ");
            boolean removed =inventoryDAO.removeInventoryItem(vaultId);
            if (removed) {
                System.out.println("\n[Success] Removed Vault ID " + vaultId + " from your personal vault.");
            } else {
                System.out.println("\n[Not Found] No vault entry exists with Vault ID " + vaultId + ".");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("\n[Validation Error] " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("\n[Database Error] " + e.getMessage());
        }

    };


    private void handleViewPortfolioValuation() {
        try {
            PortfolioSummary summary= valuationEngine.generatePortfolioSummary();

            System.out.println("\n============================================================");
            System.out.println("              POKÉVAULT PORTFOLIO FINANCIAL VALUATION   ");   
            System.out.println("============================================================");
            System.out.printf("Unique Vault Entries : %d%n", summary.uniqueVaultEntries());
            System.out.printf("Total Physical Cards : %d%n", summary.totalPhysicalCards());
            System.out.printf("Total Cost Basis     : $%s%n", summary.totalCostBasis());
            System.out.printf("Total Net Worth      : $%s%n", summary.totalNetWorth());
            System.out.printf("Net Profit / Loss    : $%s%n", summary.netProfitOrLoss());
            System.out.printf("Overall Portfolio ROI: %s%%%n", summary.overallRoiPercentage());

            summary.mostValuableItem().ifPresentOrElse(
                top -> System.out.printf("Most Valuable Card   : %s (%s) [%s] — $%s%n",
                        top.getCard().getName(),
                        top.getCard().getSetName(),
                        top.getConditionGrade().getLabel(),
                        top.getUnitCurrentValue()),
                () -> System.out.println("Most Valuable Card   : N/A (Vault is empty)")
            );
            System.out.println("============================================================");
        } catch (SQLException e) {
            System.out.println("\n[Database Error] Failed to compute valuation: " + e.getMessage());
        }

    };


    private int promptForInt(String prompt) {
        System.out.print(prompt);
        String raw= scanner.nextLine().trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a whole number, but received '" + raw + "'.");
        }


        
    }

    private BigDecimal promptForBigDecimal(String prompt) {
        System.out.print(prompt);
        String raw = scanner.nextLine().trim();
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a valid dollar amount, but received '" + raw + "'.");
        }


    }









};