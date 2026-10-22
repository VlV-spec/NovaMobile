#!/usr/bin/env python3
"""Apply Nova Mobile 0.5 wallpaper and drawer gesture updates to 0.4 sources."""
from pathlib import Path

source = Path("app/src/main/java/com/novahome/mobile/MainActivity.kt")
content = source.read_text(encoding="utf-8")

def change(old: str, new: str, *, count: int = 1) -> None:
    global content
    n = content.count(old)
    if n != count:
        raise SystemExit(f"Upgrade aborted: expected {count} matches, got {n} for {old[:80]!r}")
    content = content.replace(old, new, count)

change('import android.graphics.Bitmap\n',
       'import android.graphics.Bitmap\nimport android.graphics.BitmapFactory\nimport android.net.Uri\n')
change('import androidx.activity.compose.setContent\n',
       'import androidx.activity.compose.setContent\nimport androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\n')
change('import androidx.compose.foundation.Image\n',
       'import androidx.compose.foundation.Image\nimport androidx.compose.foundation.gestures.detectVerticalDragGestures\n')
change('import androidx.compose.ui.Alignment\n',
       'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.layout.ContentScale\n')

change('            val accent = themes[palette]\n', '''            val accent = themes[palette]
            var wallpaperUri by remember { mutableStateOf(prefs.getString("wallpaper_uri", "").orEmpty()) }
            var wallpaperShade by remember { mutableFloatStateOf(prefs.getFloat("wallpaper_shade", .65f).coerceIn(.25f, .9f)) }
            val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    wallpaperUri = uri.toString()
                    prefs.edit().putString("wallpaper_uri", wallpaperUri).apply()
                }
            }
''')
change('''                ) {
                    when {
                        settingsOpen -> NovaSettings(''', '''                ) {
                    if (!drawerOpen && !settingsOpen && wallpaperUri.isNotBlank()) {
                        WallpaperLayer(wallpaperUri, wallpaperShade)
                    }
                    when {
                        settingsOpen -> NovaSettings(''')
change('''                            showClock = clockEnabled,
                            onShowClock =''', '''                            showClock = clockEnabled,
                            wallpaperChosen = wallpaperUri.isNotBlank(), wallpaperShade = wallpaperShade,
                            onChooseWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                            onClearWallpaper = {
                                wallpaperUri = ""
                                prefs.edit().remove("wallpaper_uri").apply()
                            },
                            onWallpaperShade = {
                                wallpaperShade = it
                                prefs.edit().putFloat("wallpaper_shade", it).apply()
                            },
                            onShowClock =''')

# Gesture target stays outside the lazy grid to avoid scroll gesture conflicts.
change('''        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp))''', '''        var gestureDrag by remember { mutableFloatStateOf(0f) }
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
                Text("↑ Все приложения", color = WhiteMuted, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp))''')
change('''    showClock: Boolean, onShowClock: (Boolean) -> Unit,
    onColumns: (Int) -> Unit, onPalette: (Int) -> Unit,''', '''    showClock: Boolean, onShowClock: (Boolean) -> Unit,
    wallpaperChosen: Boolean, wallpaperShade: Float,
    onChooseWallpaper: () -> Unit, onClearWallpaper: () -> Unit,
    onWallpaperShade: (Float) -> Unit,
    onColumns: (Int) -> Unit, onPalette: (Int) -> Unit,''')
change('''        Spacer(Modifier.height(29.dp))
        Text("ЦВЕТОВАЯ ТЕМА",''', '''        Spacer(Modifier.height(20.dp))
        Text("ОБОИ РАБОЧЕГО СТОЛА", color = accent, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(onClick = onChooseWallpaper, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)) {
                Text(if (wallpaperChosen) "Сменить фото" else "Выбрать фото", fontSize = 12.sp)
            }
            if (wallpaperChosen) {
                OutlinedButton(onClick = onClearWallpaper, shape = RoundedCornerShape(14.dp)) {
                    Text("Убрать", fontSize = 12.sp)
                }
            }
        }
        if (wallpaperChosen) {
            Text("Затемнение фона: ${(wallpaperShade * 100).toInt()}%",
                color = WhiteMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Slider(value = wallpaperShade, onValueChange = onWallpaperShade,
                valueRange = 0.25f..0.9f)
        }
        Spacer(Modifier.height(22.dp))
        Text("ЦВЕТОВАЯ ТЕМА",''')
change('''        Text("Nova Mobile 0.4 · Home Editor Preview",''', '''        Text("Nova Mobile 0.5 · Personalize Preview",''')
change('''        Text("Рабочий стол: «Расставить» → первая иконка → вторая. Их места меняются и сохраняются. Зажатие и перетаскивание пока не используются.",''', '''        Text("Обои выбираются через системный проводник. Над Dock есть полоска: проведи по ней вверх или нажми, чтобы открыть приложения. Расстановка иконок пока двумя касаниями.",''')

content += '''
// Decode gallery photos on a background dispatcher, with downsampling.
@Composable
private fun WallpaperLayer(uriString: String, shade: Float) {
    val context = LocalContext.current
    val wallpaper by produceState<Bitmap?>(initialValue = null, key1 = uriString) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 2560) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize()) {
        wallpaper?.let {
            val image = remember(it) { it.asImageBitmap() }
            Image(bitmap = image, contentDescription = "Обои рабочего стола",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = shade)))
    }
}
'''
source.write_text(content, encoding="utf-8")

build = Path("app/build.gradle.kts")
b = build.read_text(encoding="utf-8")
assert 'applicationId = "com.novahome.mobile.editorpreview"' in b
b = b.replace('applicationId = "com.novahome.mobile.editorpreview"',
              'applicationId = "com.novahome.mobile.personalizepreview"')
b = b.replace('versionCode = 5', 'versionCode = 6')
b = b.replace('versionName = "0.4.0-preview"', 'versionName = "0.5.0-preview"')
build.write_text(b, encoding="utf-8")

manifest = Path("app/src/main/AndroidManifest.xml")
m = manifest.read_text(encoding="utf-8")
assert 'android:label="Nova Mobile 0.4"' in m
manifest.write_text(m.replace('android:label="Nova Mobile 0.4"',
                              'android:label="Nova Mobile 0.5"'), encoding="utf-8")
print("Nova Mobile 0.5 upgrade applied successfully")
