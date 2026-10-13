#!/usr/bin/env python3
"""Capture the watch's USB serial log; run with the ESP-IDF Python environment."""
import argparse, time
from pathlib import Path
import serial
from esptool.reset import HardReset
p=argparse.ArgumentParser();p.add_argument('--port',required=True);p.add_argument('--seconds',type=int,default=30);p.add_argument('--output',default='/tmp/naari-watch-boot.log');p.add_argument('--reset',action='store_true');a=p.parse_args()
with serial.Serial(a.port,115200,timeout=.2) as port, Path(a.output).open('w') as log:
    port.dtr=False;port.rts=False
    if a.reset:HardReset(port,uses_usb=True)()
    end=time.monotonic()+min(a.seconds,55)
    while time.monotonic()<end:
        text=port.read(port.in_waiting or 1).decode('utf-8',errors='replace')
        if text:log.write(text);log.flush()
print(f'Captured watch log: {a.output}')
