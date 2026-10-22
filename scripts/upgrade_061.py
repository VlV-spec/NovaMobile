#!/usr/bin/env python3
"""Nova Mobile 0.6.1: independent, saved Dock and explicit app pin/unpin buttons."""
from pathlib import Path

path = Path("app/src/main/java/com/novahome/mobile/MainActivity.kt")
code = path.read_text("utf-8")

def change(old, new, expected=1):
    global code
    count = code.count(old)
    if count != expected:
        raise RuntimeError(f"Expected {expected} matches, got {count}: {old[:100]!r}")
    code = code.replace(old, new, expected)

change('''            fun toggleFavorite(pkg: String) {
                if (pkg in favorites) favorites.remove(pkg) else favorites.add(pkg)
                prefs.edit().putString("favorites", favorites.joinToString("|")).apply()
            }
''', '''            val dockPackages = remember {
                mutableStateListOf<String>().apply {
                    val saved = prefs.getString("dock_apps", null)
                    val initial = if (saved == null) favorites.take(4)
                        else saved.split("|").filter { it.isNotBlank() }
                    addAll(initial.distinct().filter { pkg -> apps.any { it.packageName == pkg } }.take(4))
                }
            }
            fun persistLayout() {
                prefs.edit()
                    .putString("favorites", favorites.joinToString("|"))
                    .putString("dock_apps", dockPackages.joinToString("|"))
                    .apply()
            }
            fun toggleFavorite(pkg: String) {
                if (pkg in favorites) {
                    favorites.remove(pkg)
                    dockPackages.remove(pkg)
                } else favorites.add(pkg)
                persistLayout()
            }
            fun toggleDock(pkg: String) {
                if (pkg in dockPackages) {
                    dockPackages.remove(pkg)
                    Toast.makeText(this, "Убрано из Dock", Toast.LENGTH_SHORT).show()
                } else {
                    if (pkg !in favorites) favorites.add(pkg)
                    if (dockPackages.size >= 4) dockPackages.removeAt(3)
                    dockPackages.add(pkg)
                    Toast.makeText(this, "Приложение закреплено в Dock", Toast.LENGTH_SHORT).show()
                }
                persistLayout()
            }
''')
change('''            fun reorderFavorites(from: String, to: String) {
                val source = favorites.indexOf(from)
                val target = favorites.indexOf(to)
                if (source >= 0 && target >= 0 && source != target) {
                    val old = favorites[source]
                    favorites[source] = favorites[target]
                    favorites[target] = old
                    prefs.edit().putString("favorites", favorites.joinToString("|")).apply()
                }
            }
            var clockEnabled by remember { mutableStateOf(prefs.getBoolean("clock_widget", true)) }
            val homeApps = favorites.mapNotNull { pkg -> apps.find { it.packageName == pkg } }
            val dock = homeApps.take(4)
            val shortcuts = homeApps.drop(4).take(12)
''', '''            fun reorderFavorites(from: String, to: String) {
                if (from == to || from !in favorites || to !in favorites) return
                val fromDock = dockPackages.indexOf(from)
                val toDock = dockPackages.indexOf(to)
                when {
                    fromDock >= 0 && toDock >= 0 -> {
                        dockPackages[fromDock] = to
                        dockPackages[toDock] = from
                    }
                    fromDock >= 0 -> dockPackages[fromDock] = to
                    toDock >= 0 -> dockPackages[toDock] = from
                    else -> {
                        val source = favorites.indexOf(from)
                        val target = favorites.indexOf(to)
                        favorites[source] = to
                        favorites[target] = from
                    }
                }
                persistLayout()
            }
            var clockEnabled by remember { mutableStateOf(prefs.getBoolean("clock_widget", true)) }
            val homeApps = favorites.mapNotNull { pkg -> apps.find { it.packageName == pkg } }
            val dock = dockPackages.mapNotNull { pkg -> apps.find { it.packageName == pkg } }
            val dockSet = dockPackages.toSet()
            val shortcuts = homeApps.filterNot { it.packageName in dockSet }.take(12)
''')
change('''                            onLaunch = { launch(it) }, onFavorite = { toggleFavorite(it) }
''','''                            onLaunch = { launch(it) }, onFavorite = { toggleFavorite(it) },
                            dockPackages = dockPackages, onDock = { toggleDock(it) }
''')
change('''    onLaunch: (InstalledApp) -> Unit,
    onFavorite: (String) -> Unit
) {
''','''    onLaunch: (InstalledApp) -> Unit,
    onFavorite: (String) -> Unit,
    dockPackages: List<String>,
    onDock: (String) -> Unit
) {
''')
change('''            Text("Нажми на приложение, чтобы добавить или убрать его из избранного",
                color = WhiteMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
''','''            Text("Нажми на плитку для избранного, а «+ Dock» — чтобы закрепить внизу. До 4 значков; новый заменяет последний.",
                color = WhiteMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
''')
change('''                    AppShortcut(app, accent, editing, app.packageName in favoriteSet,
                        onLaunch = { onLaunch(app) }, onToggle = { onFavorite(app.packageName) })
''','''                    AppShortcut(app, accent, editing, app.packageName in favoriteSet,
                        onLaunch = { onLaunch(app) }, onToggle = { onFavorite(app.packageName) },
                        dockPinned = app.packageName in dockPackages,
                        onDock = { onDock(app.packageName) })
''')
change('''    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().height(91.dp).clip(RoundedCornerShape(15.dp))
''','''    selected: Boolean = false,
    modifier: Modifier = Modifier,
    dockPinned: Boolean = false,
    onDock: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().height(if (editing && onDock != null) 117.dp else 91.dp)
            .clip(RoundedCornerShape(15.dp))
''')
change('''        Text(app.label, color = Color.White.copy(alpha = .91f), fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp))
    }
}
''','''        Text(app.label, color = Color.White.copy(alpha = .91f), fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp))
        if (editing && onDock != null) {
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (dockPinned) accent.copy(alpha = .28f) else Color.White.copy(alpha = .10f))
                    .clickable { onDock() }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (dockPinned) "✓ Dock" else "+ Dock",
                    color = if (dockPinned) accent else Color.White,
                    fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
''')
change('''            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.size(47.dp).clip(RoundedCornerShape(15.dp))
                    .background(accent.copy(alpha = .17f)).clickable { onAppDrawer() }, contentAlignment = Alignment.Center) {
                    Text("▦", color = accent, fontSize = 27.sp)
                }
            }
''','''            repeat((4 - dock.size).coerceAtLeast(0)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(47.dp).clip(RoundedCornerShape(15.dp))
                        .background(accent.copy(alpha = .10f))
                        .border(1.dp, accent.copy(alpha = .35f), RoundedCornerShape(15.dp))
                        .clickable { onEditFavorites() }, contentAlignment = Alignment.Center) {
                        Text("+", color = accent, fontSize = 25.sp)
                    }
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.size(47.dp).clip(RoundedCornerShape(15.dp))
                    .background(accent.copy(alpha = .17f)).clickable { onAppDrawer() }, contentAlignment = Alignment.Center) {
                    Text("▦", color = accent, fontSize = 27.sp)
                }
            }
''')
change('''                        selectedApp == null -> "Удерживай значок и перенеси на другое место"
''', '''                        selectedApp == null -> "Держи и перетаскивай. Для Dock: «Добавить» → + Dock"
''')
change('''Text("Nova Mobile 0.6 · Drag & Drop Preview"''','''Text("Nova Mobile 0.6.1 · Dock Fix Preview"''')
change('''Два касания также работают.''','''Можно также закрепить приложение через «Добавить» → «+ Dock». Два касания для обмена тоже работают.''')

path.write_text(code, "utf-8")
build = Path("app/build.gradle.kts")
gradle = build.read_text("utf-8")
assert 'applicationId = "com.novahome.mobile.dragpreview"' in gradle
gradle = gradle.replace('applicationId = "com.novahome.mobile.dragpreview"', 'applicationId = "com.novahome.mobile.dockfixpreview"')
gradle = gradle.replace('versionCode = 7', 'versionCode = 8')
gradle = gradle.replace('versionName = "0.6.0-preview"', 'versionName = "0.6.1-preview"')
build.write_text(gradle, "utf-8")
manifest = Path("app/src/main/AndroidManifest.xml")
m = manifest.read_text("utf-8")
assert 'android:label="Nova Mobile 0.6"' in m
manifest.write_text(m.replace('android:label="Nova Mobile 0.6"', 'android:label="Nova Mobile 0.6.1"'), "utf-8")
print("Nova Mobile 0.6.1 Dock fix applied")
