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

cat > source/NOVA_WRITER_CHANGES.md <<'EOF'
Nova Writer 0.2.0-engine
Upstream: Libreon v0.9.5-beta.5 (https://github.com/jainiakhil/Libreon), GPL-3.0-or-later.
Office engine: LibreOfficeKit + Collabora Online, MPL-2.0 and other respective licenses.
Changes: product name, Android applicationId, primary UI colour, selected Russian UI labels,
ARM64 debug packaging. Original sources/attributions retained. 
This is a technical preview, NOT Microsoft Word and NOT affiliated with Microsoft/Collabora/Libreon.
Build recipe: see https://github.com/VlV-spec/NovaMobile/tree/sandbox/nova-writer-collabora/NovaWriterCollabora
EOF

cd source
chmod +x gradlew
./gradlew :app:assembleDebug --no-daemon --stacktrace
find app/build/outputs/apk -type f -name '*.apk' -ls
