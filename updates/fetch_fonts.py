# ينزّل خطوط عربية من Google Fonts وقت البناء ويحطها داخل التطبيق (تشتغل بدون نت)
# الخطوط كلها برخصة OFL المجانية
import os, re, sys, urllib.request

out = sys.argv[1] if len(sys.argv) > 1 else 'watsy/src/main/assets'
fams = ['Aref+Ruqaa:wght@700', 'Amiri:wght@400;700', 'Reem+Kufi:wght@700', 'Lalezar',
        'El+Messiri:wght@700', 'Cairo:wght@400;700;800', 'Marhey:wght@700', 'Rakkas']
url = 'https://fonts.googleapis.com/css2?' + '&'.join('family=' + f for f in fams) + '&display=swap'
ua = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36'
css = urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': ua})).read().decode()
os.makedirs(os.path.join(out, 'fonts'), exist_ok=True)
n = 0
def grab(m):
    global n
    n += 1
    name = f'f{n}.woff2'
    data = urllib.request.urlopen(urllib.request.Request(m.group(1), headers={'User-Agent': ua})).read()
    open(os.path.join(out, 'fonts', name), 'wb').write(data)
    return f'url(fonts/{name})'
css = re.sub(r'url\((https://[^)]+)\)', grab, css)
open(os.path.join(out, 'fonts.css'), 'w', encoding='utf-8').write(css)
print('fonts:', n)
