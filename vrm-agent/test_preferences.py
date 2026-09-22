"""What counts as the customer telling MEE what they like. Runs without the agent's runtime:
`python3 -m pytest test_preferences.py` or `python3 test_preferences.py`."""
from preferences import extract_preference


def objs(text):
    p = extract_preference(text)
    return None if p is None else p.obj


def test_statements_of_taste_are_found():
    cases = {
        "Tôi thích golf": "golf",
        "tôi thích chơi golf": "chơi golf",
        "Mình rất thích đi du lịch Nhật Bản": "đi du lịch Nhật Bản",
        "Em cực kỳ yêu thích cà phê sữa đá": "cà phê sữa đá",
        "Tôi mê bóng đá lắm": "bóng đá",
        "Tui khoái nghe nhạc trịnh nhé": "nghe nhạc trịnh",
        "Sở thích của tôi là chụp ảnh": "chụp ảnh",
        "Tôi thích golf vì hay gặp đối tác ở đó": "golf",
        "Tôi thích golf, còn vợ tôi thích yoga": "golf",
        "I like golf": "golf",
        "I really love travelling to Japan": "travelling to Japan",
        "i prefer saving monthly": "saving monthly",
    }
    for text, expected in cases.items():
        assert objs(text) == expected, (text, objs(text))


def test_facts_and_entities():
    p = extract_preference("Tôi thích chơi golf")
    assert p.fact == "Thích chơi golf" and p.card_text == "Bạn thích chơi golf"
    assert p.entity == "chơi golf" and p.kind == "PREFERENCE" and not p.english
    e = extract_preference("I like golf")
    assert e.fact == "Likes golf" and e.card_text == "You like golf" and e.english


def test_banking_tastes_are_product_preferences():
    for text in ["Tôi thích gửi tiết kiệm kỳ hạn dài", "Mình thích dùng thẻ tín dụng", "I like paying by card"]:
        assert extract_preference(text).kind == "PRODUCT_PREFERENCE", text


def test_not_a_statement_of_taste():
    for text in [
        "Tôi không thích golf",               # negative
        "Tôi cũng không thích đi Nhật",       # negative with an adverb before it
        "Bạn có thích golf không?",           # a question
        "Tôi có thích golf không?",           # a question about themselves
        "Nếu tôi thích golf thì nên làm gì",  # a condition
        "Hôm nay tôi thích món này",          # about today: an event, not who they are
        "Tối nay tôi thích đi ăn phở",
        "I don't like golf", "I do not love mondays", "if I like golf what then",
        "Tôi thích nó", "Tôi thích điều đó", "tôi thích bạn",   # says nothing when stored
        "Tôi thích", "Lãi suất tiết kiệm bao nhiêu",
        "",
    ]:
        assert extract_preference(text) is None, text


def test_only_the_thing_liked_is_kept():
    assert objs("Tôi thích golf nhưng không có thời gian") == "golf"
    assert objs("Tôi thích uống cà phê. Còn bạn thì sao") == "uống cà phê"
    assert objs("Tôi thích một khoản tiết kiệm an toàn và tôi muốn vay mua nhà") == "một khoản tiết kiệm an toàn"


def test_an_overlong_object_is_not_stored():
    assert extract_preference("Tôi thích " + " ".join(["rất"] * 3 + ["dài"] * 12)) is None


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn(); print("ok", name)
