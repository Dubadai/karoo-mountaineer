"""Before / after page from the field gallery's pictures: python3 make_compare.py"""
import base64, pathlib
here = pathlib.Path(__file__).parent
u = lambda p: 'data:image/png;base64,' + base64.b64encode(p.read_bytes()).decode()
rows = [('One tile · ETA TO TOP', 'eta-quarter'), ('One tile · VS PR', 'gap-quarter'), ('One tile · ahead of PR (after only: the hyphen)', 'ahead-quarter'),
        ('Two tiles · ETA + VS PR', 'eta-row'), ('Half screen', 'eta-half'), ('Before the climb · half', 'next-half')]
html = ['<meta charset="utf-8"><title>Mountaineer Karoo Type</title><style>body{margin:0;background:#2A2A2A;color:#EDEDED;font:15px/1.4 system-ui,sans-serif}main{max-width:1000px;margin:0 auto;padding:24px 16px 64px}h1{font-size:22px;margin:0 0 4px}h2{font-size:17px;margin:28px 0 8px}p{color:#9A9A9A;margin:0 0 12px;max-width:720px}.g{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;align-items:start}.g div{font-size:13px;color:#BBB}.g img{width:100%;height:auto;display:block;margin-top:4px}</style><main>',
        "<h1>Karoo type: before and after</h1><p>Drawn by Mountaineer's own code in the field gallery (Karoo 3 emulator with the Karoo's fonts). After: numbers in the Karoo's own value font (Relative, spacing −0.04, as its native fields), headers sized and placed by the Karoo's header rules (caps 23 / 20 px, its top and right margins, icon box), the one-tile time right under the header. Sa Calobra, 5.2 km up.</p>"]
for t, f in rows:
    html.append(f'<h2>{t}</h2><div class="g">')
    for mode in ('light', 'dark'):
        for side in ('before', 'after'):
            if not (here / side / f"{f}-{mode}.png").exists(): continue
            html.append(f'<div>{side.capitalize()} · {mode}<img src="{u(here / side / f"{f}-{mode}.png")}"></div>')
    html.append('</div>')
html.append('</main>')
(here / 'compare.html').write_text(''.join(html))
print('wrote compare.html')
