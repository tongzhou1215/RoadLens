package com.cs407.roadlens.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cs407.roadlens.data.local.entities.EmergencyContact

@Dao
interface EmergencyContactDao {

    @Query("SELECT * FROM emergency_contact WHERE id = 1")
    suspend fun getContact(): EmergencyContact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveContact(contact: EmergencyContact)

}