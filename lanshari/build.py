# يركّب التطبيق: تصميم Antigravity (design.html) + الطبقة الحقيقية (real.js) — برمجة أبو نواف
import os
d = os.path.dirname(os.path.abspath(__file__))
s = open(os.path.join(d, 'design.html'), encoding='utf-8').read()
s = s.replace('https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js', 'tw.js')
s = s.replace('setTimeout(simSpeed,1000);', '').replace('setTimeout(simRPM,1200);', '')
r = open(os.path.join(d, 'real.js'), encoding='utf-8').read()
i = s.rindex('</body>')
s = s[:i] + '<script>' + r + '</script>\n' + s[i:]
open(os.path.join(d, 'src/main/assets/index.html'), 'w', encoding='utf-8').write(s)
print('ok', len(s))
