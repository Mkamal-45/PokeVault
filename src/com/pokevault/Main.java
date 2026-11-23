package com.pokevault;
import com.pokevault.dao.CardDAO;
import com.pokevault.dao.InventoryDAO;
import com.pokevault.db.DatabaseManager;
import com.pokevault.service.CatalogService;
import com.pokevault.service.CsvImportService;
import com.pokevault.service.ValuationEngine;
import com.pokevault.ui.ConsoleMenu;
import java.sql.SQLException;


public class Main {
    public static void main(String[] args) {
        try {
            DatabaseManager dbManager= new DatabaseManager();
            dbManager.initializeDatabase();

            // 2. Instantiate Data Access Objects (DAOs)
            CardDAO cardDAO= new CardDAO(dbManager);
            InventoryDAO inventoryDAO= new InventoryDAO(dbManager);
            CsvImportService csvImportService= new CsvImportService(cardDAO);
            CatalogService catalogService= new CatalogService(cardDAO);
            ValuationEngine valuationEngine= new ValuationEngine(inventoryDAO);

            ConsoleMenu consoleMenu= new ConsoleMenu(
                csvImportService,
                catalogService,
                inventoryDAO,
                valuationEngine
            );
            consoleMenu.start();

        } catch (SQLException e) {
            System.err.println("Fatal startup error: Unable to initialize SQLite database.");
            System.err.println("Details: " + e.getMessage());
        }

    }


};