#!/usr/bin/env python3
"""Nova Mobile 0.6: long-press and drag reordering for home icons and Dock."""
from pathlib import Path

file = Path("app/src/main/java/com/novahome/mobile/MainActivity.kt")
s = file.read_text("utf-8")

def patch(old: str, new: str, occurrences: int = 1):
    global s
    count = s.count(old)
    if count != occurrences:
        raise RuntimeError(f"Expected {occurrences} occurrences, got {count}: {old[:90]!r}")
    s = s.replace(old, new, occurrences)

patch('import androidx.compose.foundation.gestures.detectVerticalDragGestures\n',
      'import androidx.compose.foundation.gestures.detectVerticalDragGestures\n'
      'import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress\n')
patch('import androidx.compose.ui.Alignment\n',
      'import androidx.compose.ui.Alignment\n'
      'import androidx.compose.ui.geometry.Offset\n'
      'import androidx.compose.ui.geometry.Rect\n'
      'import androidx.compose.ui.layout.boundsInRoot\n'
      'import androidx.compose.ui.layout.onGloballyPositioned\n'
      'import androidx.compose.ui.graphics.graphicsLayer\n'
      'import androidx.compose.ui.zIndex\n')

patch('''    var selectedApp by remember { mutableStateOf<String?>(null) }
    fun selectForMove''', '''    var selectedApp by remember { mutableStateOf<String?>(null) }
    var draggingApp by remember { mutableStateOf<String?>(null) }
    var fingerPosition by remember { mutableStateOf(Offset.Zero) }
    var dragDistance by remember { mutableStateOf(Offset.Zero) }
    val iconBounds = remember { mutableMapOf<String, Rect>() }
    val hoveredApp = draggingApp?.let { active ->
        iconBounds.entries.firstOrNull { (pkg, rect) -> pkg != active && rect.contains(fingerPosition) }?.key
    }

    fun stopDrag(allowDrop: Boolean) {
        val from = draggingApp
        val to = if (from != null) iconBounds.entries.firstOrNull { (pkg, rect) ->
            pkg != from && rect.contains(fingerPosition)
        }?.key else null
        if (allowDrop && from != null && to != null) {
            onReorder(from, to)
            selectedApp = null
        }
        draggingApp = null
        dragDistance = Offset.Zero
        fingerPosition = Offset.Zero
    }

    fun iconDragModifier(pkg: String): Modifier {
        val dragGesture = if (arranging) {
            Modifier.pointerInput(pkg, arranging) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localPosition ->
                        draggingApp = pkg
                        dragDistance = Offset.Zero
                        fingerPosition = (iconBounds[pkg]?.topLeft ?: Offset.Zero) + localPosition
                        selectedApp = null
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount
                        fingerPosition += amount
                    },
                    onDragEnd = { stopDrag(true) },
                    onDragCancel = { stopDrag(false) }
                )
            }
        } else Modifier
        return Modifier
            .onGloballyPositioned { coordinates -> iconBounds[pkg] = coordinates.boundsInRoot() }
            .zIndex(if (draggingApp == pkg) 10f else 0f)
            .graphicsLayer {
                if (draggingApp == pkg) {
                    translationX = dragDistance.x
                    translationY = dragDistance.y
                    scaleX = 1.12f
                    scaleY = 1.12f
                    alpha = .84f
                }
            }
            .then(dragGesture)
    }

    fun selectForMove''')

patch('''                .clickable { arranging = !arranging; selectedApp = null }''',
      '''                .clickable {
                    if (arranging) stopDrag(false)
                    arranging = !arranging
                    selectedApp = null
                    iconBounds.clear()
                }''')

patch('''                    if (selectedApp == null) "Выбери значок, который хочешь переместить"
                    else "Теперь нажми вторую иконку — поменяю их местами",''',
      '''                    when {
                        draggingApp != null -> if (hoveredApp != null)
                            "Отпусти — значки поменяются местами"
                        else "Перетащи значок на другое приложение"
                        selectedApp == null -> "Удерживай значок и перенеси на другое место"
                        else -> "Коснись другой иконки для обмена или зажми и перетащи"
                    },''')

patch('''                        selected = arranging && selectedApp == app.packageName
''',
      '''                        selected = arranging && (selectedApp == app.packageName || hoveredApp == app.packageName),
                        modifier = iconDragModifier(app.packageName)
''')

patch('''                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
''',
      '''                    Modifier.weight(1f).then(iconDragModifier(app.packageName))
                        .clip(RoundedCornerShape(18.dp))
''')

patch('''                            if (arranging && selectedApp == app.packageName) 2.dp else 0.dp,
''',
      '''                            if (arranging && (selectedApp == app.packageName || hoveredApp == app.packageName)) 2.dp else 0.dp,
''')

patch('''    selected: Boolean = false
) {
    Column(
        Modifier.fillMaxWidth().height(91.dp)''',
      '''    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().height(91.dp)''')

patch('''Text("Nova Mobile 0.5 · Personalize Preview"''',
      '''Text("Nova Mobile 0.6 · Drag & Drop Preview"''')
patch('''Расстановка иконок пока двумя касаниями.''',
      '''Режим «Расставить»: удерживай значок и перетаскивай на другую иконку, в том числе в Dock. Два касания также работают.''')

file.write_text(s, encoding="utf-8")

gradle=Path("app/build.gradle.kts")
b=gradle.read_text("utf-8")
assert 'applicationId = "com.novahome.mobile.personalizepreview"' in b
assert 'versionName = "0.5.0-preview"' in b
b=b.replace('applicationId = "com.novahome.mobile.personalizepreview"',
            'applicationId = "com.novahome.mobile.dragpreview"')
b=b.replace('versionCode = 6', 'versionCode = 7')
b=b.replace('versionName = "0.5.0-preview"', 'versionName = "0.6.0-preview"')
gradle.write_text(b, encoding="utf-8")

manifest=Path("app/src/main/AndroidManifest.xml")
m=manifest.read_text("utf-8")
assert 'android:label="Nova Mobile 0.5"' in m
manifest.write_text(m.replace('android:label="Nova Mobile 0.5"',
                              'android:label="Nova Mobile 0.6"'), encoding="utf-8")

print("Nova Mobile 0.6 drag-and-drop patch applied")
