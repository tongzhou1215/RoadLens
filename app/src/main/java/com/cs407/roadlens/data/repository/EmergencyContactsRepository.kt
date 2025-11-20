package com.cs407.roadlens.data.repository

import com.cs407.roadlens.data.local.dao.EmergencyContactDao
import com.cs407.roadlens.data.local.entities.EmergencyContact

class EmergencyContactRepository(private val dao: EmergencyContactDao) {

    suspend fun getContact(): EmergencyContact? = dao.getContact()

    suspend fun saveContact(contact: EmergencyContact) = dao.saveContact(contact)
}
