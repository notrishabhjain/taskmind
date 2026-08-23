package com.notrishabhjain.taskmind.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `notification_captures` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`idempotencyKey` TEXT NOT NULL, " +
                "`sourcePackage` TEXT NOT NULL, " +
                "`sourceAppLabel` TEXT, " +
                "`notificationKey` TEXT NOT NULL, " +
                "`notificationId` INTEGER, " +
                "`notificationTag` TEXT, " +
                "`postTime` INTEGER NOT NULL, " +
                "`title` TEXT, " +
                "`text` TEXT, " +
                "`bigText` TEXT, " +
                "`subText` TEXT, " +
                "`infoText` TEXT, " +
                "`conversationTitle` TEXT, " +
                "`category` TEXT, " +
                "`channelLabel` TEXT, " +
                "`canonicalSourceText` TEXT NOT NULL, " +
                "`contentHash` TEXT NOT NULL, " +
                "`sourceRef` TEXT NOT NULL, " +
                "`state` TEXT NOT NULL, " +
                "`retryCount` INTEGER NOT NULL, " +
                "`lastError` TEXT, " +
                "`resultingTaskId` INTEGER, " +
                "`processedAt` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_notification_captures_idempotency` " +
                "ON `notification_captures` (`idempotencyKey`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notification_captures_state` " +
                "ON `notification_captures` (`state`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notification_captures_sourceRef` " +
                "ON `notification_captures` (`sourceRef`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notification_captures_createdAt` " +
                "ON `notification_captures` (`createdAt`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notification_captures_updatedAt` " +
                "ON `notification_captures` (`updatedAt`)"
        )
    }
}
