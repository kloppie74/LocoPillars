package org.Kloppie74.locoPillars.cosmetics.database

/** SQL definitions for the cosmetics tables. */
object CosmeticTables {

    // ── Owned cosmetics ────────────────────────────────────────────────────────

    object OwnedTable {
        const val CREATE_SQL_SQLITE = """
            CREATE TABLE IF NOT EXISTS lp_cosmetic_owned (
                player_id      VARCHAR(36)  NOT NULL,
                cosmetic_id    VARCHAR(64)  NOT NULL,
                cosmetic_type  VARCHAR(16)  NOT NULL,
                acquired_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_id, cosmetic_id, cosmetic_type)
            )
        """
        const val CREATE_SQL_MYSQL = """
            CREATE TABLE IF NOT EXISTS lp_cosmetic_owned (
                player_id      VARCHAR(36)  NOT NULL,
                cosmetic_id    VARCHAR(64)  NOT NULL,
                cosmetic_type  VARCHAR(16)  NOT NULL,
                acquired_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_id, cosmetic_id, cosmetic_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    }

    // ── Active selection per type ─────────────────────────────────────────────

    object SelectedTable {
        const val CREATE_SQL_SQLITE = """
            CREATE TABLE IF NOT EXISTS lp_cosmetic_selected (
                player_id      VARCHAR(36)  NOT NULL,
                cosmetic_type  VARCHAR(16)  NOT NULL,
                cosmetic_id    VARCHAR(64)  NOT NULL,
                PRIMARY KEY (player_id, cosmetic_type)
            )
        """
        const val CREATE_SQL_MYSQL = """
            CREATE TABLE IF NOT EXISTS lp_cosmetic_selected (
                player_id      VARCHAR(36)  NOT NULL,
                cosmetic_type  VARCHAR(16)  NOT NULL,
                cosmetic_id    VARCHAR(64)  NOT NULL,
                PRIMARY KEY (player_id, cosmetic_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    }
}
