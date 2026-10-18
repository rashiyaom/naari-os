#!/usr/bin/env python3
"""Render/test the actual watch C++ on a host, or serve it at localhost:5174.

python3 firmware/tools/preview_ui.py
python3 firmware/tools/preview_ui.py --serve

Only transport and device APIs are mocked. Layouts, touch dispatch, RGB565
storage, asset masks, blending and drawing come from the firmware source.
Requires clang++ and Pillow. No ESP-IDF installation needed for the preview.
"""
from pathlib import Path
import argparse
import json
import subprocess
import tempfile
import io
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
OUTPUT=ROOT/'build-ui-preview'
STUBS=OUTPUT/'stubs'

def prepare():
    OUTPUT.mkdir(exist_ok=True)
    headers={
        'esp_err.h':'#pragma once\nusing esp_err_t=int;\n#define ESP_OK 0\n#define ESP_ERR_NO_MEM 0x101\n',
        'esp_log.h':'#pragma once\n#define ESP_LOGI(...)\n#define ESP_LOGW(...)\n#define ESP_LOGE(...)\n',
        'esp_timer.h':'#pragma once\n#include <cstdint>\nint64_t esp_timer_get_time();\n',
        'esp_system.h':'#pragma once\n', 'esp_heap_caps.h':'#pragma once\n',
        'driver/spi_master.h':'#pragma once\nusing spi_device_handle_t=void*;\n#define SPI2_HOST 2\n',
        'driver/gpio.h':'#pragma once\nusing gpio_num_t=int;\n'+''.join(f'#define GPIO_NUM_{i} {i}\n' for i in range(49))+'#define GPIO_NUM_NC -1\n#define I2C_NUM_0 0\n#define I2S_NUM_0 0\n#define UART_NUM_1 1\n',
        'freertos/FreeRTOS.h':'#pragma once\nusing BaseType_t=int;\nusing QueueHandle_t=void*;\nusing TaskHandle_t=void*;\nusing SemaphoreHandle_t=void*;\nusing TimerHandle_t=void*;\n',
        **{f'freertos/{h}.h':'#pragma once\n#include "FreeRTOS.h"\n' for h in ['task','queue','semphr','timers']},
    }
    for name,content in headers.items():
        target=STUBS/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(content)
    # Extract the drawing methods verbatim so there is only one production
    # implementation, while removing GPIO / QSPI transport from the host build.
    src=(ROOT/'main/drivers/display_co5300/display_co5300.cpp').read_text()
    pure=src[src.index('void DisplayCO5300::clear('):src.index('void DisplayCO5300::present(')]
    (OUTPUT/'graphics.cpp').write_text('#include "drivers/display_co5300/display_co5300.h"\n#include <cstring>\n#include <cmath>\n#include <cstdlib>\nnamespace SheShield {\n'+pure+'\n}\n')
    cmd=['clang++','-std=c++17','-O1','-g','-fsanitize=address,undefined','-DNAARI_UI_PREVIEW',
         '-I'+str(STUBS),'-I'+str(ROOT/'main'),str(OUTPUT/'graphics.cpp'),
         str(ROOT/'main/drivers/display_co5300/ui_graphics.cpp'),str(ROOT/'main/ui/ui_manager.cpp'),
         str(ROOT/'tools/preview/native_preview.cpp'),'-o',str(OUTPUT/'native-preview')]
    subprocess.run(cmd,check=True)
    subprocess.run([str(OUTPUT/'native-preview')],cwd=OUTPUT,check=True)
    for ppm in OUTPUT.glob('*.ppm'):
        Image.open(ppm).save(ppm.with_suffix('.png'))
    names=['home','apps-1','apps-2','apps-3','apps-4','fall-detection','phone-link','contacts','keypad','sounds','controls','motion','system','guard','sos','cancel','recorder','recording','memo-saved','memo-library','memo-library-page-2','memo-detail','memo-stored-playing','memo-delete-confirm','memo-delete-synced','memo-deleted','memo-library-empty','faces','calling','message','torch']
    sheet=Image.new('RGB',(5*442,5*548),'#101012')
    draw=ImageDraw.Draw(sheet)
    for i,name in enumerate(names):
        x=(i%5)*442+16;y=(i//5)*548+30
        sheet.paste(Image.open(OUTPUT/(name+'.png')),(x,y))
        draw.text((x,y-21),name,fill='#aaaaaf')
    sheet.save(OUTPUT/'overview.png')
    print(f'Actual firmware frames and contact sheet: {OUTPUT}',flush=True)

HTML='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Naari · Native firmware preview</title>
<style>*{box-sizing:border-box}body{margin:0;background:#101012;color:#eee;font:15px system-ui,sans-serif}main{max-width:1080px;margin:42px auto;padding:0 24px}h1{font-size:28px;letter-spacing:-1px;font-weight:600}p{color:#98989f;line-height:1.6;max-width:660px}.layout{display:flex;gap:48px;align-items:flex-start;margin-top:32px}.watch{padding:12px;border:2px solid #55565b;border-radius:70px;background:#09090a;box-shadow:0 12px 36px #0008;position:relative}.watch:after{content:'';position:absolute;right:-10px;top:100px;height:35px;width:8px;background:#55565b;border-radius:4px}#frame{display:block;width:min(410px,77vw);height:auto;border-radius:58px;cursor:pointer;touch-action:none}button{border:0;background:#242426;color:#ddd;padding:12px 16px;border-radius:14px;font:inherit;cursor:pointer}button:hover{background:#38383c}nav{display:grid;grid-template-columns:1fr 1fr;gap:8px;max-width:370px}.hardware{display:flex;flex-wrap:wrap;gap:8px;margin-top:24px;max-width:410px}.hint{font-size:13px;margin-top:22px}.gallery{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:18px;margin:50px 0}.gallery img{width:100%;border-radius:26px;background:black}.gallery span{display:block;margin:10px 0;color:#999}@media(max-width:760px){.layout{flex-direction:column;align-items:center;gap:30px}main{margin-top:24px}nav{max-width:none}}
</style><main><h1>Naari OS · Watch preview</h1><p>These pixels come from the watch's C++ framebuffer renderer. Tap the screen, use the crown, or swipe to explore. Device readings are sample values in this preview.</p><div class="layout"><div><div class="watch"><img id="frame" src="home.png" alt="Actual firmware framebuffer" draggable="false"></div><div class="hardware"><button onclick="act('crown')">Digital Crown</button><button onclick="act('up')">Controls</button><button onclick="act('right')">Previous page</button><button onclick="act('left')">Next page</button></div><p class="hint">Call, SMS and SOS actions are simulated locally.</p></div><div><nav id="screens"></nav><p id="status" class="hint">Native renderer · 410 × 502 · RGB565</p></div></div><div class="gallery" id="gallery"></div></main>
<script>const screens=[['Watch face',0],['Apps',1],['Sounds',2],['Controls',3],['Flashlight',4],['Motion',5],['System',6],['Night Guard',7],['SOS',8],['Guardians',9],['Keypad',10],['Disarm',11],['Voice Memos',14],['Watch Faces',15],['Fall Detection',16],['Phone Link',17]];let actions=[],busy=false;const frame=document.getElementById('frame');for(const [name,id]of screens){const b=document.createElement('button');b.textContent=name;b.onclick=()=>{actions=[];act('screen:'+id)};document.getElementById('screens').append(b)}async function act(action){if(busy)return;busy=true;actions.push(action);try{const r=await fetch('/render',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({actions})});if(!r.ok)throw Error(await r.text());const old=frame.src;frame.src=URL.createObjectURL(await r.blob());if(old.startsWith('blob:'))URL.revokeObjectURL(old);document.getElementById('status').textContent='Rendered by the actual firmware C++'}catch(e){document.getElementById('status').textContent=e.message}finally{busy=false}}let down;frame.onpointerdown=e=>{down=[e.clientX,e.clientY];frame.setPointerCapture(e.pointerId)};frame.onpointerup=e=>{if(!down)return;const dx=e.clientX-down[0],dy=e.clientY-down[1],r=frame.getBoundingClientRect();if(Math.abs(dx)>45||Math.abs(dy)>45)act(Math.abs(dx)>Math.abs(dy)?dx>0?'right':'left':dy>0?'down':'up');else act(Math.floor((e.clientX-r.left)*410/r.width)+','+Math.floor((e.clientY-r.top)*502/r.height));down=null};for(const name of ['home','apps-1','apps-2','apps-3','apps-4','fall-detection','phone-link','contacts','keypad','sounds','controls','motion','system','guard','sos','cancel','recorder','recording','memo-saved','memo-library','memo-library-page-2','memo-detail','memo-stored-playing','memo-delete-confirm','memo-delete-synced','memo-deleted','memo-library-empty','faces']){const div=document.createElement('div');const img=document.createElement('img');img.src=name+'.png';img.alt=name;const label=document.createElement('span');label.textContent=name;div.append(img,label);document.getElementById('gallery').append(div)}</script></html>'''

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path=='/' or self.path=='/index.html':
            data=HTML.encode();mime='text/html; charset=utf-8'
        else:
            name=self.path.lstrip('/')
            if '/' in name or not name.endswith('.png') or not (OUTPUT/name).is_file():
                self.send_error(404);return
            data=(OUTPUT/name).read_bytes();mime='image/png'
        self.send_response(200);self.send_header('Content-Type',mime);self.end_headers();self.wfile.write(data)
    def do_POST(self):
        if self.path!='/render':self.send_error(404);return
        try:
            n=int(self.headers.get('Content-Length','0'))
            if n>16384:raise ValueError('Action history too large; select a screen to reset.')
            actions=json.loads(self.rfile.read(n)).get('actions',[])
            if not isinstance(actions,list) or len(actions)>256 or any(not isinstance(a,str) or len(a)>32 for a in actions):raise ValueError('Invalid action history')
            with tempfile.TemporaryDirectory(prefix='naari-frame-') as tmp:
                subprocess.run([str(OUTPUT/'native-preview'),*actions],cwd=tmp,check=True,capture_output=True,timeout=10)
                buf=io.BytesIO();Image.open(Path(tmp)/'frame.ppm').save(buf,format='PNG');data=buf.getvalue()
            self.send_response(200);self.send_header('Content-Type','image/png');self.end_headers();self.wfile.write(data)
        except Exception as e:self.send_error(500,str(e))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--serve',action='store_true');parser.add_argument('--skip-build',action='store_true');args=parser.parse_args()
    if not args.skip_build:prepare()
    if args.serve:
        print('Native Watch OS preview: http://127.0.0.1:5174/',flush=True)
        ThreadingHTTPServer(('127.0.0.1',5174),Handler).serve_forever()
