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
import com.cs407.roadlens.viewmodel.ClipKind
import com.cs407.roadlens.viewmodel.ClipUploadStatus
import com.cs407.roadlens.viewmodel.RecordingClip
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AlbumScreen(
    clips: List<RecordingClip>,
    onBack: () -> Unit = {},
    onDeleteClips: (Set<Long>) -> Unit = {}
) {
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .navigationBarsPadding()
    ) {
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

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by file name...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        val filtered = remember(clips, query) {
            if (query.isBlank()) clips else clips.filter { clip ->
                clip.fileName.contains(query, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No clips saved yet. Start a recording to see it here.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { clip ->
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
            }
        }

        BottomActions(
            selectionMode = selectionMode,
            hasSelection = selected.isNotEmpty(),
            onToggleSelection = {
                selectionMode = !selectionMode
                if (!selectionMode) selected.clear()
            },
            onDelete = {
                onDeleteClips(selected.toSet())
                selectionMode = false
                selected.clear()
            }
        )
    }
}

@Composable
private fun AlbumCard(
    item: RecordingClip,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit
) {
    val (borderColor, badgeBg, titleColor, statusColor) = when (item.kind) {
        ClipKind.LOOP -> Quadruple(Color(0xFF22C55E), Color(0xFFEFFBF3), Color(0xFF0F5132), Color(0xFF0F5132))
        ClipKind.MANUAL -> Quadruple(Color(0xFFFFB74D), Color(0xFFFFF4E5), Color(0xFFB76E00), Color(0xFFB76E00))
    }

    val stroke = if (selected) BorderStroke(2.dp, Color(0xFF3B82F6)) else BorderStroke(1.dp, borderColor.copy(alpha = 0.5f))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { if (selectionMode) onClick() },
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
                    text = when (item.kind) {
                        ClipKind.LOOP -> "Loop Recording"
                        ClipKind.MANUAL -> "Manual Recording"
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(text = buildDateLine(item), color = Color.DarkGray, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Status: ${statusLabel(item.uploadStatus)}",
                    color = statusColor,
                    fontSize = 13.sp
                )
            }

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
    onDelete: () -> Unit
) {
    val canDelete = selectionMode && hasSelection

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
            OutlinedButton(
                onClick = onToggleSelection,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) { Text(if (selectionMode) "Cancel" else "Select") }

            Button(
                onClick = onDelete,
                enabled = canDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF87171))
            ) { Text("Delete Selected") }
        }
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component4() = d

private fun buildDateLine(clip: RecordingClip): String {
    val instant = Instant.ofEpochMilli(clip.recordedAt)
    val zoned = instant.atZone(ZoneId.systemDefault())
    val datePart = DATE_FORMATTER.format(zoned)
    val timePart = TIME_FORMATTER.format(zoned)
    val durationPart = formatDuration(clip.durationSeconds)
    return "$datePart | $timePart | $durationPart"
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        String.format("%dm %02ds", minutes, seconds)
    } else {
        String.format("%ds", seconds)
    }
}

private fun statusLabel(status: ClipUploadStatus): String = when (status) {
    ClipUploadStatus.LOCAL_ONLY -> "Local Only"
    ClipUploadStatus.SYNCED -> "Backed Up"
}

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
