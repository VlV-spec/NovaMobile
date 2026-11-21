// SPDX-License-Identifier: GPL-3.0-or-later
// Nova Writer home layout derived from Libreon; GPL-3.0-or-later.
package dev.offlineoffice.app.ui

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.offlineoffice.app.tabs.OpenOrigin
import dev.offlineoffice.app.tabs.TabManager
import kotlinx.coroutines.launch

private val OfficeBlue = Color(0xFF185ABD)
private val OfficeBg = Color(0xFFF4F6FA)

@Composable
fun HomeScreen(
    tabs: TabManager,
    onOpen: () -> Unit,
    onNew: () -> Unit,
    onSelectTab: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val openTabs by tabs.tabs.collectAsState()
    val recents by tabs.recents.collectAsState(emptyList())
    val closed by tabs.closedTabs.collectAsState(emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val matcher = remember(query) { query.trim() }
    val openFiltered = remember(openTabs, matcher) {
        if (matcher.isBlank()) openTabs else openTabs.filter { it.displayName.contains(matcher, true) }
    }
    val recentFiltered = remember(recents, matcher) {
        if (matcher.isBlank()) recents else recents.filter { it.displayName.contains(matcher, true) }
    }
    val closedFiltered = remember(closed, matcher) {
        if (matcher.isBlank()) closed else closed.filter { it.displayName.contains(matcher, true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(OfficeBg),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text("Добро пожаловать", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF202733))
                Spacer(Modifier.height(5.dp))
                Text("Создавай и редактируй документы без аккаунта", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF607085))
                Spacer(Modifier.height(22.dp))
                Text("Создать", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF202733))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onNew),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFD9E3F1)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFE9F1FF)) {
                                Icon(Icons.Outlined.Description, null, Modifier.padding(12.dp).size(30.dp), tint = OfficeBlue)
                            }
                            Text("Пустой документ", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF202733))
                            Text("DOCX · A4", style = MaterialTheme.typography.labelSmall, color = Color(0xFF67758A))
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFD9E3F1)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF0F2F5)) {
                                Icon(Icons.Outlined.FolderOpen, null, Modifier.padding(12.dp).size(30.dp), tint = Color(0xFF526176))
                            }
                            Text("Открыть файл", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF202733))
                            Text("DOCX и другие", style = MaterialTheme.typography.labelSmall, color = Color(0xFF67758A))
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("Документы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF202733))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск по документам") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
        if (openFiltered.isNotEmpty()) {
            item { DocumentSection("Открытые документы") }
            items(openFiltered, key = { "open-" + it.id }) { tab ->
                DocumentRow(
                    title = tab.displayName,
                    subtitle = if (tab.dirty) "Есть несохранённые изменения" else "Открыт",
                    onClick = { onSelectTab(tab.id) },
                )
            }
        }
        if (recentFiltered.isNotEmpty()) {
            item { DocumentSection("Недавние") }
            items(recentFiltered, key = { "recent-" + it.uri }) { recent ->
                val size = recent.size?.let { Formatter.formatShortFileSize(context, it) + " · " }.orEmpty()
                val time = DateUtils.getRelativeTimeSpanString(recent.lastOpenedAt).toString()
                DocumentRow(
                    title = recent.displayName,
                    subtitle = size + time,
                    onClick = {
                        if (!recent.reopenable) {
                            onMessage("Открой этот файл через «Открыть файл»: доступ к нему изменился")
                        } else {
                            scope.launch {
                                runCatching { tabs.open(Uri.parse(recent.uri), OpenOrigin.PICKER, recent.mimeType) }
                                    .onFailure { onMessage("Не удалось открыть документ: " + (it.message ?: "ошибка")) }
                            }
                        }
                    },
                )
            }
        }
        if (closedFiltered.isNotEmpty() && matcher.isBlank()) {
            item { DocumentSection("Недавно закрытые") }
            items(closedFiltered, key = { "closed-" + it.id }) { tab ->
                DocumentRow(title = tab.displayName, subtitle = "Закрыт", onClick = {
                    scope.launch { if (!tabs.reopen(tab)) onMessage("Файл недоступен — открой его через выбор файла") }
                })
            }
        }
        if (recentFiltered.isEmpty() && openFiltered.isEmpty() && closedFiltered.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 34.dp, horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.InsertDriveFile, null, Modifier.size(42.dp), tint = Color(0xFF98A5B9))
                    Spacer(Modifier.height(12.dp))
                    Text(if (matcher.isEmpty()) "Пока нет документов" else "Ничего не найдено", color = Color(0xFF64748B))
                    Spacer(Modifier.height(8.dp))
                    Text(if (matcher.isEmpty()) "Начни с пустого документа или открой файл на устройстве" else "Измени запрос", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DocumentSection(label: String) {
    Text(label, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF39495B))
}

@Composable
private fun DocumentRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE9F1FF)) {
                Icon(Icons.Outlined.Description, null, Modifier.padding(9.dp).size(24.dp), tint = OfficeBlue)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = Color(0xFF263341))
                Spacer(Modifier.height(3.dp))
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color(0xFF607085))
            }
        }
    }
}
