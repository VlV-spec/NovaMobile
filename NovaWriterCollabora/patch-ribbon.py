#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Add a functional Compose Word-style command ribbon over the LibreOfficeKit editor."""
from pathlib import Path

engine=Path("source/engine-office/src/main/java/dev/offlineoffice/office")
ui=Path("source/app/src/main/java/dev/offlineoffice/app/ui/viewers")

p=engine/"OfficeProtocol.kt"
s=p.read_text()
s=s.replace("    const val EXPORT_PDF = 11","    const val EXPORT_PDF = 11\n    const val UNO_COMMAND = 12")
s=s.replace('    const val KEY_QUERY = "query"','    const val KEY_QUERY = "query"\n    const val KEY_UNO = "uno"')
assert "const val UNO_COMMAND" in s and "KEY_UNO" in s
p.write_text(s)

p=engine/"OfficeClient.kt"
s=p.read_text()
needle="    fun clearFind() = send(P.FIND_CLEAR)"
assert needle in s
s=s.replace(needle,needle+'''

    /** Dispatch an allowlisted LibreOffice command in the editor process. */
    fun uno(command: String) = send(P.UNO_COMMAND, data = Bundle().apply { putString(P.KEY_UNO, command) })
''')
p.write_text(s)

p=engine/"OfficeService.kt"
s=p.read_text()
needle="            P.FIND_CLEAR -> document?.clearFind()"
assert needle in s
s=s.replace(needle,needle+'''
            P.UNO_COMMAND -> document?.uno(msg.data.getString(P.KEY_UNO).orEmpty())''')
p.write_text(s)

p=engine/"OfficeDocument.kt"
s=p.read_text()
needle='    fun clearFind() = webView.post { webView.evaluateJavascript("app.searchService.resetSelection()", null) }'
assert needle in s
s=s.replace(needle,needle+'''

    /** Only known safe editing commands; called from the native Word-like toolbar. */
    fun uno(command: String) {
        if (_state.value != State.Ready) return
        val allowed = setOf(
            ".uno:Bold", ".uno:Italic", ".uno:Underline", ".uno:Undo", ".uno:Redo",
            ".uno:LeftPara", ".uno:CenterPara", ".uno:RightPara",
            ".uno:DefaultBullet", ".uno:DefaultNumbering",
            ".uno:InsertPagebreak", ".uno:HyperlinkDialog", ".uno:PageFormatDialog",
        )
        if (command !in allowed) return
        val js = "if (window.app && app.map && app.map.sendUnoCommand) app.map.sendUnoCommand(" + JSONObject.quote(command) + ");"
        webView.post { webView.evaluateJavascript(js, null) }
    }
''')
p.write_text(s)

p=ui/"OfficeView.kt"
s=p.read_text()
s=s.replace("import androidx.compose.foundation.layout.Arrangement",
'''import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement''')
s=s.replace("import androidx.compose.ui.Alignment",
'''import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment''')
needle="        Box(Modifier.weight(1f).fillMaxWidth()) {"
assert needle in s
s=s.replace(needle,'''        // Visible only for editable Word documents; controls dispatch actual UNO commands.
        if (session.kind == DocumentKind.WORD && state == OfficeClient.State.Ready && !session.readOnly && !LocalFullScreen.current) {
            WordRibbon(onCommand = { document.uno(it) })
        }
'''+needle,1)
s += '''

/** Compact Word-like ribbon. Every tool sends a real UNO command to Collabora. */
@Composable
private fun WordRibbon(onCommand: (String) -> Unit) {
    val primary = Color(0xFF185ABD)
    var selected by rememberSaveable { mutableStateOf("Главная") }
    val tabs = listOf("Главная", "Вставка", "Макет")
    val commands = when (selected) {
        "Вставка" -> listOf("Разрыв страницы" to ".uno:InsertPagebreak", "Гиперссылка" to ".uno:HyperlinkDialog")
        "Макет" -> listOf("Параметры страницы" to ".uno:PageFormatDialog")
        else -> listOf(
            "Ж" to ".uno:Bold",
            "К" to ".uno:Italic",
            "Ч" to ".uno:Underline",
            "↶" to ".uno:Undo",
            "↷" to ".uno:Redo",
            "Слева" to ".uno:LeftPara",
            "Центр" to ".uno:CenterPara",
            "Справа" to ".uno:RightPara",
            "Маркеры" to ".uno:DefaultBullet",
            "Нумерация" to ".uno:DefaultNumbering",
        )
    }
    Column(Modifier.fillMaxWidth().background(Color(0xFFF9FBFE))) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            tabs.forEach { name ->
                TextButton(onClick = { selected = name }) {
                    Text(name, color = if (selected == name) primary else Color(0xFF64748B))
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFFEEF3FB))) {
            commands.forEach { (label, uno) ->
                TextButton(onClick = { onCommand(uno) }) {
                    Text(label, color = Color(0xFF203F6C))
                }
            }
        }
    }
}
'''
p.write_text(s)
print("UNO bridge & functional ribbon patched")
