package com.cs407.roadlens.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- UI entry point ---
@Composable
fun AlbumScreen(
    onBack: () -> Unit = {}
) {
    // selection mode + selections
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    // sample list (replace with real data later)
    val clips = remember {
        listOf(
            ClipItem(
                id = "1",
                title = "CRITICAL: Auto-Saved Crash",
                dateLine = "Oct 10, 2025 | 14:32 | 35s",
                status = "Not Backed Up",
                kind = ClipKind.CRITICAL
            ),
            ClipItem(
                id = "2",
                title = "Manual Recording",
                dateLine = "Oct 10, 2025 | 09:12 | 1m 20s",
                status = "Local",
                kind = ClipKind.MANUAL
            ),
            ClipItem(
                id = "3",
                title = "Manual Recording",
                dateLine = "Oct 07, 2025 | 18:44 | 5m 0s",
                status = "Local",
                kind = ClipKind.MANUAL
            )
        )
    }

    // search text (visual only for now)
    var query by remember { mutableStateOf("") }

    // layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .navigationBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 8.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Video Album",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Search
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by date, type, or tag...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        // List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(clips, key = { it.id }) { clip ->
                val isSelected = clip.id in selected
                AlbumCard(
                    item = clip,
                    selected = isSelected,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) {
                            if (isSelected) selected.remove(clip.id) else selected.add(clip.id)
                        }
                    }
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("... Scroll for more clips ...", color = Color.Gray) }
            }
        }

        // Bottom actions
        BottomActions(
            selectionMode = selectionMode,
            hasSelection = selected.isNotEmpty(),
            onToggleSelection = {
                selectionMode = !selectionMode
                if (!selectionMode) selected.clear()
            },
            onUpload = { /* requires selection; disabled until allowed */ },
            onDelete = { /* requires selection; disabled until allowed */ }
        )
    }
}

// --- Components ---

@Composable
private fun AlbumCard(
    item: ClipItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit
) {
    val (borderColor, badgeBg, titleColor, statusColor) = when (item.kind) {
        ClipKind.CRITICAL -> Quadruple(Color(0xFFFF6B6B), Color(0xFFFFEFEF), Color(0xFFB00020), Color(0xFFB00020))
        ClipKind.MANUAL -> Quadruple(Color(0xFF22C55E), Color(0xFFEFFBF3), Color(0xFF0F5132), Color(0xFF0F5132))
    }

    val stroke = if (selected) BorderStroke(2.dp, Color(0xFF3B82F6)) else BorderStroke(1.dp, borderColor.copy(alpha = 0.5f))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = badgeBg,
        tonalElevation = if (selected) 4.dp else 1.dp,
        border = stroke
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(text = item.dateLine, color = Color.DarkGray, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Status: ${item.status}",
                    color = statusColor,
                    fontSize = 13.sp
                )
            }

            // trailing chevron (visual)
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = borderColor
            )
        }
    }
}

@Composable
private fun BottomActions(
    selectionMode: Boolean,
    hasSelection: Boolean,
    onToggleSelection: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit
) {
    // upload/delete only enabled when selection mode ON and something selected
    val canAct = selectionMode && hasSelection

    Surface(
        color = Color(0xFFF6F6F8),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Upload
            FilledTonalButton(
                onClick = onUpload,
                enabled = canAct,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Upload to Cloud") }

            // Select toggle
            OutlinedButton(
                onClick = onToggleSelection,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) { Text(if (selectionMode) "Cancel" else "Select") }

            // Delete
            Button(
                onClick = onDelete,
                enabled = canAct,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF87171))
            ) { Text("Delete Selected") }
        }
    }
}

// --- Models (UI-only placeholder) ---

private enum class ClipKind { CRITICAL, MANUAL }

private data class ClipItem(
    val id: String,
    val title: String,
    val dateLine: String,
    val status: String,
    val kind: ClipKind
)

// Small tuple helper
private data class Quadruple<A,B,C,D>(val a:A,val b:B,val c:C,val d:D)
private operator fun <A,B,C,D> Quadruple<A,B,C,D>.component1() = a
private operator fun <A,B,C,D> Quadruple<A,B,C,D>.component2() = b
private operator fun <A,B,C,D> Quadruple<A,B,C,D>.component3() = c
private operator fun <A,B,C,D> Quadruple<A,B,C,D>.component4() = d
