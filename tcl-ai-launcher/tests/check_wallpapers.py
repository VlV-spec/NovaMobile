from pathlib import Path
from PIL import Image
p=Path(__file__).resolve().parents[1]
res=p/'android/app/src/main/res/drawable-nodpi'
names=['wallpaper_neon_moon','wallpaper_penthouse','wallpaper_cyberpunk','wallpaper_rooftop']
src=(p/'android/app/src/main/java/com/tclai/launcher/MainActivity.java').read_text()
assert len(names)==4
for name in names:
 f=res/(name+'.webp')
 assert f.exists() and f.stat().st_size < 750_000, f
 with Image.open(f) as im:
  assert im.size[0]>=1600 and im.size[1]>=900, im.size
  assert abs(im.size[0]/im.size[1] - 16/9) < .015
 assert 'R.drawable.'+name in src
assert 'ACTION_OPEN_DOCUMENT' in src
assert 'takePersistableUriPermission' in src
assert 'PICK_WALLPAPER' in src
assert 'category_icons' not in src.lower()
print('PASS: four original widescreen wallpapers, TV dimensions, compact assets, picker integration')
