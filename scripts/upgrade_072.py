#!/usr/bin/env python3
"""Nova Mobile 0.7.2: remove the duplicate battery and clock date labels.

Keep the large tappable clock (alarms), the battery widget, and the separate
calendar widget (calendar app). Do not modify Dock, favorites or app loading.
"""
from pathlib import Path

root = Path('app')
activity = root / 'src/main/java/com/novahome/mobile/MainActivity.kt'
s = activity.read_text(encoding='utf-8')

def replace_exact(before: str, after: str) -> None:
    global s
    actual = s.count(before)
    if actual != 1:
        raise RuntimeError(f'Expected exactly one anchor ({actual} found): {before[:100]!r}')
    s = s.replace(before, after, 1)

# The same battery percentage appears both at the top-right AND in the widget.
# Remove only the redundant header badge. The battery widget stays unchanged.
replace_exact('''            Box(Modifier.clip(RoundedCornerShape(13.dp)).background(Panel.copy(alpha = .88f)).padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text("⚡ $battery", color = WhiteMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
''', '')

# Keep the standalone date card clickable. Only the line below big clock goes.
replace_exact('''                Text(date.replaceFirstChar { it.uppercase() }, color = WhiteMuted, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp))
                        .clickable(onClick = onOpenCalendar)
                        .padding(vertical = 5.dp))
''', '')
replace_exact('''    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date(now)) }
''', '')
replace_exact('''Nova Mobile 0.7.1 · Clock & Calendar Preview''', '''Nova Mobile 0.7.2 · Clean Widgets Preview''')

assert s.count('Text("⚡ $battery"') == 0
assert 'Text(battery, color = Color.White, fontSize = 21.sp,' in s
assert 'if (showBatteryWidget)' in s
assert 'if (showCalendarWidget)' in s
assert 'onOpenAlarms' in s and '.clickable(onClick = onOpenAlarms)' in s
assert '.clickable(onClick = onOpenCalendar)' in s
activity.write_text(s, encoding='utf-8')

build = root / 'build.gradle.kts'
b = build.read_text(encoding='utf-8')
assert 'applicationId = "com.novahome.mobile.clickwidgets071"' in b
assert 'versionCode = 12' in b
assert 'versionName = "0.7.1-preview"' in b
b = b.replace('applicationId = "com.novahome.mobile.clickwidgets071"',
              'applicationId = "com.novahome.mobile.cleanwidgets072"')
b = b.replace('versionCode = 12', 'versionCode = 13')
b = b.replace('versionName = "0.7.1-preview"', 'versionName = "0.7.2-preview"')
build.write_text(b, encoding='utf-8')

manifest = root / 'src/main/AndroidManifest.xml'
m = manifest.read_text(encoding='utf-8')
assert 'android:label="Nova Mobile 0.7.1"' in m
manifest.write_text(m.replace('android:label="Nova Mobile 0.7.1"',
                              'android:label="Nova Mobile 0.7.2"'), encoding='utf-8')
print('Nova Mobile 0.7.2: duplicate battery and date removed')
