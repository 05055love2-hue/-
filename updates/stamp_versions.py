# يكتب رقم النسخة جنب «برمجة أبو نواف» في كل تطبيق — يشتغل وقت البناء
import re, os
mods = ['app', 'tv', 'router', 'seasons', 'ytcar', 'rawatib', 'obd', 'hammil', 'cleaner', 'salati', 'widgety', 'tamarini', 'qisas', 'watsy']
for m in mods:
    f = f'{m}/src/main/assets/index.html'
    if not os.path.exists(f):
        continue
    g = open(f'{m}/build.gradle', encoding='utf-8').read()
    v = re.search(r'versionName "([^"]+)"', g).group(1)
    h = open(f, encoding='utf-8').read()
    n = h.count('برمجة أبو نواف<')
    h = h.replace('برمجة أبو نواف<', f'برمجة أبو نواف • نسخة {v}<')
    open(f, 'w', encoding='utf-8').write(h)
    print(m, v, n)
