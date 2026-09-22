"""Client for memory-hook-service, the single owner of a customer's long-term memory.

The agent used to write remembered facts to AgentBase Memory. Nothing else reads that store: the
customer's Memory screen, the back-office panel, consent, erasure and the audit log all live in
memory-hook, so a fact the customer told MEE on the phone was remembered by the agent alone and never
showed anywhere (guideline 11 §13, "two memory stores diverge"). Now `remember` submits a *candidate*
to memory-hook, which decides, encrypts and stores it; the customer confirms it on the Memory screen
before it is ever recalled.

Kept free of langchain and AgentBase imports so it can be tested without the agent's runtime.
"""

import json
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Callable, Optional

# Kinds memory-hook accepts on POST /candidates. EVENT and TRANSACTION_CONTEXT are accepted so the model
# can say "this is a one-off" and memory-hook records the DONT_STORE decision deliberately.
KINDS = (
    "PREFERENCE", "LIFESTYLE", "GOAL", "PLAN", "CONCERN", "RELATIONSHIP", "PRODUCT_PREFERENCE",
    "NICKNAME", "HABIT", "FACT", "EVENT", "TRANSACTION_CONTEXT",
)
DEFAULT_KIND = "PREFERENCE"
TEXT_MAX = 200  # memory-hook's RECORD_TEXT_MAX

_NAMESPACE = uuid.UUID("6f0a2c1e-5b1d-4c53-9a1e-3d6e5f0b7a11")


class MemoryHookError(Exception):
    """A call that did not produce a usable answer. `code` is a stable, customer-safe reason."""

    def __init__(self, code: str, detail: str = ""):
        super().__init__(f"{code}: {detail}" if detail else code)
        self.code = code


def customer_uuid(actor_id: str) -> Optional[str]:
    """The customer id if the AgentBase actor is one.

    The gateway sets the actor from the verified JWT, so on the real path this is the customer's UUID.
    Anything else (a curl probe with `test-user`) has no memory-hook namespace and is refused rather
    than mapped to somebody.
    """
    try:
        return str(uuid.UUID(str(actor_id)))
    except (ValueError, AttributeError, TypeError):
        return None


def as_uuid(value: str) -> str:
    """A stable UUID for a session id that may or may not already be one."""
    try:
        return str(uuid.UUID(str(value)))
    except (ValueError, AttributeError, TypeError):
        return str(uuid.uuid5(_NAMESPACE, str(value or "anon")))


def _call(method: str, url: str, token: str, body: Optional[dict] = None, timeout: int = 8) -> dict:
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            payload = json.loads(resp.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", "replace")
        # MH-001 is memory-hook's "long-term memory consent is off"; a 403 without it is a scope problem.
        if e.code == 403 and ("MH-001" in text or "noConsent" in text):
            raise MemoryHookError("NO_CONSENT") from e
        if e.code in (401, 403):
            raise MemoryHookError("UNAUTHORIZED", f"{e.code} {text[:120]}") from e
        raise MemoryHookError("HTTP_ERROR", f"{e.code} {text[:120]}") from e
    except (urllib.error.URLError, TimeoutError, OSError, ValueError) as e:
        raise MemoryHookError("UNAVAILABLE", str(e)) from e
    return payload.get("data", payload) if isinstance(payload, dict) else {}


def submit_candidate(
    base_url: str,
    token: Callable[[], str],
    customer_id: str,
    text: str,
    kind: str = DEFAULT_KIND,
    entity: str = "",
    reason: str = "",
    conversation_id: str = "",
    message_id: Optional[str] = None,
) -> dict:
    """POST /internal/memory/{customer}/candidates. Returns `{"decision": ..., "record": ...}`.

    Decision is `CANDIDATE` (stored, awaiting the customer), `DONT_STORE` (a temporary event) or
    `UPDATE_OF:{id}` (the customer already confirmed this).
    """
    fact = (text or "").strip()
    if not fact:
        raise MemoryHookError("EMPTY")
    kind = (kind or DEFAULT_KIND).strip().upper()
    if kind not in KINDS:
        kind = DEFAULT_KIND
    body = {
        "kind": kind,
        "text": fact[:TEXT_MAX],
        "conversationId": as_uuid(conversation_id),
        "messageId": message_id or str(uuid.uuid4()),
    }
    if entity.strip():
        body["entity"] = entity.strip().lower()[:64]
    if reason.strip():
        body["reason"] = reason.strip()[:TEXT_MAX]
    url = f"{base_url.rstrip('/')}/internal/memory/{customer_id}/candidates"
    out = _call("POST", url, token(), body)
    return out if isinstance(out, dict) else {}


def recall(
    base_url: str, token: Callable[[], str], customer_id: str, question: str, k: int = 5
) -> list:
    """GET /internal/memory/{customer}/recall. Returns the remembered texts, best first.

    memory-hook returns confirmed records only, and nothing at all without consent, so an empty list is
    an answer, not an error.
    """
    query = urllib.parse.urlencode({"q": (question or "")[:500], "k": k})
    url = f"{base_url.rstrip('/')}/internal/memory/{customer_id}/recall?{query}"
    out = _call("GET", url, token())
    records = list(out.get("pinned") or []) if isinstance(out, dict) else []
    for match in (out.get("matches") or []) if isinstance(out, dict) else []:
        records.append(match.get("record") or {})
    texts, seen = [], set()
    for r in records:
        if r.get("state") not in (None, "CONFIRMED"):
            continue
        text = str(r.get("text") or "").strip()
        if text and text not in seen:
            seen.add(text)
            texts.append(text)
    return texts[:k]


# Kinds a customer states about themselves and that are safe to keep without a second tap: what they like,
# how they live. Goals, plans and worries stay candidates — those change what MEE suggests about money, so the
# customer is asked (guideline 11 §4, open question 2 answered for tastes only).
AUTO_CONFIRM_KINDS = ("PREFERENCE", "LIFESTYLE", "PRODUCT_PREFERENCE")


def confirm(base_url: str, token: Callable[[], str], customer_id: str, record_id: str) -> dict:
    """POST /internal/memory/{customer}/records/{id}/confirm: a CANDIDATE becomes CONFIRMED."""
    url = f"{base_url.rstrip('/')}/internal/memory/{customer_id}/records/{record_id}/confirm"
    out = _call("POST", url, token())
    return out if isinstance(out, dict) else {}


def remember_now(
    base_url: str,
    token: Callable[[], str],
    customer_id: str,
    text: str,
    kind: str = DEFAULT_KIND,
    entity: str = "",
    reason: str = "",
    conversation_id: str = "",
) -> dict:
    """Submit and, for a kind that needs no second tap, confirm in the same breath.

    Returns `{"decision": ..., "record": ..., "confirmed": bool}`. `confirmed` is False when memory-hook
    decided not to store it (DONT_STORE), already had it (UPDATE_OF), or the kind is one that waits for the
    customer. A failure to confirm leaves a candidate behind, which the customer can still confirm from the
    Memory screen, so it is reported rather than raised.
    """
    out = submit_candidate(base_url, token, customer_id, text, kind=kind, entity=entity, reason=reason,
                           conversation_id=conversation_id)
    result = {"decision": str(out.get("decision") or ""), "record": out.get("record") or {}, "confirmed": False}
    record_id = str(result["record"].get("id") or "")
    if result["decision"] == "CANDIDATE" and record_id and (kind or DEFAULT_KIND).upper() in AUTO_CONFIRM_KINDS:
        try:
            confirmed = confirm(base_url, token, customer_id, record_id)
            result["record"] = confirmed or result["record"]
            result["confirmed"] = True
        except MemoryHookError as e:
            print(f"[memory] auto-confirm failed, left as a candidate: {e}")
    return result
