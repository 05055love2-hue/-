# يسوي ملف versions.json من أرقام النسخ في كل تطبيق — يشتغل وقت البناء
import json, re
mods = {'app': 'car-theme', 'tv': 'tv-launcher', 'ytcar': 'mushaghli', 'seasons': 'seasons', 'rawatib': 'rawatib', 'hammil': 'hammil', 'obd': 'fahes', 'store': 'tatbeeqati', 'cleaner': 'monadhif', 'salati': 'salati', 'sayarati': 'sayarati', 'widgety': 'widgety', 'tamarini': 'tamarini', 'qisas': 'qisas', 'shoof': 'shoof', 'watsy': 'watsy'}
notes = json.load(open('updates/notes.json', encoding='utf-8'))
out = {}
for m, key in mods.items():
    g = open(f'{m}/build.gradle', encoding='utf-8').read()
    code = int(re.search(r'versionCode (\d+)', g).group(1))
    name = re.search(r'versionName "([^"]+)"', g)
    out[key] = {'code': code, 'name': name.group(1) if name else '', 'apk': key + '.apk', 'notes': notes.get(key, '')}
json.dump(out, open('versions.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(json.dumps(out, ensure_ascii=False))
