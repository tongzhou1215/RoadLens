package com.cs407.roadlens.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_contact")
data class EmergencyContact(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val phone: String
)