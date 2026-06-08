package org.Kloppie74.locoPillars.database.dao

import org.Kloppie74.locoPillars.database.DatabaseManager
import org.Kloppie74.locoPillars.database.models.LeaderboardEntry
import org.Kloppie74.locoPillars.database.models.LeaderboardMetadata
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDateTime

object LeaderboardCacheDAO {

    // Leaderboard types
    const val TYPE_WINS = "wins"
    const val TYPE_WINRATE = "winrate"
    const val TYPE_KILLS = "kills"
    const val TYPE_KD_RATIO = "kd"
    const val TYPE_LEVEL = "level"
    const val TYPE_PLAYTIME = "playtime"
    const val TYPE_STREAK = "streak"
    const val TYPE_PILLARS = "pillars"

    // =================== INITIALIZATION ===================

    fun initializeMetadata(): Boolean {
        val leaderboardTypes = listOf(
            TYPE_WINS, TYPE_WINRATE, TYPE_KILLS, TYPE_KD_RATIO,
            TYPE_LEVEL, TYPE_PLAYTIME, TYPE_STREAK, TYPE_PILLARS
        )

        val sql = when (DatabaseManager.getDatabaseType().lowercase()) {
            "h2" -> """
                MERGE INTO lp_leaderboard_metadata (leaderboard_type, qualifying_threshold, created_at, updated_at) 
                KEY(leaderboard_type)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """
            "mysql", "mariadb" -> """
                INSERT IGNORE INTO lp_leaderboard_metadata (leaderboard_type, qualifying_threshold) 
                VALUES (?, ?)
            """
            else -> """
                INSERT OR IGNORE INTO lp_leaderboard_metadata (leaderboard_type, qualifying_threshold) 
                VALUES (?, ?)
            """
        }

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    leaderboardTypes.forEach { type ->
                        ps.setString(1, type)
                        ps.setInt(2, getDefaultQualifyingThreshold(type))
                        ps.addBatch()
                    }
                    ps.executeBatch()
                    conn.commit()
                    true
                }
            }
        } catch (e: Exception) {
            // Log maar geen crash - metadata kan later geïnitialiseerd worden
            false
        }
    }

    // =================== CACHE UPDATE ===================

    fun updateCache(): Boolean {
        return try {
            val startTime = System.currentTimeMillis()

            val success = listOf(
                updateWinsLeaderboard(),
                updateWinRateLeaderboard(),
                updateKillsLeaderboard(),
                updateKDRatioLeaderboard(),
                updateLevelLeaderboard(),
                updatePlaytimeLeaderboard(),
                updateWinStreakLeaderboard(),
                updatePillarsLeaderboard()
            ).all { it }

            val duration = System.currentTimeMillis() - startTime

            if (success) {
                updateAllMetadata(duration)
            }

            success
        } catch (e: Exception) {
            false
        }
    }

    private fun updateWinsLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.games_won DESC, s.games_played ASC) as rank_position,
                s.games_won as rank_value,
                CAST(s.games_won AS CHAR) as rank_display_value,
                s.games_played as secondary_value,
                CONCAT(s.games_played, ' played') as secondary_display,
                CASE WHEN s.games_played > 0 THEN ROUND((s.games_won * 100.0 / s.games_played), 2) ELSE 0 END as tertiary_value,
                CASE WHEN s.games_played > 0 THEN CONCAT(ROUND((s.games_won * 100.0 / s.games_played), 2), '%') ELSE '0%' END as tertiary_display,
                0.0 as percentile,
                0.0 as score_normalized,
                NULL as previous_rank,
                0 as rank_change,
                'STABLE' as rank_change_direction,
                s.games_played as qualifying_games,
                CASE WHEN s.games_played >= ? THEN 1 ELSE 0 END as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.games_won > 0
            ORDER BY s.games_won DESC, s.games_played ASC
        """
        return upsertLeaderboardFromSelect(
            selectSql,
            TYPE_WINS,
            TYPE_WINS,
            getDefaultQualifyingThreshold(TYPE_WINS),
            LocalDateTime.now()
        )
    }

    private fun updateWinRateLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY win_rate DESC, s.games_won DESC) as rank_position,
                win_rate as rank_value,
                CONCAT(ROUND(win_rate, 2), '%') as rank_display_value,
                s.games_won as secondary_value,
                CONCAT(s.games_won, ' wins') as secondary_display,
                s.games_played as tertiary_value,
                CONCAT(s.games_played, ' played') as tertiary_display,
                0.0 as percentile,
                0.0 as score_normalized,
                NULL as previous_rank,
                0 as rank_change,
                'STABLE' as rank_change_direction,
                s.games_played as qualifying_games,
                CASE WHEN s.games_played >= ? THEN 1 ELSE 0 END as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            INNER JOIN (
                SELECT player_id, CASE WHEN games_played > 0 THEN (games_won * 100.0 / games_played) ELSE 0 END as win_rate
                FROM lp_stats
            ) wr ON s.player_id = wr.player_id
            WHERE s.games_played >= ?
            ORDER BY win_rate DESC, s.games_won DESC
        """
        val threshold = getDefaultQualifyingThreshold(TYPE_WINRATE)
        return upsertLeaderboardFromSelect(
            selectSql,
            TYPE_WINRATE,
            TYPE_WINRATE,
            threshold,
            LocalDateTime.now(),
            threshold
        )
    }

    private fun updateKillsLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.total_kills DESC, s.total_deaths ASC) as rank_position,
                s.total_kills as rank_value,
                CAST(s.total_kills AS CHAR) as rank_display_value,
                s.total_deaths as secondary_value,
                CONCAT(s.total_deaths, ' deaths') as secondary_display,
                CASE WHEN s.total_deaths > 0 THEN ROUND((s.total_kills * 1.0 / s.total_deaths), 2) ELSE s.total_kills END as tertiary_value,
                CASE WHEN s.total_deaths > 0 THEN CONCAT(ROUND((s.total_kills * 1.0 / s.total_deaths), 2), ' K/D') ELSE CONCAT(s.total_kills, ' K/D') END as tertiary_display,
                0.0 as percentile,
                0.0 as score_normalized,
                NULL as previous_rank,
                0 as rank_change,
                'STABLE' as rank_change_direction,
                s.games_played as qualifying_games,
                1 as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.total_kills > 0
            ORDER BY s.total_kills DESC, s.total_deaths ASC
        """
        return upsertLeaderboardFromSelect(
            selectSql,
            TYPE_KILLS,
            TYPE_KILLS,
            LocalDateTime.now()
        )
    }

    private fun updateKDRatioLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY kd_ratio DESC, s.total_kills DESC) as rank_position,
                kd_ratio as rank_value,
                ROUND(kd_ratio, 2) as rank_display_value,
                s.total_kills as secondary_value,
                CONCAT(s.total_kills, ' kills') as secondary_display,
                s.total_deaths as tertiary_value,
                CONCAT(s.total_deaths, ' deaths') as tertiary_display,
                0.0 as percentile,
                0.0 as score_normalized,
                NULL as previous_rank,
                0 as rank_change,
                'STABLE' as rank_change_direction,
                s.games_played as qualifying_games,
                CASE WHEN s.total_deaths >= ? THEN 1 ELSE 0 END as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            INNER JOIN (
                SELECT player_id, CASE WHEN total_deaths > 0 THEN (total_kills * 1.0 / total_deaths) ELSE total_kills END as kd_ratio
                FROM lp_stats
            ) kd ON s.player_id = kd.player_id
            WHERE s.total_deaths >= ?
            ORDER BY kd_ratio DESC, s.total_kills DESC
        """
        val threshold = getDefaultQualifyingThreshold(TYPE_KD_RATIO)
        return upsertLeaderboardFromSelect(
            selectSql,
            TYPE_KD_RATIO,
            TYPE_KD_RATIO,
            threshold,
            LocalDateTime.now(),
            threshold
        )
    }

    private fun updateLevelLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.level DESC, s.experience DESC) as rank_position,
                s.level as rank_value,
                CONCAT('Level ', s.level) as rank_display_value,
                s.experience as secondary_value,
                CONCAT(s.experience, ' XP') as secondary_display,
                s.games_played as tertiary_value,
                CONCAT(s.games_played, ' games') as tertiary_display,
                0.0 as percentile,
                0.0 as score_normalized,
                NULL as previous_rank,
                0 as rank_change,
                'STABLE' as rank_change_direction,
                s.games_played as qualifying_games,
                1 as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.level > 1
            ORDER BY s.level DESC, s.experience DESC
        """
        return upsertLeaderboardFromSelect(
            selectSql,
            TYPE_LEVEL,
            TYPE_LEVEL,
            LocalDateTime.now()
        )
    }

    private fun updatePlaytimeLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.total_playtime DESC, s.games_played DESC) as rank_position,
                s.total_playtime as rank_value,
                CONCAT(ROUND(s.total_playtime / 3600.0, 1), 'h') as rank_display_value,
                s.games_played as secondary_value,
                CONCAT(s.games_played, ' games') as secondary_display,
                CASE WHEN s.games_played > 0 THEN s.total_playtime / s.games_played ELSE 0 END as tertiary_value,
                CASE WHEN s.games_played > 0 THEN CONCAT(ROUND(s.total_playtime / s.games_played / 60.0, 1), 'm avg') ELSE '0m avg' END as tertiary_display,
                0.0 as percentile,
                0.0 as score_normalized,
                NULL as previous_rank,
                0 as rank_change,
                'STABLE' as rank_change_direction,
                s.games_played as qualifying_games,
                1 as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.total_playtime > 0
            ORDER BY s.total_playtime DESC, s.games_played DESC
        """
        return upsertLeaderboardFromSelect(
            selectSql,
            TYPE_PLAYTIME,
            TYPE_PLAYTIME,
            LocalDateTime.now()
        )
    }

    private fun updateWinStreakLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.best_win_streak DESC, s.current_win_streak DESC) as rank_position,
                s.best_win_streak as rank_value,
                CONCAT(s.best_win_streak, ' best') as rank_display_value,
                s.current_win_streak as secondary_value,
                CONCAT(s.current_win_streak, ' current') as secondary_display,
                s.games_won as tertiary_value,
                CONCAT(s.games_won, ' wins') as tertiary_display,
                0.0 as percentile,
                0.0 as score_normalized,
                NULL as previous_rank,
                0 as rank_change,
                'STABLE' as rank_change_direction,
                s.games_played as qualifying_games,
                CASE WHEN s.games_played >= ? THEN 1 ELSE 0 END as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.best_win_streak > 0
            ORDER BY s.best_win_streak DESC, s.current_win_streak DESC
        """
        return upsertLeaderboardFromSelect(
            selectSql,
            TYPE_STREAK,
            TYPE_STREAK,
            getDefaultQualifyingThreshold(TYPE_STREAK),
            LocalDateTime.now()
        )
    }

    private fun updatePillarsLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.pillars_destroyed DESC, s.games_played ASC) as rank_position,
                s.pillars_destroyed as rank_value,
                CAST(s.pillars_destroyed AS CHAR) as rank_display_value,
                s.games_played as secondary_value,
                CONCAT(s.games_played, ' games') as secondary_display,
                CASE WHEN s.games_played > 0 THEN ROUND(s.pillars_destroyed * 1.0 / s.games_played, 2) ELSE 0 END as tertiary_value,
                CASE WHEN s.games_played > 0 THEN CONCAT(ROUND(s.pillars_destroyed * 1.0 / s.games_played, 2), ' avg') ELSE '0 avg' END as tertiary_display,
                0.0 as percentile,
                0.0 as score_normalized,
                NULL as previous_rank,
                0 as rank_change,
                'STABLE' as rank_change_direction,
                s.games_played as qualifying_games,
                1 as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.pillars_destroyed > 0
            ORDER BY s.pillars_destroyed DESC, s.games_played ASC
        """
        return upsertLeaderboardFromSelect(
            selectSql,
            TYPE_PILLARS,
            TYPE_PILLARS,
            LocalDateTime.now()
        )
    }

    private fun upsertLeaderboardFromSelect(selectSql: String, leaderboardType: String, vararg params: Any): Boolean {
        // BELANGRIJK: getDatabaseType() geeft de RAUWE config-waarde terug (bijv. "H2" of
        // "MySQL"), dus hier .lowercase() — anders viel dit in de else-tak → exception →
        // updateCache() faalde → cache leeg → alle ranks "Unranked".
        val upsertSql = when (DatabaseManager.getDatabaseType().lowercase()) {
            "mysql", "mariadb" -> """
                REPLACE INTO lp_leaderboard_cache (
                    player_id, player_name, leaderboard_type, rank_position, rank_value, rank_display_value,
                    secondary_value, secondary_display, tertiary_value, tertiary_display,
                    percentile, score_normalized, previous_rank, rank_change, rank_change_direction,
                    qualifying_games, eligible, last_updated, cache_version
                )
                $selectSql
            """
            // H2 draait in MODE=MySQL maar kent GEEN "INSERT OR REPLACE" (dat is SQLite).
            // H2 ondersteunt MERGE INTO ... KEY(...) met een SELECT-bron.
            "h2" -> """
                MERGE INTO lp_leaderboard_cache (
                    player_id, player_name, leaderboard_type, rank_position, rank_value, rank_display_value,
                    secondary_value, secondary_display, tertiary_value, tertiary_display,
                    percentile, score_normalized, previous_rank, rank_change, rank_change_direction,
                    qualifying_games, eligible, last_updated, cache_version
                )
                KEY(player_id, leaderboard_type)
                $selectSql
            """
            "sqlite" -> """
                INSERT OR REPLACE INTO lp_leaderboard_cache (
                    player_id, player_name, leaderboard_type, rank_position, rank_value, rank_display_value,
                    secondary_value, secondary_display, tertiary_value, tertiary_display,
                    percentile, score_normalized, previous_rank, rank_change, rank_change_direction,
                    qualifying_games, eligible, last_updated, cache_version
                )
                $selectSql
            """
            "postgresql" -> """
                INSERT INTO lp_leaderboard_cache (
                    player_id, player_name, leaderboard_type, rank_position, rank_value, rank_display_value,
                    secondary_value, secondary_display, tertiary_value, tertiary_display,
                    percentile, score_normalized, previous_rank, rank_change, rank_change_direction,
                    qualifying_games, eligible, last_updated, cache_version
                )
                $selectSql
                ON CONFLICT (player_id, leaderboard_type) DO UPDATE SET
                    player_name = EXCLUDED.player_name,
                    rank_position = EXCLUDED.rank_position,
                    rank_value = EXCLUDED.rank_value,
                    rank_display_value = EXCLUDED.rank_display_value,
                    secondary_value = EXCLUDED.secondary_value,
                    secondary_display = EXCLUDED.secondary_display,
                    tertiary_value = EXCLUDED.tertiary_value,
                    tertiary_display = EXCLUDED.tertiary_display,
                    percentile = EXCLUDED.percentile,
                    score_normalized = EXCLUDED.score_normalized,
                    previous_rank = EXCLUDED.previous_rank,
                    rank_change = EXCLUDED.rank_change,
                    rank_change_direction = EXCLUDED.rank_change_direction,
                    qualifying_games = EXCLUDED.qualifying_games,
                    eligible = EXCLUDED.eligible,
                    last_updated = EXCLUDED.last_updated,
                    cache_version = EXCLUDED.cache_version
            """
            else -> throw IllegalArgumentException("Unsupported database type: ${DatabaseManager.getDatabaseType()}")
        }

        DatabaseManager.getConnection().use { conn ->
            conn.prepareStatement(upsertSql).use { ps ->
                params.forEachIndexed { i, value ->
                    when (value) {
                        is String -> ps.setString(i + 1, value)
                        is Int -> ps.setInt(i + 1, value)
                        is LocalDateTime -> ps.setObject(i + 1, value)
                        else -> ps.setObject(i + 1, value)
                    }
                }
                ps.executeUpdate()
                conn.commit()
            }
        }

        // Percentielen zijn optioneel (en niet elk SQL-dialect ondersteunt UPDATE..FROM).
        // Mag NOOIT de cache-vulling laten falen — de rank_position is al gecommit.
        try {
            updatePercentiles(leaderboardType)
        } catch (_: Exception) {
            // negeren — ranks werken ook zonder percentielen
        }
        return true
    }

    private fun updatePercentiles(type: String): Boolean {
        val sql = """
            UPDATE lp_leaderboard_cache 
            SET percentile = CASE 
                WHEN total_entries > 0 THEN ((total_entries - rank_position + 1) * 100.0 / total_entries)
                ELSE 0 
            END
            FROM (
                SELECT COUNT(*) as total_entries 
                FROM lp_leaderboard_cache 
                WHERE leaderboard_type = ? AND eligible = 1
            ) totals
            WHERE leaderboard_type = ? AND eligible = 1
        """

        DatabaseManager.getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, type)
                ps.setString(2, type)
                val updated = ps.executeUpdate()
                conn.commit()
                return updated >= 0
            }
        }
    }

    private fun updateAllMetadata(updateDuration: Long): Boolean {
        val sql = """
            UPDATE lp_leaderboard_metadata 
            SET last_full_update = ?, 
                update_duration_ms = ?,
                total_entries = (
                    SELECT COUNT(*) FROM lp_leaderboard_cache 
                    WHERE leaderboard_type = lp_leaderboard_metadata.leaderboard_type
                ),
                eligible_entries = (
                    SELECT COUNT(*) FROM lp_leaderboard_cache 
                    WHERE leaderboard_type = lp_leaderboard_metadata.leaderboard_type AND eligible = 1
                ),
                updated_at = ?
        """

        DatabaseManager.getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                val now = LocalDateTime.now()
                ps.setObject(1, now)
                ps.setLong(2, updateDuration)
                ps.setObject(3, now)
                val updated = ps.executeUpdate()
                conn.commit()
                return updated >= 0
            }
        }
    }

    // =================== QUERY METHODS ===================

    fun getTopPlayersByType(type: String, limit: Int, offset: Int, eligibleOnly: Boolean): List<LeaderboardEntry> {
        val eligibilityFilter = if (eligibleOnly) "AND eligible = 1" else ""
        val sql = """
            SELECT * FROM lp_leaderboard_cache 
            WHERE leaderboard_type = ? $eligibilityFilter
            ORDER BY rank_position ASC 
            LIMIT ? OFFSET ?
        """

        val list = mutableListOf<LeaderboardEntry>()
        DatabaseManager.getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, type)
                ps.setInt(2, limit)
                ps.setInt(3, offset)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        list.add(rowToLeaderboardEntry(rs))
                    }
                }
            }
        }
        return list
    }

    /**
     * Haal de cache-entry op die op een specifieke positie (rank) staat voor een type.
     * Gebruikt voor placeholders als db_rank_1_wins (= speler op plek 1 in wins).
     * Returnt null als er niemand op die positie staat.
     */
    fun getEntryAtPosition(leaderboardType: String, position: Int): LeaderboardEntry? {
        val sql = "SELECT * FROM lp_leaderboard_cache WHERE leaderboard_type = ? AND rank_position = ?"
        DatabaseManager.getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, leaderboardType)
                ps.setInt(2, position)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) rowToLeaderboardEntry(rs) else null
                }
            }
        }
    }

    fun getPlayerRank(playerId: String, leaderboardType: String): Int {
        val sql = "SELECT rank_position FROM lp_leaderboard_cache WHERE player_id = ? AND leaderboard_type = ?"
        DatabaseManager.getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, playerId)
                ps.setString(2, leaderboardType)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) rs.getInt("rank_position") else 999
                }
            }
        }
    }

    fun getMetadata(type: String): LeaderboardMetadata? {
        val sql = "SELECT * FROM lp_leaderboard_metadata WHERE leaderboard_type = ?"
        DatabaseManager.getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, type)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) rowToMetadata(rs) else null
                }
            }
        }
    }

    // =================== HELPER METHODS ===================

    private fun getDefaultQualifyingThreshold(type: String): Int {
        return when (type) {
            TYPE_WINS -> 1
            TYPE_WINRATE -> 5
            TYPE_KILLS -> 0
            TYPE_KD_RATIO -> 3
            TYPE_LEVEL -> 0
            TYPE_PLAYTIME -> 0
            TYPE_STREAK -> 3
            TYPE_PILLARS -> 0
            else -> 0
        }
    }

    private fun rowToLeaderboardEntry(rs: ResultSet): LeaderboardEntry {
        return LeaderboardEntry(
            playerId = rs.getString("player_id"),
            playerName = rs.getString("player_name"),
            leaderboardType = rs.getString("leaderboard_type"),
            rankPosition = rs.getInt("rank_position"),
            rankValue = rs.getDouble("rank_value"),
            rankDisplayValue = rs.getString("rank_display_value"),
            secondaryValue = rs.getObject("secondary_value") as? Double,
            secondaryDisplay = rs.getString("secondary_display"),
            tertiaryValue = rs.getObject("tertiary_value") as? Double,
            tertiaryDisplay = rs.getString("tertiary_display"),
            percentile = rs.getDouble("percentile"),
            scoreNormalized = rs.getDouble("score_normalized"),
            previousRank = rs.getObject("previous_rank") as? Int,
            rankChange = rs.getInt("rank_change"),
            rankChangeDirection = rs.getString("rank_change_direction"),
            qualifyingGames = rs.getInt("qualifying_games"),
            eligible = rs.getBoolean("eligible"),
            lastUpdated = rs.getObject("last_updated", LocalDateTime::class.java),
            cacheVersion = rs.getInt("cache_version")
        )
    }

    private fun rowToMetadata(rs: ResultSet): LeaderboardMetadata {
        return LeaderboardMetadata(
            leaderboardType = rs.getString("leaderboard_type"),
            lastFullUpdate = rs.getObject("last_full_update", LocalDateTime::class.java),
            lastIncrementalUpdate = rs.getObject("last_incremental_update", LocalDateTime::class.java),
            nextScheduledUpdate = rs.getObject("next_scheduled_update", LocalDateTime::class.java),
            totalEntries = rs.getInt("total_entries"),
            eligibleEntries = rs.getInt("eligible_entries"),
            qualifyingThreshold = rs.getInt("qualifying_threshold"),
            updateDurationMs = rs.getInt("update_duration_ms"),
            cacheHitRate = rs.getDouble("cache_hit_rate"),
            queryCount24h = rs.getInt("query_count_24h"),
            updateFrequencyMinutes = rs.getInt("update_frequency_minutes"),
            autoUpdateEnabled = rs.getBoolean("auto_update_enabled"),
            cacheVersion = rs.getInt("cache_version"),
            schemaVersion = rs.getInt("schema_version"),
            createdAt = rs.getObject("created_at", LocalDateTime::class.java),
            updatedAt = rs.getObject("updated_at", LocalDateTime::class.java)
        )
    }
}