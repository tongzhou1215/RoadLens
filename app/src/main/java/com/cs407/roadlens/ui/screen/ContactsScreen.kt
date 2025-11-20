package com.cs407.roadlens.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.cs407.roadlens.viewmodel.EmergencyContactViewModel
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(
    viewModel: EmergencyContactViewModel,
    onBack: () -> Unit = {},
) {

    if (!viewModel.isLoaded) {
        Text("Loading...")
        return
    }

    var editing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {

        Text("Emergency Contact", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.name,
            onValueChange = { viewModel.name = it },
            label = { Text("Name") },
            enabled = editing,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = viewModel.phone,
            onValueChange = { viewModel.phone = it },
            label = { Text("Phone") },
            enabled = editing,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!editing) {
            Button(onClick = { editing = true }) {
                Text("Edit")
            }
        } else {
            Row {

                Button(onClick = {
                    viewModel.saveContact()
                    editing = false
                }) {
                    Text("Save")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(onClick = {
                    // Reset to values saved in DB
                    editing = false
                    // Reload from DB
                    viewModel.viewModelScope.launch {
                        val contact = viewModel.repo.getContact()
                        viewModel.clearChanges(contact)
                    }
                }) {
                    Text("Cancel")
                }
            }
        }
    }
}