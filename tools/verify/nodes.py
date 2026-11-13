"""Reads a uiautomator dump on stdin, prints text|id|bounds|checked for labelled nodes."""
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
xml = sys.stdin.buffer.read().decode("utf-8", "replace")
for m in re.finditer(r"<node [^>]*>", xml):
    n = m.group(0)

    def attr(name):
        g = re.search(name + r'="([^"]*)"', n)
        return g.group(1) if g else ""

    text = attr("text") or attr("content-desc")
    rid = attr("resource-id").split("/")[-1]
    if not text and not rid:
        continue
    b = re.findall(r"\d+", attr("bounds"))
    print(f"{text}|{rid}|{','.join(b)}|{attr('checked')}")
