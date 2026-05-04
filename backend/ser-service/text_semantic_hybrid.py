from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


EMOTION4_LABELS = ("ANG", "HAP", "NEU", "SAD")


@dataclass(frozen=True)
class TermHit:
    term: str
    start: int
    end: int
    group: str


def _load_terms(path: Path, fallback: tuple[str, ...]) -> tuple[str, ...]:
    if not path.exists():
        return fallback
    terms = []
    for line in path.read_text(encoding="utf-8").splitlines():
        value = line.strip()
        if not value or value.startswith("#"):
            continue
        terms.append(value)
    return tuple(terms) if terms else fallback


def _normalize_language(language_hint: str | None) -> str | None:
    if not language_hint:
        return None
    value = language_hint.strip().lower()
    if value.startswith("zh"):
        return "zh"
    if value.startswith("en"):
        return "en"
    return None


def _collect_non_overlapping_hits(text: str, grouped_terms: list[tuple[str, str]]) -> list[TermHit]:
    occupied = [False] * len(text)
    hits: list[TermHit] = []
    for group, term in sorted(grouped_terms, key=lambda item: (-len(item[1]), item[1])):
        if not term:
            continue
        for match in re.finditer(re.escape(term), text):
            start, end = match.span()
            if any(occupied[index] for index in range(start, end)):
                continue
            for index in range(start, end):
                occupied[index] = True
            hits.append(TermHit(term=term, start=start, end=end, group=group))
    hits.sort(key=lambda item: item.start)
    return hits


class ZhSemanticHybridScorer:
    def __init__(
        self,
        positive_terms: tuple[str, ...],
        anger_terms: tuple[str, ...],
        sad_terms: tuple[str, ...],
        negative_terms: tuple[str, ...],
        neutral_cues: tuple[str, ...],
    ) -> None:
        self.positive_terms = positive_terms
        self.anger_terms = anger_terms
        self.sad_terms = sad_terms
        self.negative_terms = negative_terms
        self.neutral_cues = neutral_cues

    @classmethod
    def from_lexicon_dir(cls, lexicon_dir: Path) -> "ZhSemanticHybridScorer":
        return cls(
            positive_terms=_load_terms(
                lexicon_dir / "text_positive_zh.txt",
                (
                    "开心",
                    "高兴",
                    "快乐",
                    "愉快",
                    "喜悦",
                    "幸福",
                    "真好",
                    "很好",
                    "挺好",
                    "不错",
                    "舒服",
                    "放松",
                    "满足",
                    "喜欢",
                    "太阳非常好",
                    "心情很好",
                    "散步",
                    "休息得很好",
                    "休息的很好",
                ),
            ),
            anger_terms=_load_terms(
                lexicon_dir / "text_anger_zh.txt",
                (
                    "愤怒",
                    "生气",
                    "气愤",
                    "恼火",
                    "火大",
                    "暴躁",
                    "发火",
                    "气死",
                    "怒",
                ),
            ),
            sad_terms=_load_terms(
                lexicon_dir / "text_sad_zh.txt",
                (
                    "难过",
                    "伤心",
                    "悲伤",
                    "想哭",
                    "沮丧",
                    "低落",
                    "失落",
                    "难受",
                    "委屈",
                    "郁闷",
                    "不开心",
                ),
            ),
            negative_terms=_load_terms(
                lexicon_dir / "text_negative_zh.txt",
                (
                    "不开心",
                    "痛苦",
                    "压力",
                    "焦虑",
                    "绝望",
                    "抑郁",
                    "崩溃",
                    "烦躁",
                    "烦",
                    "恐惧",
                    "害怕",
                    "担心",
                    "失眠",
                ),
            ),
            neutral_cues=_load_terms(
                lexicon_dir / "text_neutral_cues_zh.txt",
                (
                    "是什么情绪",
                    "什么情绪",
                    "能听见",
                    "测试",
                    "录音",
                    "请问",
                    "吗",
                    "呢",
                ),
            ),
        )

    def _normalize_scores(self, values: dict[str, float], labels: Iterable[str]) -> dict[str, float]:
        clipped = {label: max(0.0, float(values.get(label, 0.0))) for label in labels}
        total = float(sum(clipped.values()))
        if total <= 0.0:
            return {label: (1.0 if label == "NEU" else 0.0) for label in labels}
        return {label: float(clipped[label] / total) for label in labels}

    def score(self, text: str) -> dict:
        payload = (text or "").strip()
        if not payload:
            return {
                "label": "neutral",
                "negativeScore": 0.0,
                "scores": {"negative": 0.0, "neutral": 1.0, "positive": 0.0},
                "emotion4Ready": True,
                "emotion4Scores": {"ANG": 0.0, "HAP": 0.0, "NEU": 1.0, "SAD": 0.0},
                "mappedMass": 1.0,
                "emotion4Label": "NEU",
                "emotion4Confidence": 1.0,
                "topLabelRaw": "LEXICON_NEU",
                "topConfidenceRaw": 1.0,
                "rawScores": {"LEXICON_NEU": 1.0},
                "hits": [],
                "neutralCueHits": [],
            }

        grouped_terms: list[tuple[str, str]] = []
        grouped_terms.extend(("HAP", term) for term in self.positive_terms)
        grouped_terms.extend(("ANG", term) for term in self.anger_terms)
        grouped_terms.extend(("SAD", term) for term in self.sad_terms)
        grouped_terms.extend(("NEG", term) for term in self.negative_terms)
        hits = _collect_non_overlapping_hits(payload, grouped_terms)

        positive_hits = [hit.term for hit in hits if hit.group == "HAP"]
        anger_hits = [hit.term for hit in hits if hit.group == "ANG"]
        sad_hits = [hit.term for hit in hits if hit.group == "SAD"]
        negative_hits = [hit.term for hit in hits if hit.group == "NEG"]
        neutral_cue_hits = [term for term in self.neutral_cues if term and term in payload]

        positive_raw = float(len(positive_hits))
        anger_raw = float(len(anger_hits))
        sad_raw = float(len(sad_hits))
        negative_general_raw = float(len(negative_hits))

        emotion4_raw = {
            "ANG": anger_raw + negative_general_raw * 0.35,
            "HAP": positive_raw,
            "NEU": 1.2 if (positive_raw + anger_raw + sad_raw + negative_general_raw) <= 0.0 else (0.45 if neutral_cue_hits else 0.2),
            "SAD": sad_raw + negative_general_raw * 0.55,
        }
        emotion4_scores = self._normalize_scores(emotion4_raw, EMOTION4_LABELS)

        sentiment_raw = {
            "negative": anger_raw + sad_raw + negative_general_raw,
            "neutral": 1.3 if (positive_raw + anger_raw + sad_raw + negative_general_raw) <= 0.0 else (0.5 if neutral_cue_hits else 0.2),
            "positive": positive_raw,
        }
        sentiment_scores = self._normalize_scores(sentiment_raw, ("negative", "neutral", "positive"))

        emotion4_label = max(emotion4_scores, key=emotion4_scores.get)
        sentiment_label = max(sentiment_scores, key=sentiment_scores.get)
        emotion4_confidence = float(emotion4_scores[emotion4_label])

        return {
            "label": sentiment_label,
            "negativeScore": float(sentiment_scores["negative"]),
            "scores": sentiment_scores,
            "emotion4Ready": True,
            "emotion4Scores": emotion4_scores,
            "mappedMass": 1.0,
            "emotion4Label": emotion4_label,
            "emotion4Confidence": emotion4_confidence,
            "topLabelRaw": f"LEXICON_{emotion4_label}",
            "topConfidenceRaw": emotion4_confidence,
            "rawScores": {
                "LEXICON_ANG": float(emotion4_scores["ANG"]),
                "LEXICON_HAP": float(emotion4_scores["HAP"]),
                "LEXICON_NEU": float(emotion4_scores["NEU"]),
                "LEXICON_SAD": float(emotion4_scores["SAD"]),
            },
            "hits": {
                "positive": positive_hits,
                "anger": anger_hits,
                "sad": sad_hits,
                "negative": negative_hits,
            },
            "neutralCueHits": neutral_cue_hits,
        }


def build_zh_hybrid_scorer() -> ZhSemanticHybridScorer:
    lexicon_dir = Path(__file__).resolve().parent / "lexicon"
    return ZhSemanticHybridScorer.from_lexicon_dir(lexicon_dir)


def should_use_zh_hybrid(language_hint: str | None) -> bool:
    return _normalize_language(language_hint) == "zh"
