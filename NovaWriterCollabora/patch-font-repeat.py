#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Correct repeated font switching using Collabora's own toolbar APIs.

Avoids bypassing map.applyFont() / map.applyFontSize() and restores cursor focus
exactly like upstream's TopToolbar. Exposes only fonts actually installed in
the bundled LibreOfficeKit package to avoid invisible font substitution.
"""
from pathlib import Path

p = Path("source/engine-office/src/main/java/dev/offlineoffice/office/OfficeDocument.kt")
s = p.read_text(encoding="utf-8")
start = s.index("    private val supportedFamilies = setOf(")
end = s.index("    /** Send typed UNO parameters",start)
s=s[:start]+'''    /** Font families physically included with the native LibreOfficeKit package. */
    private val supportedFamilies = setOf(
        "Carlito", "Caladea", "Liberation Sans", "Liberation Serif",
        "Liberation Mono", "Liberation Sans Narrow",
        "Gentium Basic", "Gentium Book Basic",
    )

'''+s[end:]
start=s.index("    fun setFontFamily(family: String) {")
end=s.index("    fun setFontSize(points: Int)",start)
s=s[:start]+'''    fun setFontFamily(family: String) {
        if (_state.value != State.Ready || family !in supportedFamilies) return
        // The original Collabora toolbar calls map.applyFont() and then map.focus().
        // Calling sendUnoCommand directly loses the editor's selection/typing focus
        // after our external Android Compose dropdown, so later changes may not stick.
        val js = "(function() {" +
            "const m = window.app && app.map;" +
            "if (!m || !m.isEditMode || !m.isEditMode() || !m.applyFont) return 'no-edit';" +
            "m.applyFont(" + JSONObject.quote(family) + ");" +
            "m.focus();" +
            // Android's native popup can close AFTER the immediate focus call.
            // Refocus once more after its dismiss animation so the caret sticks.
            "setTimeout(function(){ if(m.refocus) m.refocus(); else m.focus(); }, 180);" +
            "return 'font-sent';" +
            "})()"
        executeFormatting(js)
    }

'''+s[end:]
start=s.index("    fun setFontSize(points: Int) {")
end=s.index("    fun uno(command: String)",start)
s=s[:start]+'''    fun setFontSize(points: Int) {
        if (_state.value != State.Ready || points !in 6..72) return
        val js = "(function() {" +
            "const m = window.app && app.map;" +
            "if (!m || !m.isEditMode || !m.isEditMode() || !m.applyFontSize) return 'no-edit';" +
            "m.applyFontSize(" + points + ");" +
            "m.focus();" +
            // Android's native popup can close AFTER the immediate focus call.
            // Refocus once more after its dismiss animation so the caret sticks.
            "setTimeout(function(){ if(m.refocus) m.refocus(); else m.focus(); }, 180);" +
            "return 'size-sent';" +
            "})()"
        executeFormatting(js)
    }

    /** Allow command delivery to be inspected through adb logcat. */
'''+s[end:]
# Log the return result to distinguish a received command from a no-op, for
# both already-editing and pending-edit-mode dispatch paths.
x="webView.evaluateJavascript(js, null)"
assert s.count(x)>=2,("cannot find JS dispatch",s.count(x))
# Only replace within executeFormatting, not other source.
a=s.index("    private fun executeFormatting(js: String)")
b=s.index("    private val supportedFamilies",a)
segment=s[a:b]
assert segment.count(x)==2,segment.count(x)
segment=segment.replace(x,'''webView.evaluateJavascript(js) { result ->
                    Log.d(TAG, "NovaWriter formatting dispatch result: $result")
                }''')
s=s[:a]+segment+s[b:]
p.write_text(s,encoding="utf-8")

p=Path("NovaWriterCollabora/ui/WordRibbon.kt")
s=p.read_text(encoding="utf-8")
start=s.index("private val fonts = listOf(")
end=s.index("private val sizes = listOf(",start)
s=s[:start]+'''/** All of these are genuinely installed; missing proprietary fonts aren't
 * displayed as if they rendered without substitution. */
private val fonts = listOf(
    "Carlito",                // Similar proportions to Calibri
    "Liberation Sans",       // Similar to Arial
    "Liberation Serif",      // Similar to Times New Roman
    "Caladea",               // Similar to Cambria
    "Liberation Sans Narrow",
    "Liberation Mono",
    "Gentium Basic",
    "Gentium Book Basic",
)
'''+s[end:]
s=s.replace('var font by rememberSaveable { mutableStateOf("Выбрать шрифт") }',
            'var font by rememberSaveable { mutableStateOf("Выбрать шрифт") }')
s=s.replace('caption = "ШРИФТ"', 'caption = "ШРИФТ · УСТАНОВЛЕННЫЕ"')
p.write_text(s,encoding="utf-8")
assert "m.applyFont(" in Path("source/engine-office/src/main/java/dev/offlineoffice/office/OfficeDocument.kt").read_text()
assert "m.focus();" in Path("source/engine-office/src/main/java/dev/offlineoffice/office/OfficeDocument.kt").read_text()
assert '"Times New Roman",' not in s, "Missing proprietary font should not be offered"
print("Repeated font switching uses built-in Collabora API + focus, and installed fonts only.")
