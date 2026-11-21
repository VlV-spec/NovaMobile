#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Keep the mobile editing indicator in sync with Collabora's actual EDITMODE events."""
from pathlib import Path
root=Path("source")
eng=root/"engine-office/src/main/java/dev/offlineoffice/office"
p=eng/"OfficeProtocol.kt"
s=p.read_text();a="    const val VIEW_TAP = 112"
assert s.count(a)==1
s=s.replace(a,a+"\n    const val EDIT_MODE = 113")
p.write_text(s)

p=eng/"OfficeDocument.kt";s=p.read_text()
a="    @Volatile private var editing = false"
assert s.count(a)==1
s=s.replace(a,a+"\n    val editMode = MutableStateFlow(false)")
a='"EDITMODE" -> editing = message.substringAfter(\' \', "").trim() == "on"'
assert s.count(a)==1
s=s.replace(a,'''"EDITMODE" -> {
                editing = message.substringAfter(' ', "").trim() == "on"
                editMode.value = editing
            }''')
p.write_text(s)

p=eng/"OfficeService.kt";s=p.read_text()
a="        scope.launch { doc.modified.collect { send(P.MODIFIED, arg1 = if (it) 1 else 0) } }"
assert s.count(a)==1
s=s.replace(a,a+"\n        scope.launch { doc.editMode.collect { send(P.EDIT_MODE, arg1 = if (it) 1 else 0) } }")
p.write_text(s)

p=eng/"OfficeClient.kt";s=p.read_text()
a="    val modified: StateFlow<Boolean> = _modified.asStateFlow()"
assert s.count(a)==1
s=s.replace(a,a+'''

    /** True only after the Collabora front-end confirms EDITMODE on. */
    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()
''')
a="            P.MODIFIED -> _modified.value = msg.arg1 == 1"
assert s.count(a)==1
s=s.replace(a,a+"\n            P.EDIT_MODE -> _editMode.value = msg.arg1 == 1")
p.write_text(s)

p=root/"app/src/main/java/dev/offlineoffice/app/ui/viewers/OfficeView.kt"
s=p.read_text()
a="    val wordCount by document.wordCount.collectAsState()"
assert s.count(a)==1
s=s.replace(a,a+"\n    val isEditing by document.editMode.collectAsState()")
a='WordRibbon(onCommand = { document.uno(it) }, onFontFamily = { document.setFontFamily(it) }, onFontSize = { document.setFontSize(it) }, onEdit = { document.enterEditMode() })'
assert s.count(a)==1
s=s.replace(a,'WordRibbon(onCommand = { document.uno(it) }, onFontFamily = { document.setFontFamily(it) }, onFontSize = { document.setFontSize(it) }, onEdit = { document.enterEditMode() }, isEditing = isEditing)')
p.write_text(s)

p=Path("NovaWriterCollabora/ui/WordRibbon.kt")
s=p.read_text()
a='    onEdit: () -> Unit,\n)'
assert s.count(a)==1
s=s.replace(a,'    onEdit: () -> Unit,\n    isEditing: Boolean,\n)')
a='Text("Редактировать", color = WordBlue, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)'
assert s.count(a)==1
s=s.replace(a,'Text(if (isEditing) "Редактирование" else "Редактировать", color = WordBlue, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)')
p.write_text(s)
print("Edit mode synchronized end to end")
