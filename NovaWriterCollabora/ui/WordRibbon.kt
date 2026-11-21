// SPDX-License-Identifier: GPL-3.0-or-later
// Nova Writer: original mobile command ribbon. LibreOfficeKit is the editing engine.
package dev.offlineoffice.app.ui.viewers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.InsertLink
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val WordBlue = Color(0xFF185ABD)
private val WordInk = Color(0xFF22324A)
private val WordMuted = Color(0xFF687890)
private val WordLight = Color(0xFFF5F8FC)
private val WordBorder = Color(0xFFDCE4EF)

private val fonts = listOf(
    "Calibri", "Aptos", "Arial", "Times New Roman", "Cambria", "Georgia",
    "Verdana", "Tahoma", "Courier New",
    "Liberation Sans", "Liberation Serif", "Liberation Mono",
    "Carlito", "Caladea", "DejaVu Sans", "DejaVu Serif", "Noto Sans"
)
private val sizes = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28, 32, 36, 48, 72)

/**
 * A native, thumb-friendly ribbon with a consistent visual hierarchy.
 * All editing controls call the actual LibreOfficeKit command bridge.
 * The font name shown is the user's last requested value, NOT an engine-confirmed
 * measurement of the current selection.
 */
@Composable
internal fun WordRibbon(
    onCommand: (String) -> Unit,
    onFontFamily: (String) -> Unit,
    onFontSize: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf("Главная") }
    var font by rememberSaveable { mutableStateOf("Выбрать шрифт") }
    var pt by rememberSaveable { mutableStateOf(12) }
    var fontOpen by remember { mutableStateOf(false) }
    var sizeOpen by remember { mutableStateOf(false) }

    Surface(
        color = Color.White,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Форматирование",
                    color = WordInk,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8F1FF),
                    modifier = Modifier.clickable(onClick = onEdit),
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = WordBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Редактировать", color = WordBlue, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("Главная", "Вставка", "Макет").forEach { tab ->
                    Column(
                        Modifier.clickable { section = tab }.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            tab,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (tab == section) WordBlue else WordMuted,
                            fontWeight = if (tab == section) FontWeight.Bold else FontWeight.Medium,
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier.size(width = 36.dp, height = 3.dp)
                                .background(if (tab == section) WordBlue else Color.Transparent, RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
            Divider(color = WordBorder, thickness = 1.dp)

            if (section == "Главная") {
                Column(Modifier.fillMaxWidth().background(WordLight)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            FieldChip(value = font, caption = "ШРИФТ", onClick = { fontOpen = true })
                            DropdownMenu(
                                expanded = fontOpen,
                                onDismissRequest = { fontOpen = false },
                            ) {
                                fonts.forEach { family ->
                                    DropdownMenuItem(
                                        text = { Text(family, color = WordInk, maxLines = 1) },
                                        onClick = {
                                            fontOpen = false
                                            font = family
                                            onFontFamily(family)
                                        },
                                    )
                                }
                            }
                        }
                        Box(Modifier.width(95.dp)) {
                            FieldChip(value = pt.toString() + " пт", caption = "РАЗМЕР", onClick = { sizeOpen = true })
                            DropdownMenu(expanded = sizeOpen, onDismissRequest = { sizeOpen = false }) {
                                sizes.forEach { choice ->
                                    DropdownMenuItem(
                                        text = { Text(choice.toString() + " пт", color = WordInk) },
                                        onClick = {
                                            sizeOpen = false
                                            pt = choice
                                            onFontSize(choice)
                                        },
                                    )
                                }
                            }
                        }
                        Surface(
                            color = Color.White, shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, WordBorder),
                            modifier = Modifier.clickable {
                                pt = (pt + 1).coerceAtMost(72)
                                onFontSize(pt)
                            },
                        ) {
                            Text("A+", Modifier.padding(horizontal = 9.dp, vertical = 13.dp), color = WordBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(start = 10.dp, end = 10.dp, bottom = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tool("Ж", ".uno:Bold", onCommand, strong = true)
                        Tool("К", ".uno:Italic", onCommand)
                        Tool("Ч̲", ".uno:Underline", onCommand)
                        VerticalGap()
                        ToolIcon(Icons.Outlined.Undo, "Отменить", ".uno:Undo", onCommand)
                        ToolIcon(Icons.Outlined.Redo, "Повторить", ".uno:Redo", onCommand)
                        VerticalGap()
                        ToolIcon(Icons.Outlined.FormatAlignLeft, "Слева", ".uno:LeftPara", onCommand)
                        ToolIcon(Icons.Outlined.FormatAlignCenter, "По центру", ".uno:CenterPara", onCommand)
                        ToolIcon(Icons.Outlined.FormatAlignRight, "Справа", ".uno:RightPara", onCommand)
                        VerticalGap()
                        ToolIcon(Icons.Outlined.FormatListBulleted, "Маркеры", ".uno:DefaultBullet", onCommand)
                        ToolIcon(Icons.Outlined.FormatListNumbered, "Нумерация", ".uno:DefaultNumbering", onCommand)
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .background(WordLight).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (section == "Вставка") {
                        Tool("Разрыв страницы", ".uno:InsertPagebreak", onCommand)
                        ToolIcon(Icons.Outlined.InsertLink, "Гиперссылка", ".uno:HyperlinkDialog", onCommand)
                    } else {
                        Tool("Параметры страницы", ".uno:PageFormatDialog", onCommand)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldChip(value: String, caption: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, WordBorder),
        color = Color.White,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(caption, color = WordMuted, style = MaterialTheme.typography.labelSmall)
                Text(
                    value,
                    color = WordInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(17.dp), tint = WordMuted)
        }
    }
}

@Composable
private fun Tool(label: String, command: String, onCommand: (String) -> Unit, strong: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WordBorder),
        color = Color.White,
        modifier = Modifier.clickable { onCommand(command) },
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            color = WordInk,
            fontWeight = if (strong) FontWeight.Black else FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String, command: String, onCommand: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WordBorder),
        color = Color.White,
        modifier = Modifier.clickable { onCommand(command) },
    ) {
        Icon(
            icon, contentDescription = label,
            modifier = Modifier.padding(12.dp).size(19.dp), tint = WordInk,
        )
    }
}

@Composable
private fun VerticalGap() {
    Spacer(Modifier.width(5.dp))
}
