package com.cs407.roadlens.viewmodel.factories

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cs407.roadlens.data.local.db.AppDatabase
import com.cs407.roadlens.data.repository.EmergencyContactRepository
import com.cs407.roadlens.viewmodel.EmergencyContactViewModel

class EmergencyContactViewModelFactory(private val context: Context) :
    ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)
        val repo = EmergencyContactRepository(db.emergencyContactDao())
        return EmergencyContactViewModel(repo) as T
    }
}