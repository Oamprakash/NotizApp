import sys, re
from playwright.sync_api import sync_playwright
src = open(sys.argv[1]).read()
jobs = [  # (out, size, viewBox, clip)  clip: none | round | circle
    ('ios-1024.png', 1024, '18 18 72 72', 'none'),
    ('play-store-512.png', 512, '18 18 72 72', 'none'),
    ('preview-rounded.png', 256, '18 18 72 72', 'round'),
    ('preview-circle.png', 256, '18 18 72 72', 'circle'),
]
for d, px in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    jobs.append((f'mipmap-{d}/ic_launcher.png', px, '18 18 72 72', 'round'))
    jobs.append((f'mipmap-{d}/ic_launcher_round.png', px, '18 18 72 72', 'circle'))
import os
with sync_playwright() as p:
    b = p.chromium.launch()
    for out, size, vb, clip in jobs:
        svg = re.sub(r'viewBox="[^"]+"', f'viewBox="{vb}" width="{size}" height="{size}"', src, count=1)
        radius = {'none': '0', 'round': '18%', 'circle': '50%'}[clip]
        html = f'<html><body style="margin:0;background:transparent"><div style="width:{size}px;height:{size}px;border-radius:{radius};overflow:hidden">{svg}</div></body></html>'
        pg = b.new_page(viewport={'width': size, 'height': size})
        pg.set_content(html)
        os.makedirs(os.path.dirname(out) or '.', exist_ok=True)
        pg.screenshot(path=out, omit_background=(clip != 'none'), clip={'x': 0, 'y': 0, 'width': size, 'height': size})
        pg.close()
    b.close()
print('done')
