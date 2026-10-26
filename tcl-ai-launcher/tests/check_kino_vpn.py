from pathlib import Path
from lxml import etree
root=Path(__file__).resolve().parents[1]
main=(root/'android/app/src/main/java/com/tclai/launcher/MainActivity.java').read_text()
manifest=(root/'android/app/src/main/AndroidManifest.xml').read_text()
for token in ['vpnBadge()', 'NetworkCapabilities.TRANSPORT_VPN', 'com.happproxy',
              'drawKinoWatchlist()', 'kino_watchlist', 'showPosters', 'watch.kino',
              'ACTION_OPEN_DOCUMENT', 'getLeanbackLaunchIntentForPackage']:
    assert token in main, token
assert 'android.permission.ACCESS_NETWORK_STATE' in manifest
assert '<package android:name="com.happproxy"' in manifest
assert 'android.intent.category.HOME' not in manifest
assert main.count('{') == main.count('}')
print('PASS: VPN indicator and editable Kino series tiles, no HOME takeover')
