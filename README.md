# PokéVault — Pokémon Card Portfolio Manager

A Java & JDBC desktop CLI application for cataloging Pokémon TCG cards, managing personal vault inventory with condition grades, and calculating real-time portfolio valuation and ROI.

## Architecture
- **Domain Models:** `Card`, `InventoryItem`, `ConditionGrade`
- **Persistence :** Relational SQL database connected via JDBC with Foreign Key constraints
- **Business Services:** Java File I/O CSV batch importer, Collections/Comparator catalog engine, and `BigDecimal` valuation calculator
- **CLI:** Interactive console interface with input and constraint error handling