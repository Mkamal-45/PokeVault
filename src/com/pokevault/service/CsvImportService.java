package com.pokevault.service;
import com.pokevault.dao.CardDAO;
import com.pokevault.db.DatabaseManager;
import com.pokevault.model.Card;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/*
service responsible for parsing pkmn card CSV files using Java File I/O
and batch-persisting valid records into the database master catalog thru CardDAO.
*/
public class CsvImportService {

    private static final int EXPECTED_COLUMNS = 5;
    private final CardDAO cardDAO;

    public CsvImportService(CardDAO cardDAO) {
        if (cardDAO==null) {
            throw new IllegalArgumentException("CardDAO cannot be null.");
        }
        this.cardDAO= cardDAO;
    }

    public record ImportSummary(
        int validRowsParsed,
        int newlyInserted,
        int duplicatesSkipped,
        List<String> rowErrors
    ) {
        @Override
        public String toString() {
            return String.format(
                "Import Complete -> Valid Rows Parsed: %d | Newly Inserted: %d | Duplicates Skipped: %d | Row Errors: %d",
                validRowsParsed, newlyInserted, duplicatesSkipped, rowErrors.size()
            );
        }
    }

    public ImportSummary importCardsFromCsv(String filePath) throws IOException, SQLException {
        if (filePath==null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("CSV file path cannot be blank.");
        }

        Path path=Path.of(filePath.trim());
        if (!Files.exists(path)|| !Files.isRegularFile(path)) {
            throw new IOException("CSV file not found at path: " + path.toAbsolutePath());
        }

        List<Card> validCards =new ArrayList<>();
        List<String> rowErrors =new ArrayList<>();

        try (BufferedReader reader =Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber =0;

            while ((line=reader.readLine()) !=null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (lineNumber==1 && line.toLowerCase().startsWith("name,")) {
                    continue;
                }

                try {
                    Card parsedCard =parseCsvLine(line);
                    validCards.add(parsedCard);
                } catch (IllegalArgumentException parseEx) {
                    rowErrors.add("Line " + lineNumber + ": " + parseEx.getMessage());
                }
            }
        }

        int newlyInserted= cardDAO.batchInsertCards(validCards);
        int duplicatesSkipped= validCards.size() - newlyInserted;

        return new ImportSummary(validCards.size(), newlyInserted, duplicatesSkipped, rowErrors);
    }

    private Card parseCsvLine(String line) {
        String[] tokens = line.split(",", -1);
        if (tokens.length != EXPECTED_COLUMNS) {
            throw new IllegalArgumentException(
                "Expected " + EXPECTED_COLUMNS + " columns, found " + tokens.length + "."
            );
        }

        String name= tokens[0].trim();
        String setName= tokens[1].trim();
        String cardType= tokens[2].trim();
        String rarity= tokens[3].trim();
        String priceRaw= tokens[4].trim();

        BigDecimal basePrice;
        try {
            basePrice = new BigDecimal(priceRaw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid market price '" + priceRaw + "'.");
        }

        return new Card(name, setName, cardType, rarity, basePrice);
    }

    //temp testing, we'll remove from production code later
    public static void main(String[] args) {
        System.out.println("Testing CsvImportService (Java File I/O + Batch JDBC)");
        String testDbPath = "data/test_csv_import.db";
        String badCsvPath = "data/temp_bad_test.csv";
        File testDbFile = new File(testDbPath);
        File badCsvFile = new File(badCsvPath);

        if (testDbFile.exists()) testDbFile.delete();

        try {
            DatabaseManager testDb= new DatabaseManager("jdbc:sqlite:" + testDbPath);
            testDb.initializeDatabase();
            CardDAO cardDAO= new CardDAO(testDb);
            CsvImportService importService = new CsvImportService(cardDAO);
            ImportSummary firstRun =importService.importCardsFromCsv("data/master_cards.csv");
            System.out.println("Run 1: " + firstRun);
            ImportSummary secondRun =importService.importCardsFromCsv("data/master_cards.csv");
            System.out.println("Run 2: " + secondRun);
            String sampleMalformedCsv= """
                name,set_name,card_type,rarity,base_market_price
                Eevee,Jungle,Colorless,Common,3.25
                BrokenCard,Base Set,Fire,Rare,NOT_A_NUMBER
                MissingCols,Base Set,Water
                """;
            Files.writeString(Path.of(badCsvPath), sampleMalformedCsv, StandardCharsets.UTF_8);

            ImportSummary badRun = importService.importCardsFromCsv(badCsvPath);
            System.out.println("Malformed CSV Run: " + badRun);
            badRun.rowErrors().forEach(err -> System.out.println("  -> Caught: " + err));

        } catch (Exception e) {
            System.err.println("CsvImportService test failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (testDbFile.exists()) testDbFile.delete();
            if (badCsvFile.exists()) badCsvFile.delete();
        }







    };










};