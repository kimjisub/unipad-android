package com.kimjisub.launchpad.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DB v1 -> v2. v1 shipped from the Room refactor (891c9458, 2019) through 4.0.0.b11 (versionCode 92);
 * v2 arrived with ad16af95 in 4.0.0.b12 (versionCode 93). No schema was exported at the time, so both
 * sides were reconstructed from the entity sources in git (see app/schemas for the JSON).
 *
 * v1 kept one row per pack and a separate log with one row per open:
 * - UniPackENT(path TEXT PK, padTouch INTEGER, bookmark INTEGER, created_at INTEGER)
 * - UniPackOpenENT(id INTEGER PK AUTOINCREMENT, path TEXT, created_at INTEGER)
 *
 * v2 folds the log into the pack row:
 * - Unipack(id TEXT PK, bookmark INTEGER, openCount INTEGER, lastOpenedAt INTEGER NULL, createdAt INTEGER)
 *
 * Dates are epoch milliseconds on both sides (the same DateConverter). padTouch has no v2 column and is dropped.
 *
 * The key needs care. Up to 4.0.0.b10 `path` held the pack folder name, 4.0.0.b11 stored the absolute folder
 * path (92ce5c0d), and v2 went back to the folder name (09972b9f). So the key is the last path segment, and a
 * device that ran both b10 and b11 has two v1 rows for one pack, which merge: bookmarked if either was, opens
 * from both counted, earliest creation kept. Opens logged for a pack with no UniPackENT row still become a row,
 * so the total play count does not shrink.
 *
 * The SQL sticks to what SQLite 3.9 (API 24, minSdk) understands. The builds before b82dc38d (2019) spelled the
 * tables UnipackENT/UnipackOpenENT; SQLite table names are case-insensitive, so those resolve too.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
	override fun migrate(db: SupportSQLiteDatabase) {
		MIGRATION_1_2_STATEMENTS.forEach(db::execSQL)
	}
}

// Everything up to and including the last '/' is removed. A value without '/' is returned unchanged.
private const val FOLDER_NAME = "substr(`path`, length(rtrim(`path`, replace(`path`, '/', ''))) + 1)"

private const val NOW_MILLIS = "CAST(strftime('%s', 'now') AS INTEGER) * 1000"

internal val MIGRATION_1_2_STATEMENTS: List<String> = listOf(
	"CREATE TABLE IF NOT EXISTS `Unipack` (`id` TEXT NOT NULL, `bookmark` INTEGER NOT NULL, " +
		"`openCount` INTEGER NOT NULL, `lastOpenedAt` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",

	"INSERT INTO `Unipack` (`id`, `bookmark`, `openCount`, `lastOpenedAt`, `createdAt`) " +
		"SELECT `folder`, MAX(`bookmark`), SUM(`opened`), MAX(`openedAt`), COALESCE(MIN(`created_at`), $NOW_MILLIS) " +
		"FROM (" +
		"SELECT $FOLDER_NAME AS `folder`, COALESCE(`bookmark`, 0) AS `bookmark`, 0 AS `opened`, " +
		"NULL AS `openedAt`, `created_at` FROM `UniPackENT` WHERE `path` IS NOT NULL " +
		"UNION ALL " +
		"SELECT $FOLDER_NAME, 0, 1, `created_at`, `created_at` FROM `UniPackOpenENT` WHERE `path` IS NOT NULL" +
		") WHERE `folder` <> '' GROUP BY `folder`",

	"DROP TABLE IF EXISTS `UniPackOpenENT`",
	"DROP TABLE IF EXISTS `UniPackENT`",
)
