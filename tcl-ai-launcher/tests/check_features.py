from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'android/app/src/main/java/com/tclai/launcher/MainActivity.java').read_text()
ai=(root/'android/app/src/main/java/com/tclai/launcher/AssistantActivity.java').read_text()
assert 'settings.setOnClickListener(v -> openAndroidSettings());' in main
assert 'settings.setOnLongClickListener(v -> { showWallpaperControls(); return true; });' in main
assert 'android.provider.Settings.ACTION_SETTINGS' in main
assert 'ColorMatrixColorFilter' in main
assert '"wallpaper_saturation"' in main and '"wallpaper_brightness"' in main
assert 'showWallpaperControls();break;' in main
assert 'Button ping=button("Проверить сервер")' in ai
assert 'new URL(url + "/health")' in ai
assert 'OPENAI_API_KEY' not in ai
print('PASS: Android settings click/long press, image adjustments, fallback menu, AI health check')
