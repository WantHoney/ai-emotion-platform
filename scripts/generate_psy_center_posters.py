from __future__ import annotations

import hashlib
import json
import random
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SEED_PATH = ROOT / "backend" / "src" / "main" / "resources" / "seeds" / "psy_centers.json"
ASSET_DIR = ROOT / "frontend" / "public" / "assets" / "psy-centers"
ANIME_DIR = ROOT / "frontend" / "public" / "assets" / "psy-centers" / "anime"
FALLBACK_PATH = ROOT / "frontend" / "public" / "assets" / "illustrations" / "psy-center-fallback.svg"


CITY_PALETTES = {
    "北京": {
        "bg": "#10243d",
        "accent": "#79c5ff",
        "accent_alt": "#95e5d7",
        "warm": "#e6c78e",
        "glow": "rgba(84, 192, 255, 0.24)",
    },
    "上海": {
        "bg": "#0e2140",
        "accent": "#73b0ff",
        "accent_alt": "#8fe8d4",
        "warm": "#e3c18a",
        "glow": "rgba(84, 146, 255, 0.24)",
    },
    "杭州": {
        "bg": "#112c43",
        "accent": "#7bd2ca",
        "accent_alt": "#a4e68d",
        "warm": "#dfc48b",
        "glow": "rgba(87, 210, 205, 0.22)",
    },
    "福州": {
        "bg": "#123148",
        "accent": "#7ed2ff",
        "accent_alt": "#87edd8",
        "warm": "#efb98f",
        "glow": "rgba(72, 190, 255, 0.22)",
    },
    "广州": {
        "bg": "#162746",
        "accent": "#85b8ff",
        "accent_alt": "#8ce4c8",
        "warm": "#eeb27a",
        "glow": "rgba(102, 170, 255, 0.22)",
    },
    "深圳": {
        "bg": "#102744",
        "accent": "#71d4ff",
        "accent_alt": "#97e390",
        "warm": "#d9c790",
        "glow": "rgba(78, 214, 255, 0.22)",
    },
    "default": {
        "bg": "#12263e",
        "accent": "#82cfff",
        "accent_alt": "#90ead7",
        "warm": "#dbc18f",
        "glow": "rgba(84, 192, 255, 0.24)",
    },
}


SOURCE_LEVEL_LABEL = {
    "official": "官方来源",
    "gov_directory": "政务目录",
    "trusted_reference": "可信参考",
}


ANIME_BACKGROUND_BY_KIND = {
    "center": "center-campus.png",
    "hospital": "hospital-specialty.png",
    "brain": "hospital-specialty.png",
    "counsel": "counsel-room.png",
    "recovery": "recovery-garden.png",
}


def escape_xml(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "&apos;")
    )


def stable_rng(seed_key: str) -> random.Random:
    digest = hashlib.md5(seed_key.encode("utf-8")).hexdigest()
    return random.Random(int(digest[:8], 16))


def poster_kind(name: str) -> tuple[str, str]:
    if "脑科" in name:
        return "brain", "脑科专科"
    if "社会心理" in name or "心理健康" in name or "诊疗中心" in name or "心理医院" in name:
        return "counsel", "心理服务"
    if "疗养院" in name or "防治院" in name:
        return "recovery", "康复支持"
    if "精神卫生" in name or "保健院" in name:
        return "center", "精神卫生"
    return "hospital", "专科医院"


def compact_name(name: str, city_name: str) -> str:
    normalized = name.strip()
    if "（" in normalized and "）" in normalized:
        inner = normalized.split("（", 1)[1].split("）", 1)[0].strip()
        normalized = inner if 4 <= len(inner) <= 14 else normalized.split("（", 1)[0].strip()
    elif "(" in normalized and ")" in normalized:
        inner = normalized.split("(", 1)[1].split(")", 1)[0].strip()
        normalized = inner if 4 <= len(inner) <= 14 else normalized.split("(", 1)[0].strip()

    prefixes = [f"{city_name}市", city_name, "北京市", "上海市", "杭州市", "福州市", "广州市", "深圳市"]
    for prefix in prefixes:
        if normalized.startswith(prefix):
            normalized = normalized[len(prefix) :].lstrip("市")

    replacements = [
        ("儿童青少年心理健康诊疗中心", "青少年心理诊疗"),
        ("心理健康诊疗中心", "心理诊疗中心"),
    ]
    for old, new in replacements:
        normalized = normalized.replace(old, new)
    return normalized or name


def wrap_text(text: str, width: int) -> list[str]:
    lines: list[str] = []
    chunk = ""
    for char in text:
        if len(chunk) >= width:
            lines.append(chunk)
            chunk = char
        else:
            chunk += char
    if chunk:
        lines.append(chunk)
    return lines


def title_lines(name: str, city_name: str) -> tuple[list[str], int]:
    display = compact_name(name, city_name)
    if len(display) <= 7:
        return [display], 42

    for token in ["分中心", "院区", "医院", "中心", "保健院", "防治院", "疗养院"]:
        idx = display.find(token)
        if idx == -1:
            continue
        cut = idx + len(token)
        left = display[:cut]
        right = display[cut:]
        if right and len(left) <= 10 and len(right) <= 9:
            return [left, right], 34

    lines = wrap_text(display, 7)
    if len(lines) > 3:
        lines = lines[:3]
        last = lines[-1]
        lines[-1] = (last[:-1] if len(last) > 1 else last) + "…"
    font_size = 40 if len(lines) == 1 else 34 if len(lines) == 2 else 30
    return lines, font_size


def background_href(kind: str) -> str:
    filename = ANIME_BACKGROUND_BY_KIND.get(kind, "center-campus.png")
    return f"/assets/psy-centers/anime/{filename}"


def ornament_svg(palette: dict[str, str], rng: random.Random) -> str:
    arc_y = 370 + rng.randint(-12, 10)
    arc_lift = rng.randint(-8, 8)
    dot_x = 504 + rng.randint(-16, 12)
    return f"""
  <circle cx="118" cy="120" r="96" fill="rgba(255,255,255,0.05)" />
  <circle cx="514" cy="112" r="116" fill="{palette['glow']}" opacity="0.54" />
  <path d="M82 {arc_y} C168 {arc_y - 96 + arc_lift} 292 {arc_y - 126 + arc_lift} 454 {arc_y - 86 + arc_lift} C502 {arc_y - 74 + arc_lift} 542 {arc_y - 48 + arc_lift} 574 {arc_y - 10}" fill="none" stroke="{palette['accent_alt']}" stroke-width="14" stroke-linecap="round" opacity="0.94" />
  <path d="M82 {arc_y + 10} C168 {arc_y - 86 + arc_lift} 292 {arc_y - 116 + arc_lift} 454 {arc_y - 76 + arc_lift} C502 {arc_y - 64 + arc_lift} 542 {arc_y - 38 + arc_lift} 574 {arc_y}" fill="none" stroke="{palette['accent']}" stroke-width="8" stroke-linecap="round" opacity="0.7" />
  <circle cx="{dot_x}" cy="490" r="24" fill="rgba(255,255,255,0.08)" />
  <path d="M{dot_x - 8} 490 H{dot_x + 8} M{dot_x} 482 V498" stroke="{palette['warm']}" stroke-width="5" stroke-linecap="round" />
"""


def title_markup(title: list[str], font_size: int) -> tuple[str, int, int]:
    lines: list[str] = []
    title_y = 482
    line_height = font_size + 8
    for idx, line in enumerate(title):
        lines.append(
            f"<text x='56' y='{title_y + idx * line_height}' font-size='{font_size}' font-weight='700' "
            f"font-family='Microsoft YaHei, PingFang SC, Noto Sans SC, sans-serif' fill='#f5fbff'>{escape_xml(line)}</text>"
        )
    subtitle_y = title_y + max(1, len(title)) * line_height + 10
    return "".join(lines), subtitle_y, line_height


def make_svg(item: dict[str, object]) -> str:
    seed_key = str(item["seedKey"])
    city_name = str(item["cityName"])
    district = str(item["district"])
    name = str(item["name"])
    source_level = str(item.get("sourceLevel") or "")
    recommended = bool(item.get("recommended"))
    palette = CITY_PALETTES.get(city_name, CITY_PALETTES["default"])
    rng = stable_rng(seed_key)
    kind, type_label = poster_kind(name)
    title, font_size = title_lines(name, city_name)
    source_label = SOURCE_LEVEL_LABEL.get(source_level, "来源备注")
    title_svg, subtitle_y, _ = title_markup(title, font_size)
    badge_y = subtitle_y + 18
    recommend_chip = (
        f"""
  <rect x="450" y="86" width="144" height="34" rx="17" fill="rgba(11, 18, 32, 0.40)" stroke="rgba(171,192,228,0.18)" />
  <text x="522" y="109" text-anchor="middle" font-size="16" font-weight="600" font-family="Microsoft YaHei, PingFang SC, Noto Sans SC, sans-serif" fill="{palette['warm']}">优先联系</text>
"""
        if recommended
        else ""
    )
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="640" height="640" viewBox="0 0 640 640" role="img" aria-label="{escape_xml(name)}">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="{palette['bg']}" />
      <stop offset="100%" stop-color="#07121f" />
    </linearGradient>
    <linearGradient id="coverShade" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="rgba(7,12,22,0.10)" />
      <stop offset="48%" stop-color="rgba(7,12,22,0.22)" />
      <stop offset="100%" stop-color="rgba(7,12,22,0.68)" />
    </linearGradient>
    <linearGradient id="panelGlow" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="rgba(255,255,255,0.12)" />
      <stop offset="100%" stop-color="rgba(255,255,255,0)" />
    </linearGradient>
    <clipPath id="frameClip">
      <rect x="20" y="20" width="600" height="600" rx="36" />
    </clipPath>
  </defs>
  <rect width="640" height="640" rx="44" fill="url(#bg)" />
  <g clip-path="url(#frameClip)">
    <image href="{background_href(kind)}" x="20" y="20" width="600" height="600" preserveAspectRatio="xMidYMid slice" />
    <rect x="20" y="20" width="600" height="600" fill="rgba(6, 12, 20, 0.12)" />
    <rect x="20" y="20" width="600" height="600" fill="url(#coverShade)" />
    <rect x="20" y="20" width="600" height="600" fill="{palette['glow']}" opacity="0.18" />
  </g>
  {ornament_svg(palette, rng)}
  <rect x="20" y="20" width="600" height="600" rx="36" fill="rgba(255,255,255,0.02)" stroke="rgba(180,206,236,0.18)" />
  <rect x="44" y="44" width="92" height="30" rx="15" fill="rgba(10,18,30,0.42)" stroke="rgba(171,192,228,0.14)" />
  <text x="90" y="64" text-anchor="middle" font-size="17" font-weight="700" font-family="Microsoft YaHei, PingFang SC, Noto Sans SC, sans-serif" fill="#eef7ff">{escape_xml(city_name)}</text>
  <rect x="484" y="44" width="110" height="30" rx="15" fill="rgba(10,18,30,0.40)" stroke="rgba(171,192,228,0.14)" />
  <text x="539" y="64" text-anchor="middle" font-size="15" font-weight="600" font-family="Microsoft YaHei, PingFang SC, Noto Sans SC, sans-serif" fill="{palette['accent_alt']}">{escape_xml(type_label)}</text>
  {recommend_chip}
  <rect x="40" y="426" width="560" height="168" rx="28" fill="rgba(7,12,22,0.58)" stroke="rgba(171,192,228,0.16)" />
  <rect x="56" y="444" width="140" height="4" rx="2" fill="{palette['accent_alt']}" opacity="0.94" />
  <rect x="56" y="456" width="212" height="28" rx="14" fill="rgba(255,255,255,0.08)" />
  <text x="162" y="475" text-anchor="middle" font-size="14" font-weight="600" font-family="Microsoft YaHei, PingFang SC, Noto Sans SC, sans-serif" fill="{palette['warm']}">心理中心支持入口</text>
  {title_svg}
  <text x="56" y="{subtitle_y}" font-size="20" font-weight="500" font-family="Microsoft YaHei, PingFang SC, Noto Sans SC, sans-serif" fill="#9bc6df">{escape_xml(district)} · {escape_xml(source_label)}</text>
  <rect x="56" y="{badge_y}" width="116" height="28" rx="14" fill="rgba(255,255,255,0.08)" />
  <text x="114" y="{badge_y + 19}" text-anchor="middle" font-size="14" font-weight="600" font-family="Microsoft YaHei, PingFang SC, Noto Sans SC, sans-serif" fill="#eef7ff">可联系机构</text>
  <rect x="56" y="{badge_y + 38}" width="220" height="2" rx="1" fill="url(#panelGlow)" />
</svg>
"""


def write_posters() -> None:
    seeds = json.loads(SEED_PATH.read_text(encoding="utf-8"))
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    for item in seeds:
        svg = make_svg(item)
        (ASSET_DIR / f"{item['seedKey']}.svg").write_text(svg, encoding="utf-8")


def write_fallback() -> None:
    palette = CITY_PALETTES["default"]
    fallback_svg = f"""<svg xmlns="http://www.w3.org/2000/svg" width="640" height="640" viewBox="0 0 640 640" role="img" aria-label="心理中心封面占位图">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="{palette['bg']}" />
      <stop offset="100%" stop-color="#07121f" />
    </linearGradient>
    <linearGradient id="shade" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="rgba(7,12,22,0.12)" />
      <stop offset="100%" stop-color="rgba(7,12,22,0.64)" />
    </linearGradient>
  </defs>
  <rect width="640" height="640" rx="44" fill="url(#bg)" />
  <g clip-path="url(#frame)">
    <image href="/assets/psy-centers/anime/center-campus.png" x="20" y="20" width="600" height="600" preserveAspectRatio="xMidYMid slice" />
    <rect x="20" y="20" width="600" height="600" fill="url(#shade)" />
  </g>
  <defs>
    <clipPath id="frame">
      <rect x="20" y="20" width="600" height="600" rx="36" />
    </clipPath>
  </defs>
  <rect x="20" y="20" width="600" height="600" rx="36" fill="rgba(255,255,255,0.02)" stroke="rgba(180,206,236,0.18)" />
  <rect x="40" y="438" width="560" height="148" rx="28" fill="rgba(7,12,22,0.58)" stroke="rgba(171,192,228,0.16)" />
  <rect x="56" y="456" width="140" height="4" rx="2" fill="{palette['accent_alt']}" opacity="0.94" />
  <text x="56" y="500" font-size="40" font-weight="700" font-family="Microsoft YaHei, PingFang SC, Noto Sans SC, sans-serif" fill="#f5fbff">心理支持机构</text>
  <text x="56" y="542" font-size="20" font-weight="500" font-family="Microsoft YaHei, PingFang SC, Noto Sans SC, sans-serif" fill="#9bc6df">动漫海报封面缺失时的统一占位图</text>
</svg>
"""
    FALLBACK_PATH.write_text(fallback_svg, encoding="utf-8")


def ensure_backgrounds() -> None:
    missing = [filename for filename in set(ANIME_BACKGROUND_BY_KIND.values()) if not (ANIME_DIR / filename).exists()]
    if missing:
        joined = ", ".join(sorted(missing))
        raise FileNotFoundError(f"Missing anime background assets: {joined}")


def main() -> None:
    ensure_backgrounds()
    write_posters()
    write_fallback()
    count = len(list(ASSET_DIR.glob("seed_psy_*.svg")))
    print(f"generated {count} posters")


if __name__ == "__main__":
    main()
