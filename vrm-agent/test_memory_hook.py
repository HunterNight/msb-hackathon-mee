"""The memory-hook client, against a stand-in server. `python3 test_memory_hook.py` or pytest."""
import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import memory_hook as mh

CUSTOMER = "11111111-2222-3333-4444-555555555555"


class Fake(BaseHTTPRequestHandler):
    calls = []
    decision = "CANDIDATE"
    confirm_status = 200

    def log_message(self, *a):
        pass

    def _send(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(length)) if length else None
        Fake.calls.append((self.path, body))
        if self.path.endswith("/candidates"):
            if "NOCONSENT" in self.path:
                return self._send(403, {"code": "MH-001"})
            record = {"id": "rec-1", "state": "CANDIDATE", "text": body["text"]} if Fake.decision == "CANDIDATE" else None
            return self._send(200, {"data": {"decision": Fake.decision, "record": record}})
        if self.path.endswith("/confirm"):
            if Fake.confirm_status != 200:
                return self._send(Fake.confirm_status, {"code": "X"})
            return self._send(200, {"data": {"id": "rec-1", "state": "CONFIRMED"}})
        self._send(404, {})


server = HTTPServer(("127.0.0.1", 0), Fake)
threading.Thread(target=server.serve_forever, daemon=True).start()
BASE = f"http://127.0.0.1:{server.server_port}"
TOKEN = lambda: "T"  # noqa: E731


def reset(decision="CANDIDATE", confirm_status=200):
    Fake.calls.clear()
    Fake.decision = decision
    Fake.confirm_status = confirm_status


def test_a_preference_is_saved_and_confirmed_in_one_step():
    reset()
    out = mh.remember_now(BASE, TOKEN, CUSTOMER, "Thích golf", kind="PREFERENCE", entity="golf")
    assert out["confirmed"] is True and out["record"]["state"] == "CONFIRMED"
    assert [p.rsplit("/", 2)[-1] if p.endswith("confirm") else p.rsplit("/", 1)[-1] for p, _ in Fake.calls] == ["candidates", "confirm"]
    assert Fake.calls[1][0] == f"/internal/memory/{CUSTOMER}/records/rec-1/confirm"


def test_goals_plans_and_worries_still_wait_for_the_customer():
    for kind in ("GOAL", "PLAN", "CONCERN", "HABIT"):
        reset()
        out = mh.remember_now(BASE, TOKEN, CUSTOMER, "Muốn mua nhà", kind=kind)
        assert out["decision"] == "CANDIDATE" and out["confirmed"] is False, kind
        assert len(Fake.calls) == 1, kind  # submitted, never confirmed


def test_nothing_is_confirmed_when_memory_hook_did_not_store_it():
    for decision in ("DONT_STORE", "UPDATE_OF:abc"):
        reset(decision=decision)
        out = mh.remember_now(BASE, TOKEN, CUSTOMER, "Thích golf", kind="PREFERENCE")
        assert out["confirmed"] is False, decision
        assert len(Fake.calls) == 1, decision


def test_a_failed_confirm_leaves_a_candidate_instead_of_raising():
    reset(confirm_status=500)
    out = mh.remember_now(BASE, TOKEN, CUSTOMER, "Thích golf", kind="PREFERENCE")
    assert out["decision"] == "CANDIDATE" and out["confirmed"] is False


def test_consent_off_is_reported_not_swallowed():
    reset()
    try:
        mh.remember_now(BASE + "/NOCONSENT", TOKEN, CUSTOMER, "Thích golf")
        assert False, "should raise"
    except mh.MemoryHookError as e:
        assert e.code == "NO_CONSENT"


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn(); print("ok", name)
