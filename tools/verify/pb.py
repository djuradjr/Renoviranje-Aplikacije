"""Prints DataStore preference entries whose key contains argv[1] (int/long values decoded)."""
import sys

data = sys.stdin.buffer.read()


def varint(buf, i):
    n = shift = 0
    while True:
        b = buf[i]; i += 1
        n |= (b & 0x7F) << shift; shift += 7
        if not b & 0x80:
            return n, i


def fields(buf):
    i = 0
    while i < len(buf):
        tag, i = varint(buf, i)
        f, w = tag >> 3, tag & 7
        if w == 0:
            v, i = varint(buf, i)
        elif w == 2:
            ln, i = varint(buf, i); v = buf[i:i + ln]; i += ln
        elif w == 1:
            v = buf[i:i + 8]; i += 8
        elif w == 5:
            v = buf[i:i + 4]; i += 4
        else:
            return
        yield f, w, v


for _, _, entry in fields(data):
    key = val = None
    for f, w, v in fields(entry):
        if f == 1:
            key = v.decode()
        elif f == 2:
            val = [(ff, vv if ww == 0 else vv[:60]) for ff, ww, vv in fields(v)]
    if key and sys.argv[1] in key:
        print(key, "=", val)
