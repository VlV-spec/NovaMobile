#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Word-like ribbon: actual font family/size UNO integration and touch-friendly layout."""
from pathlib import Path

root=Path("source")
engine=root/"engine-office/src/main/java/dev/offlineoffice/office"
ui=root/"app/src/main/java/dev/offlineoffice/app/ui/viewers"

p=engine/"OfficeProtocol.kt"
s=p.read_text()
needle='    const val UNO_COMMAND = 12'
assert s.count(needle)==1
s=s.replace(needle, needle+'''
    const val FONT_FAMILY = 13
    const val FONT_SIZE = 14
''')
needle='    const val KEY_UNO = "uno"'
assert s.count(needle)==1
s=s.replace(needle, needle+'''
    const val KEY_FONT_FAMILY = "fontFamily"
    const val KEY_FONT_SIZE = "fontSize"
''')
p.write_text(s)

p=engine/"OfficeClient.kt"
s=p.read_text()
needle='    fun uno(command: String) = send(P.UNO_COMMAND, data = Bundle().apply { putString(P.KEY_UNO, command) })'
assert s.count(needle)==1
s=s.replace(needle,needle+'''

    /** Change selected text's font, or the input style at the cursor. */
    fun setFontFamily(family: String) {
        send(P.FONT_FAMILY, data = Bundle().apply { putString(P.KEY_FONT_FAMILY, family) })
    }

    /** Point size to be applied by LibreOfficeKit, 6..72 pt. */
    fun setFontSize(points: Int) {
        send(P.FONT_SIZE, data = Bundle().apply { putInt(P.KEY_FONT_SIZE, points) })
    }
''')
p.write_text(s)

p=engine/"OfficeService.kt"
s=p.read_text()
needle='            P.UNO_COMMAND -> document?.uno(msg.data.getString(P.KEY_UNO).orEmpty())'
assert s.count(needle)==1
s=s.replace(needle,needle+'''
            P.FONT_FAMILY -> document?.setFontFamily(msg.data.getString(P.KEY_FONT_FAMILY).orEmpty())
            P.FONT_SIZE -> document?.setFontSize(msg.data.getInt(P.KEY_FONT_SIZE))
''')
p.write_text(s)

p=engine/"OfficeDocument.kt"
s=p.read_text()
needle='    fun uno(command: String) {'
assert s.count(needle)==1
# Insert outside existing UNO command handler, just before it.
prefix=r'''
    private val supportedFamilies = setOf(
        "Liberation Sans", "Liberation Serif", "Liberation Mono",
        "Carlito", "Caladea", "DejaVu Sans", "DejaVu Serif", "Noto Sans",
    )

    /** Send typed UNO parameters to the actual Collabora editor, never to the HTML shell. */
    fun setFontFamily(family: String) {
        if (_state.value != State.Ready || family !in supportedFamilies) return
        val js = "if (window.app && app.map && app.map.sendUnoCommand) " +
            "app.map.sendUnoCommand('.uno:CharFontName', {" +
            "'CharFontName.FamilyName': {type: 'string', value: " +
            JSONObject.quote(family) + "}});"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    fun setFontSize(points: Int) {
        if (_state.value != State.Ready || points !in 6..72) return
        val js = "if (window.app && app.map && app.map.sendUnoCommand) " +
            "app.map.sendUnoCommand('.uno:FontHeight', {" +
            "'FontHeight.Height': {type: 'float', value: '" + points + "'}});"
        webView.post { webView.evaluateJavascript(js, null) }
    }

'''
s=s.replace(needle,prefix+needle)
p.write_text(s)

p=ui/"OfficeView.kt"
s=p.read_text()
s=s.replace(
    "import androidx.compose.foundation.background",
    "import androidx.compose.foundation.background\nimport androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.clickable"
)
s=s.replace(
    "import androidx.compose.material3.Button",
    "import androidx.compose.material3.Button\nimport androidx.compose.material3.DropdownMenu\nimport androidx.compose.material3.DropdownMenuItem\nimport androidx.compose.material3.Card\nimport androidx.compose.material3.CardDefaults"
)
s=s.replace(
    "import androidx.compose.ui.graphics.Color",
    "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.font.FontStyle\nimport androidx.compose.foundation.shape.RoundedCornerShape"
)
s=s.replace(
    'WordRibbon(onCommand = { document.uno(it) })',
    'WordRibbon(onCommand = { document.uno(it) }, onFontFamily = { document.setFontFamily(it) }, onFontSize = { document.setFontSize(it) })'
)
marker='/** Compact Word-like ribbon. Every tool sends a real UNO command to Collabora. */'
assert marker in s
s=s[:s.index(marker)] + r'''
/** Word-like editing ribbon, with real UNO-backed font family and point-size selectors. */
@Composable
private fun WordRibbon(
    onCommand: (String) -> Unit,
    onFontFamily: (String) -> Unit,
    onFontSize: (Int) -> Unit,
) {
    val blue = Color(0xFF185ABD)
    val ink = Color(0xFF203044)
    val familyNames = listOf(
        "Liberation Sans", "Liberation Serif", "Liberation Mono",
        "Carlito", "Caladea", "DejaVu Sans", "DejaVu Serif", "Noto Sans",
    )
    val sizeChoices = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28, 32, 36, 48, 72)
    var tab by rememberSaveable { mutableStateOf("Главная") }
    var familyMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var chosenFamily by rememberSaveable { mutableStateOf("Liberation Sans") }
    var chosenSize by rememberSaveable { mutableStateOf(12) }
    val tabs = listOf("Главная", "Вставка", "Макет")

    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 6.dp)) {
            tabs.forEach { name ->
                Column(
                    modifier = Modifier.clickable { tab = name }
                        .padding(horizontal = 15.dp, vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        name,
                        color = if (tab == name) blue else Color(0xFF65748A),
                        fontWeight = if (tab == name) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(2.dp))
                    Box(Modifier.size(width = 38.dp, height = 3.dp)
                        .background(if (tab == name) blue else Color.Transparent, RoundedCornerShape(2.dp)))
                }
            }
        }

        if (tab == "Главная") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .background(Color(0xFFF5F8FC)).padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFD5DFEB)),
                        modifier = Modifier.clickable { familyMenu = true },
                    ) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(chosenFamily, maxLines = 1,
                                color = ink, style = MaterialTheme.typography.bodyMedium)
                            Text("  ⌄", color = Color(0xFF61738C))
                        }
                    }
                    DropdownMenu(expanded = familyMenu, onDismissRequest = { familyMenu = false }) {
                        familyNames.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name, fontWeight = if(name==chosenFamily) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    chosenFamily = name
                                    familyMenu = false
                                    onFontFamily(name)
                                }
                            )
                        }
                    }
                }
                Box {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFD5DFEB)),
                        modifier = Modifier.clickable { sizeMenu = true },
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("$chosenSize", color = ink, style = MaterialTheme.typography.bodyMedium)
                            Text(" ⌄", color = Color(0xFF61738C))
                        }
                    }
                    DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                        sizeChoices.forEach { pt ->
                            DropdownMenuItem(
                                text = { Text("$pt пт") },
                                onClick = {
                                    chosenSize = pt
                                    sizeMenu = false
                                    onFontSize(pt)
                                }
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .background(Color(0xFFF5F8FC)).padding(start = 7.dp, end = 7.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RibbonAction("Ж", ".uno:Bold", onCommand, weight = FontWeight.Bold)
                RibbonAction("К", ".uno:Italic", onCommand, italic = true)
                RibbonAction("Ч̲", ".uno:Underline", onCommand)
                RibbonAction("↶", ".uno:Undo", onCommand)
                RibbonAction("↷", ".uno:Redo", onCommand)
                RibbonAction("≡", ".uno:LeftPara", onCommand)
                RibbonAction("☷", ".uno:CenterPara", onCommand)
                RibbonAction("≣", ".uno:RightPara", onCommand)
                RibbonAction("• Список", ".uno:DefaultBullet", onCommand)
                RibbonAction("1. Список", ".uno:DefaultNumbering", onCommand)
            }
        } else {
            val buttons = if (tab == "Вставка") {
                listOf("Разрыв страницы" to ".uno:InsertPagebreak", "Гиперссылка" to ".uno:HyperlinkDialog")
            } else listOf("Параметры страницы" to ".uno:PageFormatDialog")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .background(Color(0xFFF5F8FC)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                buttons.forEach { (label, command) ->
                    RibbonAction(label, command, onCommand)
                }
            }
        }
    }
}

@Composable
private fun RibbonAction(
    title: String,
    command: String,
    onCommand: (String) -> Unit,
    weight: FontWeight = FontWeight.Normal,
    italic: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFDCE4EE)),
        modifier = Modifier.clickable { onCommand(command) },
    ) {
        Text(
            title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            color = Color(0xFF23384E), fontWeight = weight,
            fontStyle = if(italic) FontStyle.Italic else FontStyle.Normal,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}
'''
assert 'fun setFontFamily' in (engine/"OfficeDocument.kt").read_text()
assert 'RibbonAction(' in s
p.write_text(s)
print("font family / size bridge, selectors and compact Word-like ribbon patched")
