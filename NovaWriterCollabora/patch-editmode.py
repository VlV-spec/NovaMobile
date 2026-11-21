#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Bridge native edit-mode controls to Collabora's real document state."""
from pathlib import Path
root=Path("source")
engine=root/"engine-office/src/main/java/dev/offlineoffice/office"
p=engine/"OfficeProtocol.kt";s=p.read_text()
a='    const val FONT_SIZE = 14'
assert s.count(a)==1
s=s.replace(a,a+"\n    const val ENTER_EDIT_MODE = 15")
p.write_text(s)

p=engine/"OfficeClient.kt";s=p.read_text()
a="    fun setFontFamily(family: String) {"
assert s.count(a)==1
s=s.replace(a,"    /** Switch the active document from viewing to editing. */\n    fun enterEditMode() = send(P.ENTER_EDIT_MODE)\n\n"+a)
p.write_text(s)

p=engine/"OfficeService.kt";s=p.read_text()
a='            P.FONT_FAMILY -> document?.setFontFamily(msg.data.getString(P.KEY_FONT_FAMILY).orEmpty())'
assert s.count(a)==1
s=s.replace(a,a+"\n            P.ENTER_EDIT_MODE -> document?.enterEditMode()")
p.write_text(s)

p=engine/"OfficeDocument.kt";s=p.read_text()
a='    fun setFontFamily(family: String) {'
assert s.count(a)==1
# Replace only command invocation inside our methods and the earlier UNO method, not unrelated scripts.
start=s.index("    private val supportedFamilies = setOf(")
end=s.index("    /** The current selection as plain text",start)
t=s[start:end]
t=t.replace("webView.post { webView.evaluateJavascript(js, null) }", "executeFormatting(js)")
assert t.count("executeFormatting(js)")==3, t.count("executeFormatting(js)")
s=s[:start]+t+s[end:]
# On Android, the front-end begins in reading mode. Do not silently drop formatting.
a='    private val supportedFamilies = setOf('
assert a in s
wrapper=r"""
    /** Official mobile edit button first, then dispatch the formatting command.
     * This avoids the silent no-op of sendUnoCommand while the viewer is in reading mode.
     */
    fun enterEditMode() {
        if (_state.value != State.Ready || readOnly || editing) return
        webView.post {
            webView.evaluateJavascript(
                "(function(){ const b=document.getElementById('mobile-edit-button'); " +
                "if(b){b.click();return 'button';} " +
                "if(window.app&&app.map&&app.map.setPermission){app.map.setPermission('edit');return 'map';} " +
                "return 'unavailable'; })();", null
            )
        }
    }

    private fun executeFormatting(js: String) {
        if (_state.value != State.Ready || readOnly) return
        webView.post {
            if (editing) {
                webView.evaluateJavascript(js, null)
                return@post
            }
            enterEditMode()
            // The mobile pencil switches permission asynchronously; wait for the actual
            // EDITMODE event instead of firing the UNO command in view mode.
            var tries = 0
            val attempt = object : Runnable {
                override fun run() {
                    if (_state.value != State.Ready) return
                    if (editing) webView.evaluateJavascript(js, null)
                    else if (++tries < 30) webView.postDelayed(this, 100)
                    else Log.w(TAG, "Formatting command was not sent: editor stayed in view mode")
                }
            }
            webView.postDelayed(attempt, 100)
        }
    }

"""
s=s.replace(a,wrapper+a,1)
# Update a state marker when user edits, so commands from the native toolbar are in sync.
assert '"EDITMODE" -> editing = message.substringAfter' in s
s=s.replace('"EDITMODE" -> editing = message.substringAfter', '"EDITMODE" -> editing = message.substringAfter')
p.write_text(s)

v=root/"app/src/main/java/dev/offlineoffice/app/ui/viewers/OfficeView.kt"
s=v.read_text()
needle='WordRibbon(onCommand = { document.uno(it) }, onFontFamily = { document.setFontFamily(it) }, onFontSize = { document.setFontSize(it) })'
assert s.count(needle)==1
s=s.replace(needle,'WordRibbon(onCommand = { document.uno(it) }, onFontFamily = { document.setFontFamily(it) }, onFontSize = { document.setFontSize(it) }, onEdit = { document.enterEditMode() })')
# Remove the initial throwaway ribbon: implement a single polished UI component instead.
needle='/** Word-like editing ribbon, with real UNO-backed font family and point-size selectors. */'
assert s.count(needle)==1
s=s[:s.index(needle)]
v.write_text(s)

# Use Russian Collabora UI strings where available, not only our native toolbar.
p=engine/"OfficeDocument.kt"
s=p.read_text()
s=s.replace('.appendQueryParameter("lang", "en-US")','.appendQueryParameter("lang", "ru-RU")')
p.write_text(s)
print("Editing-mode guard and native ribbon integration ready")
