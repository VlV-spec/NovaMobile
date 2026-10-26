from pathlib import Path
from lxml import etree
import re
root=Path(__file__).resolve().parents[1]
xml=root/'android/app/src/main/AndroidManifest.xml'
tree=etree.parse(str(xml))
ns={'android':'http://schemas.android.com/apk/res/android'}
activities=tree.xpath('//activity',namespaces=ns)
assert len(activities)==2
assert not tree.xpath('//category[@android:name="android.intent.category.HOME"]',namespaces=ns), 'MVP must not register as HOME'
assert tree.xpath('//category[@android:name="android.intent.category.LEANBACK_LAUNCHER"]',namespaces=ns)
assert tree.xpath('//category[@android:name="android.intent.category.LAUNCHER"]',namespaces=ns)
source='\n'.join(f.read_text() for f in root.rglob('*.java'))
assert 'com.spocky.projengmenu' not in source
assert 'set-home-activity' not in source
assert 'OPENAI_API_KEY' not in source
assert 'premium' not in source.lower().replace('projectivy','') or 'Платных функций' in source
for cls in ('MainActivity','AssistantActivity'):
    f=root/f'android/app/src/main/java/com/tclai/launcher/{cls}.java'
    assert f.exists()
    s=f.read_text()
    assert s.count('{')==s.count('}'),cls
print('PASS: manifest XML, separate app, no HOME takeover, files and basic consistency')
