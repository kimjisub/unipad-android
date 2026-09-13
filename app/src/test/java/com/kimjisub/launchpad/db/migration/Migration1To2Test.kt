package com.kimjisub.launchpad.db.migration

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs MIGRATION_1_2's SQL on a real SQLite database built from the exported v1 schema, then checks the
 * result against the exported v2 schema the way Room validates it (columns, affinity, NOT NULL, primary key).
 * The instrumented AppDatabaseMigrationTest covers the same ground through Room itself on a device.
 */
class Migration1To2Test {

	private lateinit var db: Connection

	@Before
	fun setUp() {
		db = DriverManager.getConnection("jdbc:sqlite::memory:")
		createFromSchema(ExportedSchemas.load(1))
	}

	@After
	fun tearDown() {
		db.close()
	}

	@Test
	fun `migrated database matches the exported v2 schema`() {
		migrate()

		val entities = ExportedSchemas.load(2).getValue("entities").jsonArray.map { it.jsonObject }
		assertEquals(entities.map { it.string("tableName") }.toSet(), userTables())

		for (entity in entities) {
			val table = entity.string("tableName")
			val expectedColumns = entity.getValue("fields").jsonArray.map { it.jsonObject }.map {
				Column(it.string("columnName"), it.string("affinity"), it["notNull"]?.jsonPrimitive?.booleanOrNull ?: false)
			}.toSet()
			val expectedPrimaryKey = entity.getValue("primaryKey").jsonObject.getValue("columnNames").jsonArray
				.map { it.jsonPrimitive.content }

			val actualColumns = mutableSetOf<Column>()
			val actualPrimaryKey = sortedMapOf<Int, String>()
			db.createStatement().use { st ->
				st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
					while (rs.next()) {
						actualColumns += Column(rs.getString("name"), rs.getString("type"), rs.getInt("notnull") == 1)
						if (rs.getInt("pk") > 0) actualPrimaryKey[rs.getInt("pk")] = rs.getString("name")
					}
				}
			}

			assertEquals("columns of $table", expectedColumns, actualColumns)
			assertEquals("primary key of $table", expectedPrimaryKey, actualPrimaryKey.values.toList())
		}
	}

	@Test
	fun `keeps bookmarks, play counts, last played and download dates`() {
		// 4.0.0.b10 and earlier keyed rows by folder name.
		insertPack("Pack A", bookmark = true, createdAt = 1_000)
		insertOpen("Pack A", at = 2_000)
		insertOpen("Pack A", at = 3_000)
		// 4.0.0.b11 keyed the same pack by its absolute path, so a device that ran both has two rows for it.
		insertPack("/storage/emulated/0/Unipad/Pack A", bookmark = false, createdAt = 5_000)
		insertOpen("/storage/emulated/0/Unipad/Pack A", at = 6_000)
		// Non-ASCII folder name, never played.
		insertPack("/storage/emulated/0/Android/data/com.kimjisub.launchpad/files/한글 팩 [Remix]", bookmark = true, createdAt = 7_000)
		insertPack("Pack Unplayed", bookmark = false, createdAt = 7_500)
		// Opens logged for a pack that has no pack row.
		insertOpen("Pack Orphan", at = 8_000)

		migrate()

		assertEquals(
			listOf(
				Row("Pack A", bookmark = 1, openCount = 3, lastOpenedAt = 6_000, createdAt = 1_000),
				Row("Pack Orphan", bookmark = 0, openCount = 1, lastOpenedAt = 8_000, createdAt = 8_000),
				Row("Pack Unplayed", bookmark = 0, openCount = 0, lastOpenedAt = null, createdAt = 7_500),
				Row("한글 팩 [Remix]", bookmark = 1, openCount = 0, lastOpenedAt = null, createdAt = 7_000),
			),
			unipackRows(),
		)
	}

	@Test
	fun `empty v1 database migrates to an empty v2 table`() {
		migrate()

		assertEquals(emptyList<Row>(), unipackRows())
	}

	@Test
	fun `v2 total play count equals the v1 open log size`() {
		insertPack("A", bookmark = false, createdAt = 1)
		insertPack("/x/B", bookmark = false, createdAt = 1)
		repeat(4) { insertOpen("A", at = 10L + it) }
		repeat(2) { insertOpen("/x/B", at = 20L + it) }
		insertOpen("C", at = 30)
		val v1Opens = count("SELECT COUNT(*) FROM UniPackOpenENT")

		migrate()

		assertEquals(v1Opens, count("SELECT COALESCE(SUM(openCount), 0) FROM Unipack"))
	}

	private data class Column(val name: String, val affinity: String, val notNull: Boolean)

	private data class Row(val id: String, val bookmark: Int, val openCount: Long, val lastOpenedAt: Long?, val createdAt: Long)

	private fun createFromSchema(schema: JsonObject) {
		for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
			val table = entity.string("tableName")
			exec(entity.string("createSql").replace("\${TABLE_NAME}", table))
			entity["indices"]?.jsonArray?.forEach {
				exec(it.jsonObject.string("createSql").replace("\${TABLE_NAME}", table))
			}
		}
		schema["setupQueries"]?.jsonArray?.forEach { exec(it.jsonPrimitive.content) }
	}

	private fun migrate() = MIGRATION_1_2_STATEMENTS.forEach(::exec)

	private fun exec(sql: String) {
		db.createStatement().use { it.execute(sql) }
	}

	private fun insertPack(path: String, bookmark: Boolean, createdAt: Long) {
		db.prepareStatement("INSERT INTO UniPackENT (path, padTouch, bookmark, created_at) VALUES (?, 0, ?, ?)").use {
			it.setString(1, path)
			it.setInt(2, if (bookmark) 1 else 0)
			it.setLong(3, createdAt)
			it.executeUpdate()
		}
	}

	private fun insertOpen(path: String, at: Long) {
		db.prepareStatement("INSERT INTO UniPackOpenENT (path, created_at) VALUES (?, ?)").use {
			it.setString(1, path)
			it.setLong(2, at)
			it.executeUpdate()
		}
	}

	private fun unipackRows(): List<Row> = db.createStatement().use { st ->
		st.executeQuery("SELECT id, bookmark, openCount, lastOpenedAt, createdAt FROM Unipack ORDER BY id").use { rs ->
			buildList {
				while (rs.next()) {
					add(
						Row(
							id = rs.getString(1),
							bookmark = rs.getInt(2),
							openCount = rs.getLong(3),
							lastOpenedAt = rs.getLong(4).takeUnless { rs.wasNull() },
							createdAt = rs.getLong(5),
						)
					)
				}
			}
		}
	}

	private fun userTables(): Set<String> = db.createStatement().use { st ->
		st.executeQuery(
			"SELECT name FROM sqlite_master WHERE type = 'table' " +
				"AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata')"
		).use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
	}

	private fun count(sql: String): Long = db.createStatement().use { st ->
		st.executeQuery(sql).use { rs -> rs.next(); rs.getLong(1) }
	}

	private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
}
