# صوت دعائي احترافي: معالجة للصوت + موسيقى خلفية خفيفة تنخفض وقت الكلام
# الاستخدام: python voice.py            ← كل المقاطع بالصوت المختار
#           python voice.py samples    ← مقطع مواعيد الصرف بكل الأصوات عشان نختار
import asyncio, json, subprocess, os, sys, wave
import numpy as np
import edge_tts

CFG = json.load(open("voice_cfg.json", encoding="utf-8"))
texts = json.load(open("voice.json", encoding="utf-8"))
os.makedirs("out", exist_ok=True)
SR = 44100

def dur(p):
    return float(subprocess.check_output(["ffprobe","-v","error","-show_entries","format=duration","-of","csv=p=0",p]).decode())

def music(path, seconds=15.0, bpm=100):
    """موسيقى خلفية بسيطة من صنعنا (بدون حقوق): نغمات هادئة + إيقاع خفيف"""
    n = int(SR*seconds); t = np.arange(n)/SR; out = np.zeros(n)
    beat = 60/bpm; bar = beat*4
    chords = [[57,60,64],[53,57,60],[48,55,60],[55,59,62]]  # Am F C G
    f = lambda m: 440*2**((m-69)/12)
    for i in range(int(seconds/bar)+1):
        s = int(i*bar*SR); e = min(n, int((i+1)*bar*SR))
        if s >= n: break
        tt = t[s:e]-t[s]; env = np.minimum(1, tt/0.4)*np.minimum(1,(bar-tt)/0.4)
        for m in chords[i%4]:
            for h,a in [(1,1),(2,.35),(3,.15)]:
                out[s:e] += a*0.05*env*np.sin(2*np.pi*f(m)*h*tt)
        # باص
        out[s:e] += 0.09*env*np.sin(2*np.pi*f(chords[i%4][0]-12)*tt)
    k = 0
    while k*beat < seconds:   # كيك + هاي هات
        s = int(k*beat*SR); L = int(0.25*SR); tt = np.arange(min(L,n-s))/SR
        out[s:s+len(tt)] += 0.35*np.exp(-tt*18)*np.sin(2*np.pi*(55+90*np.exp(-tt*30))*tt)
        h = int((k+0.5)*beat*SR); Lh = int(0.05*SR)
        if h+Lh < n: out[h:h+Lh] += 0.04*np.random.randn(Lh)*np.exp(-np.arange(Lh)/SR*80)
        k += 1
    fade = np.ones(n); fi = int(0.8*SR); fo = int(1.5*SR)
    fade[:fi] = np.linspace(0,1,fi); fade[-fo:] = np.linspace(1,0,fo)
    out = out*fade; out = out/np.max(np.abs(out))*0.8
    st = np.stack([out,out],1); data = (st*32767).astype(np.int16)
    with wave.open(path,"wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(data.tobytes())

async def make(k, voice, outname, pitch=None, rate=None):
    mp3 = f"out/{outname}.mp3"
    await edge_tts.Communicate(texts[k], voice, rate=rate or CFG["rate"], pitch=pitch or CFG["pitch"]).save(mp3)
    d = dur(mp3); tempo = max(1.0, min(1.25, d/14.2))
    print(outname, voice, round(d,2), "tempo", round(tempo,3))
    fc = (f"[1:a]atempo={tempo},adelay=350|350,highpass=f=80,"
          "equalizer=f=120:t=q:w=1:g=3,equalizer=f=3500:t=q:w=1.2:g=3,"
          "acompressor=threshold=-20dB:ratio=3.5:attack=5:release=90:makeup=5,"
          "aecho=0.8:0.4:35:0.06,apad,asplit=2[v1][v2];"
          "[2:a]volume=0.45[m];[m][v1]sidechaincompress=threshold=0.02:ratio=8:attack=15:release=350[md];"
          "[md][v2]amix=inputs=2:normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11,atrim=0:15[a]")
    subprocess.run(["ffmpeg","-y","-loglevel","error","-i",f"src/{k}.mp4","-i",mp3,"-i","out/music.wav",
        "-filter_complex",fc,"-map","0:v","-map","[a]","-c:v","copy","-c:a","aac","-b:a","192k","-ar","44100",
        "-t","15","-movflags","+faststart",f"out/{outname}.mp4"], check=True)
    os.remove(mp3)

def music_only(k):
    """بدون صوت رجّال: الموسيقى الخفيفة بس، والكلام مكتوب في المقطع نفسه"""
    print(k, "music only")
    subprocess.run(["ffmpeg","-y","-loglevel","error","-i",f"src/{k}.mp4","-i","out/music.wav",
        "-filter_complex","[1:a]volume=0.7,loudnorm=I=-16:TP=-1.5:LRA=11,atrim=0:15[a]",
        "-map","0:v","-map","[a]","-c:v","copy","-c:a","aac","-b:a","192k","-ar","44100",
        "-t","15","-movflags","+faststart",f"out/{k}.mp4"], check=True)

async def main():
    music("out/music.wav")
    if CFG["voice"] == "none":
        for k in texts: music_only(k)
        os.remove("out/music.wav"); return
    allv = [v["ShortName"] for v in await edge_tts.list_voices() if v["Locale"].startswith("ar-") and v["Gender"]=="Male"]
    print("الأصوات المتوفرة:", allv)
    if len(sys.argv) > 1 and sys.argv[1] == "samples":
        for v, pitch, rate, label in CFG["samples"]:
            if v in allv: await make("rawatib", v, "sample_"+label, pitch, rate)
    else:
        for k in texts: await make(k, CFG["voice"], k)
    os.remove("out/music.wav")

asyncio.run(main())
