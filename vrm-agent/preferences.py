"""Spotting a customer telling MEE what they like ("Tôi thích golf", "I love travelling").

A first-person statement of taste is the clearest thing a customer can say about themselves: they said it,
in their own words, on purpose. Asking them to tap [Ghi nhớ] before MEE keeps it only made the map stay
empty, and left it to the model to remember to call `remember` at all. So this is deterministic and runs on
the message itself, and what it finds is stored at once, with an undo on the card.

It is deliberately narrow. It must not fire on a question ("bạn có thích…?"), a negative ("tôi không
thích…"), a condition ("nếu tôi thích…"), or something about today ("hôm nay tôi thích món này") — the
last is an EVENT in the BRD's terms and is not stored. When in doubt it finds nothing, and the model's own
`remember` tool is still there for the cases this does not cover.

Kept free of langchain and AgentBase imports so it can be tested without the agent's runtime.
"""

import re
import unicodedata
from dataclasses import dataclass
from typing import Optional

# "tôi (rất) thích X" — no negation word is allowed between the speaker and the verb, which is what
# keeps "tôi không thích X" and "tôi cũng không thích X" from matching.
_ADVERBS = r"(?:(?:rất|khá|cực kỳ|đặc biệt|luôn|luôn luôn|cũng|hay|thực sự|vẫn|thật sự|đều|khá là|siêu)\s+)*"
_VI = re.compile(
    rf"\b(?:tôi|mình|tui|tớ|em|tao)\s+{_ADVERBS}(?:thích|yêu thích|mê|ưa thích|ưa|khoái)\s+(?P<obj>.+)",
    re.IGNORECASE,
)
_VI_HOBBY = re.compile(
    r"\bsở thích (?:của (?:tôi|mình|em|tui) )?là\s+(?P<obj>.+)", re.IGNORECASE
)
_EN = re.compile(
    r"\bi\s+(?:(?:really|also|absolutely|just|do|truly|totally|very much)\s+)*(?:like|love|enjoy|prefer|adore)\s+(?P<obj>.+)",
    re.IGNORECASE,
)

# Before the sentence: a condition, or asking about it.
_NOT_A_STATEMENT = re.compile(
    r"\b(nếu|giả sử|giá như|khi nào|bao giờ|liệu|hỏi|if|suppose|whether|when)\b", re.IGNORECASE
)
# Anywhere in the clause: it is about now or today, not about who the customer is.
_TEMPORARY = re.compile(
    r"\b(hôm nay|hôm qua|tối nay|sáng nay|chiều nay|lúc nãy|vừa (?:mới )?|bây giờ|hiện tại|ngay lúc này|"
    r"today|tonight|yesterday|right now|just now|at the moment)\b",
    re.IGNORECASE,
)
# Where the thing liked ends and the rest of the sentence begins.
_CLAUSE_END = re.compile(
    r"\s*(?:[,.;!?\n]|\s+(?:vì|bởi vì|do|nhưng|mà|nên|và (?:tôi|mình|em)|rồi|tuy|because|but|so|and i)\b)",
    re.IGNORECASE,
)
_TRAILING_FILLER = re.compile(
    r"\s+(?:lắm|quá|ghê|nhé|nha|nhỉ|đấy|đó|thôi|cực|vô cùng|luôn|cả|ạ|đâu|nè|nghen|very much|a lot|too)$",
    re.IGNORECASE,
)
# Not a thing: pronouns and demonstratives, which say nothing about the customer once stored.
_EMPTY_OBJECTS = {
    "nó", "điều này", "điều đó", "cái này", "cái đó", "cách này", "bạn", "mee", "mi", "it", "this",
    "that", "you", "them", "vậy", "thế", "như vậy", "như thế",
}
_EMPTY_OBJECTS |= {"điều", "cái", "thứ", "chuyện này", "chuyện đó"}
_PRODUCT_WORDS = re.compile(
    r"tiết kiệm|thẻ|vay|lãi|kỳ hạn|ngân hàng|chuyển (?:tiền|khoản)|ứng dụng|\bapp\b|đầu tư|bảo hiểm|"
    r"savings?|\bcard\b|loan|invest|deposit|banking|interest",
    re.IGNORECASE,
)

MAX_WORDS = 8
MAX_CHARS = 80


@dataclass(frozen=True)
class Preference:
    """What the customer said they like, ready to hand to memory-hook."""

    obj: str  # as they wrote it: "chơi golf"
    fact: str  # the record's text: "Thích chơi golf"
    card_text: str  # what the customer is shown: "Bạn thích chơi golf"
    entity: str  # short, lowercase: "chơi golf"
    kind: str  # PREFERENCE or PRODUCT_PREFERENCE
    english: bool


def _fold(text: str) -> str:
    decomposed = unicodedata.normalize("NFD", text.lower().replace("đ", "d"))
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


_EMPTY_FOLDED = {_fold(word) for word in _EMPTY_OBJECTS}


def _object_of(raw: str) -> Optional[str]:
    """The thing liked: cut where the clause ends, trimmed, and refused when it is empty or a pronoun."""
    obj = _CLAUSE_END.split(raw, maxsplit=1)[0].strip().strip("\"'“”‘’()[]").strip()
    # Checked before the filler comes off as well as after: "điều đó" loses its "đó" to the filler rule
    # and would otherwise be stored as "điều".
    if _is_empty(obj):
        return None
    previous = None
    while previous != obj:
        previous = obj
        obj = _TRAILING_FILLER.sub("", obj).strip()
    if not obj or len(obj) < 2 or len(obj) > MAX_CHARS or len(obj.split()) > MAX_WORDS:
        return None
    if _is_empty(obj):
        return None
    return obj


def _is_empty(obj: str) -> bool:
    return _fold(obj) in _EMPTY_FOLDED


def extract_preference(text: str) -> Optional[Preference]:
    """The first statement of taste in `text`, or None when it is a question, a negative, a condition,
    about today, or about nothing in particular."""
    if not text:
        return None
    message = " ".join(str(text).split())
    if message.endswith("?") or "?" in message:
        return None

    for pattern, english in ((_VI, False), (_VI_HOBBY, False), (_EN, True)):
        found = pattern.search(message)
        if not found:
            continue
        before = message[: found.start()]
        if _NOT_A_STATEMENT.search(before):
            continue
        obj = _object_of(found.group("obj"))
        if obj is None:
            continue
        clause = message[found.start() : found.end()]
        if _TEMPORARY.search(clause) or _TEMPORARY.search(before):
            return None
        entity = " ".join(obj.lower().split()[:4])[:64]
        kind = "PRODUCT_PREFERENCE" if _PRODUCT_WORDS.search(obj) else "PREFERENCE"
        if english:
            return Preference(obj, f"Likes {obj}", f"You like {obj}", entity, kind, True)
        return Preference(obj, f"Thích {obj}", f"Bạn thích {obj}", entity, kind, False)
    return None
