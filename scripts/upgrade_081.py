#!/usr/bin/env python3
"""Nova Mobile 0.8.1: fix home viewport and duplicate app drawer buttons."""
from pathlib import Path
p=Path("app/src/main/java/com/novahome/mobile/MainActivity.kt")
s=p.read_text("utf-8")
def replace(old,new):
    global s
    n=s.count(old)
    if n!=1: raise RuntimeError(f"Expected one match; found {n}: {old[:90]!r}")
    s=s.replace(old,new,1)
replace('''        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            QuickAction("▦", "Все приложения", accent, Modifier.weight(1f), onAppDrawer)
            QuickAction("⚙", "Система", accent, Modifier.weight(1f), onSystemSettings)
        }
        Spacer(Modifier.height(21.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("ИЗБРАННОЕ", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
            Text("DYNAMIC_FAVORITES_COUNT", color = WhiteMuted, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onEditFavorites) { Text("Добавить", color = WhiteMuted, fontSize = 12.sp) }
        }'''.replace('DYNAMIC_FAVORITES_COUNT', chr(36)+'{dock.count { it != null } + shortcuts.size}'),'''        // Dock retains the only visible All Apps button. Put system settings
        // in the favorites heading to release one full icon row.
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("ИЗБРАННОЕ", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
            Text("DYNAMIC_FAVORITES_COUNT", color = WhiteMuted, fontSize = 12.sp)
            Spacer(Modifier.width(5.dp))
            TextButton(onClick = onSystemSettings,
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) {
                Text("⚙", color = WhiteMuted, fontSize = 19.sp)
            }
            TextButton(onClick = onEditFavorites,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text("Добавить", color = WhiteMuted, fontSize = 12.sp)
            }
        }'''.replace('DYNAMIC_FAVORITES_COUNT', chr(36)+'{dock.count { it != null } + shortcuts.size}'))
replace('''        var gestureDrag by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.fillMaxWidth().height(29.dp)
                .pointerInput(onAppDrawer) {
                    detectVerticalDragGestures(
                        onDragStart = { gestureDrag = 0f },
                        onDragEnd = {
                            if (gestureDrag < -28f) onAppDrawer()
                            gestureDrag = 0f
                        },
                        onVerticalDrag = { change, amount ->
                            gestureDrag += amount
                            change.consume()
                        }
                    )
                }
                .clickable(onClick = onAppDrawer),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(43.dp).height(4.dp).clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = .65f)))
                Spacer(Modifier.height(4.dp))
                Text("↑ Все приложения", color = WhiteMuted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(5.dp))''','''        // Retain the swipe-up gesture, but no duplicate visible entry point.
        var gestureDrag by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.fillMaxWidth().height(14.dp)
                .pointerInput(onAppDrawer) {
                    detectVerticalDragGestures(
                        onDragStart = { gestureDrag = 0f },
                        onDragEnd = {
                            if (gestureDrag < -28f) onAppDrawer()
                            gestureDrag = 0f
                        },
                        onVerticalDrag = { change, amount ->
                            gestureDrag += amount
                            change.consume()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.width(38.dp).height(3.dp).clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = .55f)))
        }
        Spacer(Modifier.height(4.dp))''')
replace('Nova Mobile 0.8 · Ultra Visual Preview','Nova Mobile 0.8.1 · Layout Fix Preview')
assert 'QuickAction("▦"' not in s and 'Text("↑ Все приложения"' not in s
assert '.clickable { onAppDrawer() }' in s
p.write_text(s,encoding="utf-8")
build=Path("app/build.gradle.kts")
b=build.read_text(encoding="utf-8")
assert 'applicationId = "com.novahome.mobile.ultra080preview"' in b
assert 'versionCode = 14' in b
b=b.replace('applicationId = "com.novahome.mobile.ultra080preview"','applicationId = "com.novahome.mobile.layoutfix081"')
b=b.replace('versionCode = 14','versionCode = 15')
b=b.replace('versionName = "0.8.0-preview"','versionName = "0.8.1-preview"')
build.write_text(b,encoding="utf-8")
manifest=Path("app/src/main/AndroidManifest.xml")
m=manifest.read_text(encoding="utf-8")
assert 'android:label="Nova Mobile 0.8"' in m
manifest.write_text(m.replace('android:label="Nova Mobile 0.8"','android:label="Nova Mobile 0.8.1"'),encoding="utf-8")
print("Nova Mobile 0.8.1 layout fix applied")
