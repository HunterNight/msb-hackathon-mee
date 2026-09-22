"""Whether a turn is the agent's business at all.

In standard mode the agent answers banking and personal-finance questions, and anything else is
handed back so mi-assistant can deal with it: mi is the bank's own assistant, with the guardrails,
the refusal copy and the tested behaviour, so it is the right place for "who won the match last
night" to land.

The bias there is deliberately conservative. A banking question wrongly sent to mi costs nothing —
mi answers it, that is its job. An off-topic question wrongly kept by the agent means a bank's
assistant freelancing on a subject nobody signed off, in the bank's voice. So when the marker list
and the model disagree, in-scope needs positive evidence.

**Advance mode moves that line on purpose.** With the toggle on, MEE is the customer's personal
assistant rather than strictly the bank's: travel, food and drink, health, home, work and the rest
of everyday life are in scope, because that is what the product owner asked the mode to be. The
carve-outs are `BLOCKED_TOPIC_PATTERNS` — violence, gore and gambling — which are declined in every
mode, and the money-flow and own-data hand-offs below, which still belong to mi-assistant whatever
mode is on.

Kept free of langchain and AgentBase imports so it can be tested without the agent's runtime.
"""

import re
import unicodedata


def _fold(text: str) -> str:
    """Lowercase and strip Vietnamese diacritics, so "Mở sổ" and "mo so" match the same pattern."""
    decomposed = unicodedata.normalize("NFD", str(text).lower().replace("đ", "d"))
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


# Terms that make a turn unambiguously about money at this bank. Vietnamese first, since that is the
# product locale, then the English a bilingual customer might use. Matched as substrings on a
# lowercased message, so inflections and compounds are covered without a word list per form.
FINANCE_MARKERS = (
    # accounts and balances
    "tài khoản", "số dư", "sao kê", "biến động", "giao dịch", "chuyển tiền", "chuyển khoản",
    "nhận tiền", "rút tiền", "nạp tiền", "số tiền", "tiền", "đồng", "vnd", "vnđ",
    # cards
    "thẻ", "tín dụng", "ghi nợ", "napas", "visa", "mastercard", "hạn mức", "khoá thẻ", "khóa thẻ",
    "mở thẻ", "sao kê thẻ", "dư nợ",
    # loans
    "vay", "khoản vay", "trả nợ", "trả trước", "kỳ hạn", "lãi", "lãi suất", "gốc", "thế chấp",
    "tín chấp", "trả góp", "dti", "ltv",
    # savings and goals
    "tiết kiệm", "gửi tiền", "sổ tiết kiệm", "mục tiêu", "đáo hạn", "tất toán",
    # payments and bills
    "hoá đơn", "hóa đơn", "thanh toán", "điện nước", "internet", "học phí", "nạp thẻ", "qr",
    # products, fees, policy
    "phí", "biểu phí", "thường niên", "ưu đãi", "hoàn tiền", "điểm thưởng", "tỷ giá", "ngoại tệ",
    "bảo hiểm", "đầu tư", "chứng khoán", "quỹ", "ngân hàng", "msb", "atm", "chi nhánh",
    "internet banking", "mobile banking", "otp", "pin", "mật khẩu", "đăng nhập", "sinh trắc",
    "hạn mức chuyển", "định danh", "ekyc",
    # general personal-finance knowledge: still banking's business, and no longer handed back to mi
    "tài chính", "lạm phát", "lãi kép", "cổ phiếu", "trái phiếu", "vàng", "điểm tín dụng",
    "nợ xấu", "ngân sách", "chi tiêu", "thu nhập", "quỹ khẩn cấp", "hưu trí", "thuế", "lương",
    "tài sản", "dòng tiền", "lừa đảo", "phishing", "tiền điện tử",
    # English
    "account", "balance", "statement", "transfer", "card", "credit", "debit", "loan", "mortgage",
    "interest", "rate", "saving", "deposit", "payment", "bill", "fee", "invest", "insurance",
    "exchange", "bank", "money", "withdraw", "instalment", "installment",
    "finance", "inflation", "budget", "retirement", "credit score", "income",
)

_MARKER_RE = re.compile("|".join(re.escape(m) for m in FINANCE_MARKERS), re.IGNORECASE)

# What the router is allowed to answer. OTHER is the escape hatch that sends a turn back to mi.
DOMAIN_NAMES = ("general", "loan", "card", "saving", "payment")
OUT_OF_SCOPE = "other"


def has_finance_marker(text: str) -> bool:
    """Whether the message names something financial outright.

    Used two ways: as evidence a turn is in scope, and as a veto on the model classifying a turn as
    off-topic when the customer plainly asked about their money.
    """
    if not text:
        return False
    return bool(_MARKER_RE.search(str(text).lower()))


def resolve_domain(model_answer: str, message: str) -> str:
    """The domain to handle a turn, or {@code OUT_OF_SCOPE} to hand it back to mi.

    `model_answer` is whatever the router model said, which is not to be trusted as a bare value: it
    has been observed answering with JSON when asked for a word, with the word capitalised, and with a
    sentence around it. So the domain is recovered by looking for a known name inside the answer.

    Two guards around the model's judgement:

    - It says off-topic but the message names money -> in scope, as `general`. That agent still has
      knowledge search, so a customer asking "phí thường niên thẻ" gets an answer rather than being
      turned away because a small model had an opinion.
    - It says `general`, the catch-all, without the message naming money -> out of scope. GENERAL was
      the verdict on "write me a python function".
    - It says nothing recognisable -> fall back on the markers rather than on `general`. Defaulting to
      `general`, which is what this did before, is what let an off-topic question through: every
      unrecognised answer became a domain the agent happily answered in.
    """
    answer = (model_answer or "").strip().lower()
    marker = has_finance_marker(message)

    named = [d for d in DOMAIN_NAMES if d in answer]
    specific = [d for d in named if d != "general"]
    if specific:
        # Naming loan, card, saving or payment is a commitment to a financial subject, so it is taken
        # at face value.
        return specific[0]
    if named:
        # `general` is the catch-all, and on its own it is not evidence of anything: asked to classify
        # "write me a python function" the model answered GENERAL, which would have had the agent
        # writing code in the bank's voice. It needs the message to actually name money.
        return "general" if marker else OUT_OF_SCOPE

    if OUT_OF_SCOPE in answer or "off-topic" in answer or "ngoài" in answer:
        return "general" if marker else OUT_OF_SCOPE

    # Unrecognisable answer: the markers decide, and no marker means no answer.
    return "general" if marker else OUT_OF_SCOPE


# A customer asking for money to move, or an account to be opened. The agent's own tools for these are
# mocks and the app never renders an agent proposal, so the customer only got instructions back
# ("how to open a saving") where a transfer got a real confirm card. mi-assistant owns the real tools,
# the proposal card and the step-up, so these turns are handed to it.
_ACTION_RE = re.compile(
    r"\b(mo|tao|dang ky)\b.{0,20}\b(so tiet kiem|tai khoan tiet kiem|so gui|tiet kiem)\b"
    r"|\bgui\b.{0,40}\btiet kiem\b|\bgui tiet kiem\b"
    r"|\bnap\b.{0,30}\bmuc tieu\b|\btang muc tieu\b"
    r"|\bchuyen (tien|khoan)\b|\bchuyen\b.{0,30}\b(cho|den|toi|sang)\b"
    r"|\b(thanh toan|tra|dong)\b.{0,20}\b(hoa don|tien dien|tien nuoc|internet|the|du no)\b"
)
# Questions ABOUT an action rather than a request for it stay with the agent: it explains those well.
_INFO_RE = re.compile(
    r"nhu the nao|lam sao|lam the nao|\bcach\b|huong dan|quy trinh|dieu kien|la gi\b|\bphi\b"
    r"|han muc|lai suat|bao nhieu|tai sao|\bnen\b|co duoc khong|can (gi|giay to)"
)


def is_action_request(text: str) -> bool:
    """Whether the message asks for a transfer, payment, deposit or account opening to be done."""
    folded = _fold(text)
    return bool(_ACTION_RE.search(folded)) and not _INFO_RE.search(folded)


# ── a transfer that is being filled in over several messages ──────────────────────────────────────
#
# "Tôi muốn chuyển tiền" is handed to mi-assistant, which asks for the bank, account and amount. The
# customer's next message ("mab 0123455", "6 triệu", "cho mẹ") says nothing about transferring, so on
# its own it looks like a stray finance remark and this agent would answer it. While an exchange is
# open, or when the message plainly is transfer details, it goes to mi-assistant too.

_BANK_WORD_RE = re.compile(
    r"\b(msb|vcb|tcb|bidv|vpb|acb|agr|maritime|vietcombank|techcombank|vpbank|agribank)\b"
)
# 9+ digits, or 6+ starting with 0, that are not an amount written with a unit ("500000 k").
_ACCOUNT_LIKE_RE = re.compile(
    r"(?<![\d.,])(?:\d{9,19}|0\d{5,18})(?![\d.,]*\d)(?!\s*(?:tr|trieu|ty|ti|k|m|nghin|ngan|d|dong|vnd)\b)"
)
_OPEN_EXCHANGE_SECONDS = 300
_OPEN_EXCHANGE_TURNS = 5
_SHORT_REPLY_WORDS = 12


def is_transfer_details(text: str) -> bool:
    """A message that gives the destination of a transfer whatever else it does or does not say."""
    folded = _fold(text)
    if _ACCOUNT_LIKE_RE.search(folded):
        return True
    # A bank's name alone is not a destination ("lãi suất gửi 12 tháng ở MSB" is a rates question, MSB is this
    # bank and 12 is months): it takes an account-sized number, or the word for an account, next to it.
    return bool(_BANK_WORD_RE.search(folded)) and bool(re.search(r"\d{6,}|\btk\b|\bstk\b|tai khoan", folded))


def is_reply_in_open_exchange(text: str) -> bool:
    """A short statement rather than a question: what a customer says to answer "which bank?"."""
    if not text or "?" in text:
        return False
    folded = _fold(text)
    return len(folded.split()) <= _SHORT_REPLY_WORDS and not _INFO_RE.search(folded)


def route_to_mi(text: str, session: dict, now: float) -> bool:
    """Whether this turn belongs to mi-assistant's money flow, updating `session` as it goes.

    `session` remembers that a transfer exchange is open (for five minutes and five messages), so a
    reply that only carries a detail is not mistaken for a general finance question.
    """
    is_open = session.get("exchange_until", 0) > now and session.get("exchange_left", 0) > 0
    if is_own_data_request(text):
        return True
    if is_action_request(text):
        session["exchange_until"] = now + _OPEN_EXCHANGE_SECONDS
        session["exchange_left"] = _OPEN_EXCHANGE_TURNS
        return True
    if is_transfer_details(text) or (is_open and is_reply_in_open_exchange(text)):
        session["exchange_until"] = now + _OPEN_EXCHANGE_SECONDS
        session["exchange_left"] = (session.get("exchange_left", 0) if is_open else _OPEN_EXCHANGE_TURNS) - 1
        return True
    return False


# ── questions about the customer's OWN data ───────────────────────────────────────────────────────
#
# The agent has no access to a customer's accounts. Its old tools for them returned fixed sample data,
# so "số dư" was answered with a balance that belonged to nobody. mi-assistant reads the real
# accounts, cards and loans, so those turns go there. Explaining a concept ("hạn mức thẻ là gì") stays
# with the agent.

_HOWTO_RE = re.compile(
    r"nhu the nao|lam sao|lam the nao|\bcach\b|huong dan|quy trinh|la gi\b|tai sao|dieu kien|"
    r"can (gi|giay to)|co nen|nen (gui|vay|mua|dau tu)|so sanh"
)
_OWN_DATA_RE = re.compile(
    r"\bso du\b|\bsao ke\b|lich su giao dich|giao dich gan day|"
    r"toi (con|co) bao nhieu tien|\bthang nay (toi )?(chi|tieu)\b|chi tieu (thang nay|cua toi)|toi (da )?(chi|tieu) bao nhieu|"
    r"(the|khoan vay|du no|han muc|tiet kiem|tai khoan|hoa don|muc tieu|so gui|giao dich)( tin dung| ghi no)? (cua toi|cua minh)|"
    r"(cua toi|cua minh|toi dang|toi con).{0,30}(the|khoan vay|du no|han muc|tiet kiem|tai khoan|hoa don|muc tieu)|"
    r"(hoa don|khoan vay|the).{0,20}(sap den han|den han|phai tra)|toi co khoan vay"
)


def is_own_data_request(text: str) -> bool:
    """A question whose answer is the customer's own balance, spending, cards, loans or bills."""
    folded = _fold(text)
    return bool(_OWN_DATA_RE.search(folded)) and not _HOWTO_RE.search(folded)


# ── refusals ─────────────────────────────────────────────────────────────────────────────────────

# What is refused is doing harm, not asking about it: "tôi quên mật khẩu" and "làm sao nhận biết lừa đảo" are
# ordinary customer questions and used to get a refusal because they contain the words below. Credentials
# are only off limits when the ask is to obtain, guess or bypass someone's.
# Subjects MEE declines whatever mode it is in. Advance mode opens the agent up to lifestyle
# questions (travel, food, health, and the rest of daily life), and these are the three the product
# owner carved back out of that: violence and gore, and gambling.
#
# Matched on the diacritic-folded message, so "cờ bạc" and "co bac" are the same string. Deliberately
# narrow: "đánh giá" must not match the word for hitting, an "đầu tư" question is not gambling, and
# self-harm is not on this list — a canned "MEE does not support that" is the wrong answer to it, and
# the model's own safety handles it better than a keyword ever will.
BLOCKED_TOPIC_PATTERNS = re.compile(
    # violence and weapons
    r"\bbao luc\b|\bgiet (nguoi|hai)\b|danh nhau|hanh hung|tra tan|khung bo|"
    r"\bvu khi\b|che tao (sung|bom|thuoc no)|\bbom (tu che|xang)\b|dam chet|"
    # gore
    r"mau me|ghe ron|chat dau|xac chet|"
    # gambling
    r"co bac|danh bac|\bca do\b|\bca cuoc\b|\blo de\b|\bde online\b|casino|"
    r"ca do bong da|game bai doi thuong|ta xiu|"
    # English
    r"\bviolence\b|\bgore\b|\btorture\b|\bweapon\b|\bgambling\b|\bcasino\b|\bbetting\b|\bbookmaker\b"
)


def is_blocked_topic(text: str) -> bool:
    """Whether the turn is about violence, gore or gambling — declined in every mode."""
    if not text:
        return False
    return bool(BLOCKED_TOPIC_PATTERNS.search(_fold(text)))


# Everyday subjects a personal assistant is expected to cover. Only consulted in advance mode, where
# MEE is a personal assistant rather than strictly a bank's; in standard mode the finance markers
# above still decide on their own.
LIFESTYLE_MARKERS = (
    # travel
    "du lich", "chuyen di", "ve may bay", "khach san", "resort", "tour", "visa", "ho chieu",
    "di choi", "dia diem", "lich trinh", "phuot", "bien", "da lat", "sapa", "nhat ban", "han quoc",
    # food and drink
    "an gi", "mon an", "nau", "cong thuc", "nha hang", "quan an", "ca phe", "tra sua", "do uong",
    "am thuc", "bua sang", "bua trua", "bua toi", "an uong", "thuc don",
    # health and wellness
    "suc khoe", "the duc", "tap gym", "yoga", "chay bo", "giam can", "dinh duong", "giac ngu",
    "ngu ngon", "stress", "thu gian", "nghi ngoi", "van dong", "cham soc",
    # home, family, work, learning, leisure
    "gia dinh", "con cai", "nha cua", "don dep", "mua sam", "qua tang", "mua qua", "tang qua",
    "sinh nhat", "ky nang", "hoc tieng",
    "sach", "phim", "am nhac", "so thich", "ke hoach", "thoi gian", "cong viec", "nghe nghiep",
    "thoi trang", "lam dep",
    # English
    "travel", "trip", "flight", "hotel", "restaurant", "recipe", "cook", "food", "drink", "coffee",
    "health", "fitness", "workout", "diet", "sleep", "wellness", "lifestyle", "hobby", "family",
    "shopping", "gift", "book", "movie", "music", "study", "career",
)

_LIFESTYLE_RE = re.compile("|".join(re.escape(m) for m in LIFESTYLE_MARKERS), re.IGNORECASE)


def has_lifestyle_marker(text: str) -> bool:
    """Whether the message is about everyday life — advance mode's widened subject area."""
    if not text:
        return False
    return bool(_LIFESTYLE_RE.search(_fold(text)))


ILLEGAL_PATTERNS = re.compile(
    r"(rửa tiền|trốn thuế|\bhack\b|chiếm đoạt|"
    r"(lấy|đoán|dò|bẻ khoá|bẻ khóa|vượt|bypass|qua mặt|xem trộm|đánh cắp).{0,25}(otp|mã pin|\bpin\b|mật khẩu)|"
    r"(otp|mã pin|mật khẩu).{0,20}(của (người khác|anh|chị|vợ|chồng|bạn|sếp))|"
    r"cách (lừa đảo|gian lận|rửa tiền))",
    re.IGNORECASE)
