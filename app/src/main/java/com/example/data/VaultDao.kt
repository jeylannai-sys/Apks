package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_files ORDER BY importedAtMillis DESC")
    fun getAllVaultFiles(): Flow<List<VaultFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultFile(file: VaultFileEntity)

    @Delete
    suspend fun deleteVaultFile(file: VaultFileEntity)

    @Query("DELETE FROM vault_files")
    suspend fun clearAllVaultFiles()
}
