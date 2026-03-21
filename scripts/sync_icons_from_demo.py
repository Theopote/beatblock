"""Parse IcoMoon demo.html and emit Icons.java-style mapping. Run from repo root."""
from __future__ import annotations

import re
import sys
from pathlib import Path


def parse_demo(html: str) -> dict[str, str]:
    pairs = re.findall(
        r"&#x([0-9a-fA-F]+);</span>\s+icon-bb-([^<]+)</div>",
        html,
        flags=re.IGNORECASE,
    )
    return {name.strip(): h.lower() for h, name in pairs}


def main() -> int:
    demo_path = Path(
        r"C:\Users\OOPS\Desktop\untitled-project (2)\font\demo.html"
    )
    if not demo_path.is_file():
        print("demo.html not found:", demo_path, file=sys.stderr)
        return 1
    m = parse_demo(demo_path.read_text(encoding="utf-8"))
    print("glyphs:", len(m))
    for k in sorted(m):
        print(f"{k}\t{m[k]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
