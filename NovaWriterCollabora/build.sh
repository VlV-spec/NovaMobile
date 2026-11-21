#!/usr/bin/env bash
# Nova Writer — reproducible prototype based on Libreon GPL-3.0-or-later
# Office engine: LibreOfficeKit + Collabora Online front-end (MPL-2.0)
# This script DOES NOT patch proprietary Microsoft Word software.
set -euo pipefail
ROOT="$(pwd)"
UPSTREAM_SHA=f1f8c95c7b2dfd8a26ccdbd5e17883b6c7cbfc09
UPSTREAM_TAG=v0.9.5-beta.5
APK_NAME=Libreon-0.9.5-beta.5-arm64-v8a.apk

git clone -q --depth 1 --branch "$UPSTREAM_TAG" https://github.com/jainiakhil/Libreon.git source
test "$(git -C source rev-parse HEAD)" = "$UPSTREAM_SHA" || { echo 'Upstream SHA mismatch'; exit 1; }
curl -fLSs --retry 3 -o original.apk "https://github.com/jainiakhil/Libreon/releases/download/$UPSTREAM_TAG/$APK_NAME"
curl -fLSs --retry 3 -o SHA256SUMS "https://github.com/jainiakhil/Libreon/releases/download/$UPSTREAM_TAG/SHA256SUMS-0.9.5-beta.5.txt"
grep "$APK_NAME" SHA256SUMS | sed "s|$APK_NAME|original.apk|" | sha256sum -c -

mkdir unpack
unzip -q original.apk 'lib/arm64-v8a/*.so' 'assets/*' -d unpack
test -d unpack/lib/arm64-v8a
test -d unpack/assets/dist
mkdir -p source/third_party/lok/jniLibs
cp -a unpack/lib/arm64-v8a source/third_party/lok/jniLibs/
cp -a unpack/assets source/third_party/lok/
# The PDF bridge is rebuilt from GPL sources; do not bundle duplicate prebuilt binaries.
rm -f source/third_party/lok/jniLibs/arm64-v8a/liboopdf.so
rm -f source/third_party/lok/jniLibs/arm64-v8a/libpdfium.so
printf "LOK native libraries:\\n"
find source/third_party/lok/jniLibs/arm64-v8a -maxdepth 1 -type f -printf "%f\\n" | sort
echo "Native LibreOfficeKit/Collabora payload copied from authenticated upstream release."
du -sh source/third_party/lok/*
(cd source && bash scripts/fetch-pdfium.sh)

# Change only source-level branding and user-visible text; preserve GPL notices.
python3 - <<'PY'
from pathlib import Path
r=Path('source')
st=r/'app/src/main/res/values/strings.xml'
s=st.read_text()
s=s.replace('>Libreon<','>Nova Writer<').replace('>Work freely<','>Документы без подписки<')
st.write_text(s)

build=r/'app/build.gradle.kts'
s=build.read_text().replace('applicationId = "dev.libreon.app"','applicationId = "com.novawriter.office"').replace('versionCode = 10','versionCode = 20').replace('versionName = "0.9.5-beta.5"','versionName = "0.2.0-engine"')
# Reduce APK to the only ABI we are testing: ARM64 (Samsung S24 Ultra / many Android tablets).
s=s.replace('include("arm64-v8a", "x86_64")','include("arm64-v8a")')
build.write_text(s)

theme=r/'app/src/main/java/dev/offlineoffice/app/ui/Theme.kt'
s=theme.read_text()
s=s.replace('import androidx.compose.ui.graphics.vector.ImageVector','import androidx.compose.ui.graphics.vector.ImageVector\nimport androidx.compose.ui.graphics.Color')
s=s.replace('Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)','Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> darkColorScheme(primary = Color(0xFF87B6FF))')
s=s.replace('Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)','Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> lightColorScheme(primary = Color(0xFF185ABD))')
s=s.replace('dark -> darkColorScheme()','dark -> darkColorScheme(primary = Color(0xFF87B6FF))')
s=s.replace('else -> lightColorScheme()','else -> lightColorScheme(primary = Color(0xFF185ABD))')
theme.write_text(s)

home=r/'app/src/main/java/dev/offlineoffice/app/ui/HomeScreen.kt'
s=home.read_text()
replacements={
'Open a document':'Открыть документ',
'Search open, recent and closed files':'Поиск документов',
'Search on device':'Найти на устройстве',
'Open tabs':'Открытые документы',
'Unsaved changes':'Есть несохранённые изменения',
'\"Recent\"':'\"Недавние\"',
'\"Recently closed\"':'\"Недавно закрытые\"',
'No ads, no accounts, no internet. Everything stays on this device.':'Без аккаунтов и подписок. Файлы остаются на устройстве.',
'Word, Excel, PowerPoint, PDF, Apple Pages, Numbers and Keynote, and text files open here. Everything stays on this device.':'Открывай и редактируй документы Word офлайн.',
}
for a,b in replacements.items():s=s.replace(a,b)
home.write_text(s)
# The copied app retains source notices, original author credits and GPL compliance info.
PY


# Add genuine new-document flow: SAF create -> write a valid OOXML package -> open in LibreOfficeKit.
python3 - <<'PY'
from pathlib import Path
root=Path('source/app/src/main/java/dev/offlineoffice/app')
main=root/'MainActivity.kt'
s=main.read_text()
needle='    private var pendingSaveAs: String? = null'
assert s.count(needle)==1, 'Cannot find activity insertion point'
s=s.replace(needle, '''    private val createBlankWord = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri ->
        if (uri != null) lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        BlankDocx.write(stream)
                    } ?: error("Не удалось создать документ")
                }
            }
            if (result.isSuccess) {
                open(uri, OpenOrigin.PICKER, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            } else {
                Toast.makeText(this@MainActivity, "Ошибка создания DOCX: " + result.exceptionOrNull()?.message, Toast.LENGTH_LONG).show()
            }
        }
    }

''' + needle)
needle='                    onOpen = { openDocuments.launch(DocumentKind.pickerMimeTypes) },'
assert s.count(needle)==1
s=s.replace(needle, needle + '''
                    onNew = { createBlankWord.launch("Новый документ.docx") },''')
main.write_text(s)

app=root/'ui/AppRoot.kt'
s=app.read_text()
needle='    onOpen: () -> Unit,'
assert s.count(needle)>=1
s=s.replace(needle, needle+'\n    onNew: () -> Unit,', 1)
needle='HomeScreen(tabs = tabs, onOpen = onOpen, onSelectTab = ::select, onMessage = onMessage)'
assert s.count(needle)==1
s=s.replace(needle,'HomeScreen(tabs = tabs, onOpen = onOpen, onNew = onNew, onSelectTab = ::select, onMessage = onMessage)')
app.write_text(s)

home=root/'ui/HomeScreen.kt'
s=home.read_text()
needle='fun HomeScreen(tabs: TabManager, onOpen: () -> Unit, onSelectTab: (String) -> Unit, onMessage: (String) -> Unit) {'
assert s.count(needle)==1
s=s.replace(needle,'fun HomeScreen(tabs: TabManager, onOpen: () -> Unit, onNew: () -> Unit, onSelectTab: (String) -> Unit, onMessage: (String) -> Unit) {')
needle='''            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                Button(onClick = onOpen) {'''
assert s.count(needle)==1
s=s.replace(needle,'''            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                Button(onClick = onNew) { Text("Новый документ") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = onOpen) {''')
home.write_text(s)

build=Path('source/app/build.gradle.kts')
s=build.read_text().replace('versionCode = 20','versionCode = 26').replace('versionName = "0.2.0-engine"','versionName = "0.6.0-polished"')
build.write_text(s)
PY

cat > source/app/src/main/java/dev/offlineoffice/app/BlankDocx.kt <<'KOTLIN'
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.offlineoffice.app

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Generates an editable, empty WordprocessingML .docx without networking or templates. */
internal object BlankDocx {
    fun write(output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            fun part(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.trimIndent().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            part("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                </Types>
            """)
            part("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
            """)
            part("word/_rels/document.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
            """)
            part("word/styles.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Liberation Sans" w:hAnsi="Liberation Sans"/></w:rPr></w:rPrDefault></w:docDefaults>
                  <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
                </w:styles>
            """)
            part("word/document.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p/>
                    <w:sectPr>
                      <w:pgSz w:w="11906" w:h="16838"/>
                      <w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/>
                    </w:sectPr>
                  </w:body>
                </w:document>
            """)
        }
    }
}
KOTLIN


# Replace old home screen with a native Compose home using working actions.
cp NovaWriterCollabora/ui/HomeScreen.kt source/app/src/main/java/dev/offlineoffice/app/ui/HomeScreen.kt

# Style native app chrome to match Word Mobile's blue-and-white visual hierarchy.
python3 - <<'PY'
from pathlib import Path
import re
p=Path("source/app/src/main/java/dev/offlineoffice/app/ui/AppRoot.kt")
s=p.read_text()
s=s.replace('import androidx.compose.material3.TopAppBar\n',
            'import androidx.compose.material3.TopAppBar\nimport androidx.compose.material3.TopAppBarDefaults\n')
s=s.replace('import androidx.compose.ui.Alignment\n',
            'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.graphics.Color\n')
needle='                TopAppBar(\n'
assert needle in s
s=s.replace(needle,'''                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF185ABD),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
''',1)
s=s.replace('TabCountChip(count = tabList.size, onClick = { showSwitcher = true })',
            'if (tabList.isNotEmpty()) TabCountChip(count = tabList.size, onClick = { showSwitcher = true })')
old='''@Composable
private fun HomeTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.libreon_logo), null, Modifier.size(36.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
'''
new='''@Composable
private fun HomeTitle() {
    Column(Modifier.padding(start = 8.dp)) {
        Text("Nova Writer", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text("Документы", style = MaterialTheme.typography.labelSmall, color = Color(0xFFDDE8FF))
    }
}
'''
assert old in s,"HomeTitle original not found"
s=s.replace(old,new)
# Add an actionable New document menu command, available even when a document is open.
s=s.replace('''                            onOpen = onOpen,
                            onSaveAs''','''                            onOpen = onOpen,
                            onNew = onNew,
                            onSaveAs''')
s=s.replace('''private fun OverflowMenu(
    hasActive: Boolean,
    onOpen: () -> Unit,''','''private fun OverflowMenu(
    hasActive: Boolean,
    onOpen: () -> Unit,
    onNew: () -> Unit,''')
assert '    onNew: () -> Unit,' in s
menu='''        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Новый документ") },
                leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                onClick = { expanded = false; onNew() },
            )
'''
needle='''        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
'''
assert needle in s
s=s.replace(needle,menu,1)
s=s.replace('text = { Text("Open…") }','text = { Text("Открыть файл…") }')
s=s.replace('text = { Text("Save as…") }','text = { Text("Сохранить как…") }')
s=s.replace('text = { Text("Share") }','text = { Text("Поделиться") }')
s=s.replace('text = { Text("Export as PDF…") }','text = { Text("Экспортировать в PDF…") }')
s=s.replace('text = { Text("Full screen") }','text = { Text("На весь экран") }')
s=s.replace('text = { Text("Word count") }','text = { Text("Статистика текста") }')
p.write_text(s)
PY

# Add real editor formatting commands and Word-like tabs.
python3 NovaWriterCollabora/patch-ribbon.py
python3 NovaWriterCollabora/patch-fonts.py
python3 NovaWriterCollabora/patch-editmode.py
python3 NovaWriterCollabora/patch-status.py
cp NovaWriterCollabora/ui/WordRibbon.kt source/app/src/main/java/dev/offlineoffice/app/ui/viewers/WordRibbon.kt

cat > source/NOVA_WRITER_CHANGES.md <<'EOF'
Nova Writer 0.6.0-polished
Upstream: Libreon v0.9.5-beta.5 (https://github.com/jainiakhil/Libreon), GPL-3.0-or-later.
Office engine: LibreOfficeKit + Collabora Online, MPL-2.0 and other respective licenses.
Changes: product name, Android applicationId, primary UI colour, selected Russian UI labels,
ARM64 debug packaging, new-document UI, minimal OOXML creator, polished native Word-like ribbon, editor mode guard and font family / point size selectors. Original sources/attributions retained. 
This is a technical preview, NOT Microsoft Word and NOT affiliated with Microsoft/Collabora/Libreon.
Build recipe: see https://github.com/VlV-spec/NovaMobile/tree/sandbox/nova-writer-collabora/NovaWriterCollabora
EOF

cd source
chmod +x gradlew
./gradlew :app:assembleDebug --no-daemon --stacktrace
find app/build/outputs/apk -type f -name '*.apk' -ls
