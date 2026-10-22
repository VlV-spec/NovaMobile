#!/usr/bin/env python3
"""Nova Mobile 0.6.2: fix Dock replacement with explicit per-slot picker."""
from pathlib import Path
p = Path('app/src/main/java/com/novahome/mobile/MainActivity.kt')
s = p.read_text('utf-8')

def patch(a,b):
    global s
    hits=s.count(a)
    if hits!=1: raise RuntimeError(f'Expected 1 match, found {hits}: {a[:75]!r}')
    s=s.replace(a,b,1)

patch('import androidx.compose.foundation.lazy.grid.GridCells\n',
      'import androidx.compose.foundation.lazy.LazyColumn\n'
      'import androidx.compose.foundation.lazy.items as listItems\n'
      'import androidx.compose.foundation.lazy.grid.GridCells\n')
patch('''            var editing by remember { mutableStateOf(false) }
''','''            var editing by remember { mutableStateOf(false) }
            var dockEditSlot by remember { mutableIntStateOf(-1) }
''')
patch('''            fun toggleDock(pkg: String) {
''','''            // This editor chooses a precise Dock position, without toggling favorites.
            // An existing pinned app swaps places with the destination to avoid duplicates.
            fun replaceDockSlot(slot: Int, pkg: String) {
                if (slot !in 0..3 || apps.none { it.packageName == pkg }) return
                if (pkg !in favorites) favorites.add(pkg)
                val updated = dockPackages.toMutableList()
                val existing = updated.indexOf(pkg)
                if (existing == slot) return
                if (slot < updated.size) {
                    if (existing >= 0) {
                        val displaced = updated[slot]
                        updated[slot] = pkg
                        updated[existing] = displaced
                    } else updated[slot] = pkg
                } else if (existing >= 0) {
                    updated.removeAt(existing)
                    updated.add(pkg)
                } else updated.add(pkg)
                dockPackages.clear()
                dockPackages.addAll(updated.distinct().take(4))
                persistLayout()
            }
            fun clearDockSlot(slot: Int) {
                if (slot !in dockPackages.indices) return
                val pkg = dockPackages.removeAt(slot)
                if (pkg !in favorites) favorites.add(pkg)
                persistLayout()
            }
            fun toggleDock(pkg: String) {
''')
patch('''                            onLaunch = { launch(it) }, onEditFavorites = { openApps(true) },
                            onSystemSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) }
''','''                            onLaunch = { launch(it) }, onEditFavorites = { openApps(true) },
                            onDockSlot = { dockEditSlot = it },
                            onSystemSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) }
''')
patch('''                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(''','''                    }
                    if (dockEditSlot >= 0) {
                        DockSlotDialog(
                            slot = dockEditSlot, apps = apps, accent = accent,
                            onSelect = { pkg ->
                                replaceDockSlot(dockEditSlot, pkg)
                                dockEditSlot = -1
                            },
                            onRemove = {
                                clearDockSlot(dockEditSlot)
                                dockEditSlot = -1
                            },
                            onDismiss = { dockEditSlot = -1 }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(''')
patch('''    onEditFavorites: () -> Unit,
    onSystemSettings: () -> Unit
) {''','''    onEditFavorites: () -> Unit,
    onDockSlot: (Int) -> Unit,
    onSystemSettings: () -> Unit
) {''')
patch('''                        selectedApp == null -> "Держи и перетаскивай. Для Dock: «Добавить» → + Dock"''','''                        selectedApp == null -> "Dock: нажми на значок внизу, чтобы заменить или убрать его"''')
patch('''            dock.forEach { app ->
                Box(
                    Modifier.weight(1f).then(iconDragModifier(app.packageName))''','''            dock.forEachIndexed { index, app ->
                Box(
                    Modifier.weight(1f).then(iconDragModifier(app.packageName))''')
patch('''                            if (arranging) selectForMove(app.packageName) else onLaunch(app)
''','''                            if (arranging) onDockSlot(index) else onLaunch(app)
''')
patch('''                        .clickable { onEditFavorites() }, contentAlignment = Alignment.Center) {
                        Text("+", color = accent, fontSize = 25.sp)''','''                        .clickable { onDockSlot(dock.size) }, contentAlignment = Alignment.Center) {
                        Text("+", color = accent, fontSize = 25.sp)''')
patch('''Text("Nova Mobile 0.6.1 · Dock Fix Preview"''', '''Text("Nova Mobile 0.6.2 · Dock Editor Preview"''')
s += '''

// Direct slot replacement is a reliable alternative to cross-grid drag targets.
@Composable
private fun DockSlotDialog(
    slot: Int, apps: List<InstalledApp>, accent: Color,
    onSelect: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit
) {
    var query by remember(slot) { mutableStateOf("") }
    val matches = remember(query, apps) {
        apps.filter { it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Dock · место ${slot + 1}", color = Color.White, fontSize = 19.sp)
        },
        text = {
            Column {
                Text("Выбери приложение — оно заменит значок именно в этом месте.",
                    color = WhiteMuted, fontSize = 12.sp)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Поиск приложения") })
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listItems(matches, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(app.packageName) }
                                .padding(horizontal = 7.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(app, 38)
                            Spacer(Modifier.width(10.dp))
                            Text(app.label, color = Color.White, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Text("+", color = accent, fontSize = 21.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", color = accent) }
        },
        dismissButton = {
            TextButton(onClick = onRemove) { Text("Убрать из Dock", color = Rose, fontSize = 12.sp) }
        },
        containerColor = Panel
    )
}
'''
p.write_text(s, 'utf-8')

build = Path('app/build.gradle.kts')
b = build.read_text('utf-8')
assert 'applicationId = "com.novahome.mobile.dockfixpreview"' in b
b = b.replace('applicationId = "com.novahome.mobile.dockfixpreview"',
              'applicationId = "com.novahome.mobile.dockeditorpreview"')
b = b.replace('versionCode = 8', 'versionCode = 9')
b = b.replace('versionName = "0.6.1-preview"', 'versionName = "0.6.2-preview"')
build.write_text(b, 'utf-8')
manifest = Path('app/src/main/AndroidManifest.xml')
m = manifest.read_text('utf-8')
assert 'android:label="Nova Mobile 0.6.1"' in m
manifest.write_text(m.replace('android:label="Nova Mobile 0.6.1"',
                              'android:label="Nova Mobile 0.6.2"'), 'utf-8')
print('Nova Mobile 0.6.2 slot editor applied')
