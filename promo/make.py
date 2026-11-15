import json,subprocess,os,sys,shutil
from playwright.sync_api import sync_playwright
CF={
 'car':dict(name='ثيم هادي',emoji='🚗',a='#2dd4bf',b='#2563eb',orient='land',hook='شاشة سيارتك<br>تستاهل شكل أحلى',
   imgs=['img/car.png','img/car2.png','img/car3.png'],feats=[['🎨','أكثر من 10 أشكال للواجهة'],['🎵','موسيقاك والفيديو بالواجهة'],['⛅','الساعة والطقس حسب مدينتك']],cta='لانشر لشاشة السيارة الأندرويد'),
 'tv':dict(name='شاشتي',emoji='📺',a='#f59e0b',b='#ea580c',orient='land',hook='خلّ تلفزيونك<br>بواجهة فخمة',
   imgs=['img/tv1.png'],feats=[['✨','واجهة أنيقة وبلاطات تلمع'],['🕌','مواقيت الصلاة على الشاشة'],['⭐','رتّب تطبيقاتك بالريموت']],cta='لانشر لتلفزيونك الأندرويد'),
 'ytcar':dict(name='مشغّلي',emoji='▶️',a='#38bdf8',b='#ec4899',orient='land',hook='الفيديو قدامك<br>وأنت بالشاشة الرئيسية',
   imgs=['img/yt1.png','img/yt2.png','img/yt3.png'],feats=[['⏯️','مشغّل كبير بأزرار واضحة'],['🎨','6 ألوان تختار منها'],['▦','كل تطبيقات شاشتك بضغطة']],cta='شاشة رئيسية لسيارتك'),
 'seasons':dict(name='فصول السنة',emoji='⭐',a='#f4a261',b='#7c3aed',orient='port',hook='متى يدخل<br>الوسم؟ 🌧️',
   imgs=['img/s1.png','img/s2.png','img/s3.png'],feats=[['⏳','عداد لسهيل والوسم والمربعانية'],['🔔','ويدجت وتنبيه بدخول الموسم'],['🌱','وش تزرع في كل موسم']],cta='المواسم والفصول بعدّ تنازلي'),
 'rawatib':dict(name='مواعيد الصرف',emoji='💰',a='#10b981',b='#2563eb',orient='port',hook='كم باقي<br>على الراتب؟',
   imgs=['img/r1.png','img/r2.png','img/r3.png'],feats=[['🏛️','الراتب وحساب المواطن والضمان'],['⏳','عدّ تنازلي بالثواني'],['📱','ويدجت وتنبيه يوم الصرف']],cta='كل مواعيد الصرف في مكان واحد'),
}
FPS=24;DUR=15
keys=sys.argv[1:] or list(CF)
preview='--preview' in keys
keys=[k for k in keys if k!='--preview'] or list(CF)
with sync_playwright() as p:
    b=p.chromium.launch(executable_path='/usr/bin/google-chrome')
    for k in keys:
        pg=b.new_page(viewport={'width':540,'height':960},device_scale_factor=2)
        pg.add_init_script('window.CFG='+json.dumps(CF[k],ensure_ascii=False))
        pg.goto('file:///home/claude/promo/promo.html');pg.wait_for_timeout(800)
        if preview:
            for t in [1.6,7,13.5]:
                pg.evaluate(f'render({t})');pg.screenshot(path=f'/tmp/pv_{k}_{t}.png')
            continue
        d=f'/tmp/fr_{k}';shutil.rmtree(d,ignore_errors=True);os.makedirs(d)
        for i in range(FPS*DUR):
            pg.evaluate(f'render({i/FPS})');pg.screenshot(path=f'{d}/{i:04d}.jpg',type='jpeg',quality=92)
        subprocess.run(['ffmpeg','-y','-loglevel','error','-framerate',str(FPS),'-i',f'{d}/%04d.jpg','-f','lavfi','-i','anullsrc=r=44100:cl=stereo','-shortest','-c:v','libx264','-preset','veryfast','-crf','20','-pix_fmt','yuv420p','-c:a','aac','-movflags','+faststart',f'/home/claude/promo/{k}.mp4'],check=True)
        shutil.rmtree(d);print('done',k)
