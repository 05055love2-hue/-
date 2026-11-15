# يضيف صوت رجل يشرح كل تطبيق فوق مقطعه الدعائي
import asyncio, json, subprocess, os
import edge_tts
VOICE = "ar-SA-HamedNeural"
os.makedirs("out", exist_ok=True)
texts = json.load(open("voice.json", encoding="utf-8"))

def dur(p):
    return float(subprocess.check_output(["ffprobe","-v","error","-show_entries","format=duration","-of","csv=p=0",p]).decode().strip())

async def main():
    for k, t in texts.items():
        mp3 = f"out/{k}.mp3"
        await edge_tts.Communicate(t, VOICE, rate="+8%").save(mp3)
        d = dur(mp3); limit = 14.2
        tempo = max(1.0, min(1.35, d / limit))
        print(k, round(d, 2), "tempo", round(tempo, 3))
        subprocess.run(["ffmpeg","-y","-loglevel","error","-i",f"src/{k}.mp4","-i",mp3,
            "-filter_complex",f"[1:a]atempo={tempo},adelay=300|300,apad,volume=1.6[a]",
            "-map","0:v","-map","[a]","-c:v","copy","-c:a","aac","-b:a","160k","-t","15",
            "-movflags","+faststart",f"out/{k}.mp4"], check=True)

asyncio.run(main())
