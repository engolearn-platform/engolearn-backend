"""Topic authoring browser acceptance against Vite, Spring Boot, and MongoDB.

The harness grants the role only on its own disposable database, then uses the normal
cookie login. Run from the workspace root with Python Playwright and Edge installed.
"""

import json
import os
from pathlib import Path
import secrets
from urllib.parse import urlsplit
import subprocess

from playwright.sync_api import expect, sync_playwright


BASE = os.environ.get("TOPIC_AUTHORING_FE", "http://127.0.0.1:13000")
CONTAINER = "engolearn-topic-authoring-test"
DATABASE = os.environ.get("TOPIC_AUTHORING_DB", "engolearn_db")
BASE_API = "/api/management/topics"
ARTIFACTS = Path("e2e-artifacts/topic-authoring")

AUDIT = os.environ.get("TOPIC_AUTHORING_SCREENSHOTS")
GROUPS = ("01-topic-list", "02-basic-information", "03-topic-items", "04-context",
          "05-vocabulary", "06-expressions", "07-quizzes", "08-common-states")
CAPTURES = []


def capture(page, group, name, mode="EDIT", state=None, legacy=None, settled=True):
    if os.environ.get("TOPIC_AUTHORING_CAPTURE_VOCABULARY_ONLY") == "1" and group != 5:
        return
    if not AUDIT:
        if legacy:
            page.screenshot(path=ARTIFACTS / legacy, full_page=True)
        return
    if settled:
        page.wait_for_load_state("networkidle")
    page.evaluate("""async () => {
      await document.fonts.ready;
      await Promise.all([...document.images].filter(i => i.loading !== 'lazy' || i.getBoundingClientRect().top < innerHeight)
        .map(i => i.decode().catch(() => {})));
      await new Promise(resolve => {
        let prior, stable = 0;
        function frame() {
          const boxes = [...document.querySelectorAll('main, header, footer, [role=dialog]')]
            .map(e => [e.getBoundingClientRect().toJSON(), e.scrollHeight]);
          const current = JSON.stringify(boxes);
          stable = current === prior ? stable + 1 : 0;
          prior = current;
          if (stable >= 3) resolve(); else requestAnimationFrame(frame);
        }
        requestAnimationFrame(frame);
      });
    }""")
    root = Path(AUDIT)
    folder = root / GROUPS[group - 1]
    folder.mkdir(parents=True, exist_ok=True)

    def save(suffix="", notes="", full_page=False):
        path = folder / f"{name}{suffix}.png"
        page.screenshot(path=path, full_page=full_page, animations="disabled")
        CAPTURES.append({
            "id": path.stem, "feature": GROUPS[group - 1], "screen": name,
            "route": urlsplit(page.url).path, "mode": mode, "state": state or name,
            "viewport": page.viewport_size, "imagePath": path.relative_to(root).as_posix(),
            "frontendSha": os.environ["TOPIC_AUTHORING_FE_SHA"],
            "backendSha": os.environ["TOPIC_AUTHORING_BE_SHA"],
            "captureResult": "CAPTURED", "notes": notes,
        })
        report = root / "reports" / "screenshot-manifest.json"
        report.parent.mkdir(parents=True, exist_ok=True)
        temporary = report.with_suffix(".tmp")
        temporary.write_text(json.dumps(CAPTURES, ensure_ascii=False, indent=2), encoding="utf-8")
        temporary.replace(report)

    save()
    if page.evaluate("document.scrollingElement.scrollHeight > innerHeight + 2"):
        save("-full-page", "Document overflow", full_page=True)
    scrolls = page.locator("main, main *, [role=dialog], [role=dialog] *").evaluate_all("""els => els
      .filter(e => /auto|scroll/.test(getComputedStyle(e).overflowY)
        && e.scrollHeight > e.clientHeight + 8 && e.clientHeight > 80 && e.clientWidth > 120)
      .map(e => { e.setAttribute('data-topic-audit-scroll', ''); return e.scrollTop; })""")
    for i, original in enumerate(scrolls):
        target = page.locator("[data-topic-audit-scroll]").nth(i)
        try:
            dimensions = target.evaluate("e => [e.clientHeight, e.scrollHeight]")
            step = max(1, dimensions[0] - 48)
            for j, offset in enumerate(range(0, dimensions[1] - dimensions[0] + step, step)):
                if offset == original:
                    continue
                actual = target.evaluate("(e, offset) => (e.scrollTop = offset, e.scrollTop)", offset)
                if actual != original:
                    save(f"-scroll-{i+1}-{j+1}", f"Container {i+1}, scrollTop {actual}px")
        finally:
            target.evaluate("(e, original) => e.scrollTop = original", original)
    page.locator("[data-topic-audit-scroll]").evaluate_all(
        "els => els.forEach(e => e.removeAttribute('data-topic-audit-scroll'))")


def mongo(script):
    return subprocess.run(
        ["docker", "exec", CONTAINER, "mongosh", "--quiet", DATABASE, "--eval", script],
        check=True, capture_output=True, text=True, encoding="utf-8",
    ).stdout.strip()


def exercise_items(page, topic_id, run_id):
    api = f"{BASE_API}/{topic_id}/items"
    first_title = f"Order a drink {run_id}"
    second_title = f"Ask for the menu {run_id}"
    page.goto(f"/admin/topics/{topic_id}/edit/items")
    capture(page, 3, "items-editor-empty")

    def create(title):
        page.get_by_role("button", name="Thêm Topic Item mới", exact=True).click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("Tên bài học").fill(title)
        if title == first_title:
            capture(page, 3, "items-create-dialog", "CREATE")
        with page.expect_response(lambda response: response.url.endswith(api) and response.request.method == "POST") as event:
            dialog.get_by_label("Tên bài học").press("Enter")
        assert event.value.status == 201, event.value.text()
        expect(dialog).not_to_be_visible()
        return next(item["id"] for item in event.value.json()["data"]["items"] if item["title"] == title)

    first_id = create(first_title)
    second_id = create(second_title)
    capture(page, 3, "items-editor-reorder-controls")
    page.get_by_role("button", name=f"Đưa {second_title} lên", exact=True).click()
    expect(page.get_by_role("status")).to_contain_text("Đã lưu thứ tự")
    assert page.locator("article h2").all_text_contents() == [second_title, first_title]

    card = page.locator("article").filter(has_text=first_title)
    card.get_by_role("button", name="Sửa thông tin").click()
    edit = page.get_by_role("dialog")
    edit.get_by_label("Mô tả").fill("Order at the counter")
    edit.get_by_label("Thời lượng (phút)").fill("7")
    capture(page, 3, "items-edit-dialog")
    edit.get_by_label("Tên bài học").press("Enter")
    expect(card).to_contain_text("Order at the counter")
    expect(card).to_contain_text("7 phút")
    page.reload()
    expect(card).to_contain_text("Order at the counter")
    assert page.locator("article h2").all_text_contents() == [second_title, first_title]

    page.once("dialog", lambda dialog: dialog.accept())
    card.get_by_role("button", name="Ẩn bài học").click()
    expect(card).to_contain_text("Đang ẩn")
    capture(page, 3, "items-editor-hidden")
    card.get_by_role("button", name="Hiện bài học").click()
    expect(card).to_contain_text("Bản nháp")
    capture(page, 3, "items-editor-reloaded", "EDIT", legacy="04-item-list-reload.png")

    page.once("dialog", lambda dialog: dialog.accept())
    page.locator("article").filter(has_text=second_title).get_by_role("button", name="Xóa", exact=True).click()
    expect(page.locator("article h2")).to_have_text([first_title])
    assert mongo(f"print(db.topic_items.countDocuments({{_id:ObjectId({json.dumps(second_id)})}}))") == "0"
    stored = json.loads(mongo(f"print(EJSON.stringify(db.topic_item_versions.findOne({{topic_item_id:{json.dumps(first_id)}}})))"))
    assert stored["title"] == first_title and stored["duration_minutes"] == 7 and stored["status"] == "DRAFT"
    return first_id


def exercise_context(page, topic_id, item_id):
    route = f"/admin/topics/{topic_id}/edit/items/{item_id}/context"
    page.goto(route)
    capture(page, 4, "context-editor-empty")
    if AUDIT:
        page.goto(f"/admin/topics/{topic_id}/view/items/{item_id}/context")
        expect(page.get_by_role("status")).to_contain_text("Chưa có dữ liệu bối cảnh tình huống")
        capture(page, 4, "context-draft-empty-read-only", "VIEW")
        page.goto(route)
    expected = "At the cafe counter"
    prompt = "What would you like to order?"
    page.get_by_label("Mô tả bối cảnh tình huống").fill(expected)
    page.get_by_label("Câu hỏi gợi mở phản xạ").fill(prompt)
    page.get_by_label("Mẹo cho người học").fill("Ask politely")
    turns = [("Customer", "Could I have tea?"), ("Barista", "Certainly."), ("Customer", "Thank you!")]
    for speaker, text in turns:
        page.get_by_role("button", name="Thêm dòng thoại").click()
        page.get_by_label("Tên người nói").last.fill(speaker)
        page.get_by_label(f"Lời thoại của {speaker} (tiếng Anh)").last.fill(text)
    capture(page, 4, "context-editor-dialogue-turns")
    page.get_by_role("button", name="Đưa dòng thoại lên").last.click()
    page.get_by_role("button", name="Lưu nháp", exact=True).click()
    expect(page.get_by_role("status").filter(has_text="Đã lưu nháp")).to_be_visible()
    page.reload()
    expect(page.get_by_label("Mô tả bối cảnh tình huống")).to_have_value(expected)
    expect(page.get_by_label("Câu hỏi gợi mở phản xạ")).to_have_value(prompt)
    assert page.get_by_label("Tên người nói").evaluate_all("els => els.map(e => e.value)") == ["Customer", "Customer", "Barista"]
    capture(page, 4, "context-editor-reloaded", "EDIT", legacy="09-context-reloaded.png")
    # Keep all three turns for the read-view order and repeated-speaker assertions.
    stored = json.loads(mongo(f"print(EJSON.stringify(db.topic_item_versions.findOne({{topic_item_id:{json.dumps(item_id)}}})))"))
    assert stored["context"]["scenario_description"] == expected
    assert stored["context"]["prompt"] == prompt
    assert [turn["speaker"] for turn in stored["context"]["dialogue"]] == ["Customer", "Customer", "Barista"]
    assert [turn["text_en"] for turn in stored["context"]["dialogue"]] == ["Could I have tea?", "Thank you!", "Certainly."]
    assert stored["duration_minutes"] == 7


def exercise_vocabulary(page, topic_id, item_id):
    page.goto(f"/admin/topics/{topic_id}/edit/items/{item_id}/vocabulary")
    capture(page, 5, "vocabulary-editor-empty")
    page.get_by_role("button", name="Thêm từ vựng mới").click()
    dialog = page.get_by_role("dialog")
    dialog.locator("#vocab-word").fill("beverage")
    dialog.locator("#vocab-vi").fill("thức uống")
    dialog.locator("#vocab-ipa").fill("/ˈbev.ər.ɪdʒ/")
    dialog.locator("#vocab-example-en-0").fill("Tea is a beverage.")
    dialog.locator("#vocab-example-vi-0").fill("Trà là thức uống.")
    dialog.get_by_role("textbox", name="Thêm cụm từ mới").fill("hot beverage")
    dialog.get_by_role("textbox", name="Thêm cụm từ mới").press("Enter")
    capture(page, 5, "vocabulary-add-dialog", "CREATE")
    dialog.get_by_role("button", name="Lưu vào danh sách").click()
    page.get_by_role("button", name="Lưu nháp", exact=True).click()
    expect(page.get_by_role("status").filter(has_text="Đã lưu nháp")).to_be_visible()
    page.reload()
    if os.environ.get("TOPIC_AUTHORING_VOCABULARY_READ") == "1":
        expect(page.get_by_role("heading", name="beverage", exact=True)).to_be_visible()
        assert page.get_by_role("heading", name="beverage", exact=True).count() == 1
        expect(page.get_by_text("thức uống", exact=True)).to_be_visible()
        expect(page.get_by_text("Tea is a beverage.", exact=True)).to_be_visible()
        expect(page.get_by_text("Trà là thức uống.", exact=True)).to_be_visible()
        expect(page.get_by_text("Chưa có âm thanh", exact=True)).to_be_visible()
        expect(page.get_by_text("hot beverage", exact=True)).to_be_visible()
        expect(page.get_by_role("button", name="Chỉnh sửa", exact=True)).to_be_visible()
        expect(page.get_by_role("button", name="Xóa từ beverage", exact=True)).to_be_visible()
    capture(page, 5, "vocabulary-editor-reloaded", "EDIT", legacy="10-vocabulary-reloaded.png")
    if os.environ.get("TOPIC_AUTHORING_VOCABULARY_READ") == "1":
        page.get_by_text("Thông tin tham chiếu", exact=True).first.click()
        expect(page.get_by_text("Nguồn ví dụ: thủ công", exact=True)).to_be_visible()
        capture(page, 5, "vocabulary-editor-expanded", "EDIT")
    stored = json.loads(mongo(f"print(EJSON.stringify(db.topic_item_versions.findOne({{topic_item_id:{json.dumps(item_id)}}})))"))
    entry = stored["vocabularies"][0]
    assert entry["snapshot"]["word"] == "beverage"
    assert entry["samples"][0]["text_en"] == "Tea is a beverage."
    assert entry["collocations"][0]["text"] == "hot beverage"
    assert entry.get("vocab_id") is None


def exercise_expressions(page, topic_id, item_id):
    page.goto(f"/admin/topics/{topic_id}/edit/items/{item_id}/expressions")
    capture(page, 6, "expressions-editor-empty")
    page.get_by_role("button", name="Thêm nhóm mẫu câu").click()
    page.get_by_label("Tên nhóm tiếng Anh").fill("Order politely")
    page.get_by_label("Tên nhóm tiếng Việt").fill("Gọi món lịch sự")
    page.get_by_label("Loại nhóm").select_option("BASIC")
    page.get_by_role("button", name="Thêm mẫu câu", exact=True).click()
    dialog = page.get_by_role("dialog")
    dialog.get_by_label("Mẫu câu tiếng Anh").fill("Could I have tea?")
    dialog.get_by_label("Bản dịch tiếng Việt").fill("Cho tôi một tách trà nhé?")
    dialog.get_by_label("Loại mẫu câu").select_option("POLITE_INQUIRY")
    dialog.get_by_label("Nhãn mẫu câu").select_option("POLITE")
    capture(page, 6, "expressions-add-dialog", "CREATE")
    dialog.get_by_role("button", name="Áp dụng vào bản nháp").click()
    page.get_by_role("button", name="Lưu nháp", exact=True).click()
    expect(page.get_by_role("status").filter(has_text="Đã lưu nháp")).to_be_visible()
    page.reload()
    expect(page.get_by_label("Tên nhóm tiếng Anh")).to_have_value("Order politely")
    expect(page.get_by_text("Could I have tea?", exact=False).first).to_be_visible()
    capture(page, 6, "expressions-editor-reloaded", "EDIT", legacy="11-expressions-reloaded.png")
    stored = json.loads(mongo(f"print(EJSON.stringify(db.topic_item_versions.findOne({{topic_item_id:{json.dumps(item_id)}}})))"))
    purpose = stored["expressions"][0]["purposes"][0]
    assert purpose["group_type"] == "BASIC"
    assert purpose["examples"][0]["type"] == "POLITE_INQUIRY"
    assert purpose["examples"][0]["label"] == "POLITE"


def exercise_quizzes(page, topic_id, item_id):
    page.goto(f"/admin/topics/{topic_id}/edit/items/{item_id}/quiz")
    capture(page, 7, "quiz-editor-empty")
    page.get_by_role("button", name="Thêm Fill Blank module", exact=True).click()
    page.get_by_label("Quiz module 1 title").fill("Ordering Tea")
    page.get_by_role("button", name="Thêm câu hỏi", exact=True).first.click()
    dialog = page.get_by_role("dialog")
    dialog.locator("#quiz-text-en").fill("Could I have tea?")
    dialog.locator("#quiz-meaning-vi").fill("Cho tôi một tách trà nhé?")
    dialog.locator("#quiz-blank").fill("Could I have ___?")
    dialog.locator("#quiz-wordbank").fill("tea, receipt, menu, table")
    dialog.locator("#quiz-correct").select_option("tea")
    dialog.locator("#quiz-fb-explanation").fill("Tea is the requested drink.")
    capture(page, 7, "quiz-fill-blank-add-dialog", "CREATE")
    dialog.get_by_role("button", name="Lưu vào bản nháp", exact=True).click()

    page.get_by_role("button", name="Thêm Situational Choice module", exact=True).click()
    page.get_by_label("Quiz module 2 title").fill("At the counter")
    page.get_by_role("button", name="Thêm câu hỏi", exact=True).last.click()
    dialog.locator("#quiz-prompt").fill("Chọn câu gọi đồ uống lịch sự.")
    while dialog.get_by_role("textbox", name="Nội dung đáp án").count() < 4:
        dialog.get_by_role("button", name="Thêm đáp án", exact=True).click()
    for label, answer in zip("ABCD", ["Could I have tea?", "Leave now.", "Run faster.", "Turn left."]):
        dialog.get_by_label(f"Nội dung đáp án {label}").fill(answer)
    dialog.locator('input[name="quiz-correct-option"]').first.press("Space")
    dialog.locator("#quiz-explanation").fill("Could I have is a polite request.")
    capture(page, 7, "quiz-situational-add-dialog", "CREATE")
    dialog.get_by_role("button", name="Lưu vào bản nháp", exact=True).click()
    page.get_by_role("button", name="Lưu nháp", exact=True).click()
    expect(page.get_by_role("status").filter(has_text="Đã lưu nháp")).to_be_visible()
    page.reload()
    expect(page.get_by_label("Quiz module 1 title")).to_have_value("Ordering Tea")
    expect(page.get_by_label("Quiz module 2 title")).to_have_value("At the counter")
    capture(page, 7, "quiz-editor-reloaded", "EDIT", legacy="12-quizzes-reloaded.png")
    stored = json.loads(mongo(f"print(EJSON.stringify(db.topic_item_versions.findOne({{topic_item_id:{json.dumps(item_id)}}})))"))
    fill, situation = stored["quizzes"]
    assert fill["quiz_type"] == "FILL_BLANK" and situation["quiz_type"] == "SITUATIONAL_CHOICE"
    fill_question = fill["questions"][0]
    situation_question = situation["questions"][0]
    assert fill_question["sentence"]["text_en"] == "Could I have tea?"
    assert len(fill_question["options"]) == 4 and fill_question["options"][0]["is_correct"] is True
    assert len(situation_question["options"]) == 4 and situation_question["options"][0]["is_correct"] is True
    assert situation_question["explanation"] == "Could I have is a polite request."


def exercise_read_only(page, request):
    topic_id = "672c260de000100a0000d001"
    path = f"/admin/topics/{topic_id}/view/items"
    items = request.get(f"{BASE_API}/{topic_id}/items").json()["data"]
    assert len(items) == 5
    item_id = items[0]["id"]
    page.goto(path)
    expect(page.get_by_text(items[0]["title"], exact=True).first).to_be_visible()
    capture(page, 3, "items-published-read-only", "VIEW")
    page.goto(f"{path}/{item_id}/context")
    for number, section, label in [(5, "context", "Bối cảnh"), (6, "vocabulary", "Từ vựng"),
                                    (7, "expressions", "Mẫu câu giao tiếp"), (8, "quiz", "Bài tập & Quiz")]:
        tab = page.get_by_role("tab", name=label)
        tab.click()
        page.wait_for_url(f"**/{section}")
        expect(tab).to_have_attribute("aria-selected", "true")
        if section == "context":
            expect(page.get_by_text("Tình huống", exact=True)).to_be_visible()
            expect(page.get_by_text("Thời lượng: 90 giây", exact=True)).to_be_visible()
            expect(page.get_by_text("Mẹo cho người học", exact=True)).to_be_visible()
            expect(page.get_by_text("Linh", exact=True).first).to_be_visible()
            expect(page.get_by_text("What time do you get up on workdays?", exact=True)).to_be_visible()
            assert page.get_by_text("Mô tả tình huống", exact=True).count() == 0
            assert page.get_by_text("Người nói", exact=True).count() == 0
        capture(page, number - 1, f"{section}-published-read-only", "VIEW", legacy=f"{number:02}-read-{section}.png")
    page.reload()
    expect(page.get_by_role("tab", name="Bài tập & Quiz")).to_have_attribute("aria-selected", "true")
    page.go_back()
    expect(page.get_by_role("tab", name="Mẫu câu giao tiếp")).to_have_attribute("aria-selected", "true")


def exercise_vocabulary_read(page, request):
    topic_id = "672c260de000100a0000d001"
    items = request.get(f"{BASE_API}/{topic_id}/items").json()["data"]
    item_id = items[0]["id"]
    detail = request.get(f"{BASE_API}/{topic_id}/items/{item_id}").json()["data"]
    words = [entry["snapshot"]["word"] for entry in detail["vocabularies"]]
    assert len(words) == 4 and words[0] == "get up"
    assert [entry["orderIndex"] for entry in detail["vocabularies"]] == [1, 2, 3, 4]

    page.goto(f"/admin/topics/{topic_id}/view/items/{item_id}/vocabulary")
    expect(page.get_by_role("heading", name="get up", exact=True)).to_be_visible()
    assert page.get_by_role("heading", name="get up", exact=True).count() == 1
    expect(page.get_by_text("thức dậy, ra khỏi giường", exact=True)).to_be_visible()
    expect(page.get_by_text("I get up at six thirty on Mondays.", exact=True)).to_be_visible()
    expect(page.get_by_text("Thứ Hai mình dậy lúc sáu giờ rưỡi.", exact=True)).to_be_visible()
    expect(page.get_by_text("Chưa có âm thanh", exact=True)).to_have_count(3)
    expect(page.get_by_text("get up early", exact=True)).to_be_visible()
    assert page.get_by_text("vocabId", exact=True).count() == 0
    assert page.get_by_text("senseId", exact=True).count() == 0
    assert page.get_by_text("additionalData", exact=True).count() == 0
    assert page.get_by_role("button", name="Chỉnh sửa").count() == 0
    capture(page, 5, "vocabulary-published-semantic", "VIEW")

    with page.expect_response("**/media/missing-example-audio") as event:
        page.locator('audio[aria-label="Phát âm thanh ví dụ 1"]').evaluate("audio => audio.play().catch(() => {})")
    assert event.value.status == 404, event.value.status
    expect(page.get_by_text("Không phát được âm thanh", exact=True)).to_be_visible()
    page.get_by_text("get up early", exact=True).click()
    expect(page.get_by_text("dậy sớm", exact=True)).to_be_visible()
    page.get_by_text("Kiểm tra nhanh", exact=True).click()
    expect(page.get_by_text("Bạn rời khỏi giường lúc sáu giờ. Cụm nào diễn tả việc đó?", exact=True)).to_be_visible()
    expect(page.get_by_text("Đáp án đúng", exact=True)).to_be_visible()
    page.locator("details").filter(has=page.locator("summary", has_text="Thông tin tham chiếu")).first.locator("summary").click()
    expect(page.get_by_text("Từ điển liên kết chưa được tải:", exact=False).first).to_be_visible()
    capture(page, 5, "vocabulary-published-expanded", "VIEW")


def exercise_readiness(page, topic_id):
    page.goto(f"/admin/topics/{topic_id}/edit/items")
    check = page.get_by_role("button", name="Kiểm tra điều kiện xuất bản", exact=True)
    endpoint = f"{BASE_API}/{topic_id}/readiness"
    with page.expect_response(lambda response: response.url.endswith(endpoint)) as event:
        check.click()
    assert event.value.ok, event.value.text()
    readiness = event.value.json()["data"]
    assert not readiness["ready"] and len(readiness["violations"]) >= 2
    expect(page.get_by_text("Cần bổ sung trước khi xuất bản", exact=True)).to_be_visible()
    capture(page, 8, "publish-readiness-violations", "EDIT", legacy="13-publish-readiness.png")


def exercise_invalid_media(page, topic_id):
    page.goto(f"/admin/topics/{topic_id}/edit")
    endpoint = f"{BASE_API}/{topic_id}/media/image"
    with page.expect_response(lambda response: response.url.endswith(endpoint)) as event:
        page.locator('input[type="file"]').set_input_files({
            "name": "fake.png", "mimeType": "image/png", "buffer": b"not a PNG image",
        })
    assert event.value.status == 400, event.value.text()
    assert event.value.json()["code"] == "MEDIA_UPLOAD_REJECTED"
    capture(page, 8, "topic-edit-media-upload-error")
    page.locator("#topic-desc").fill("Draft remains editable after the rejected upload")
    page.get_by_role("button", name="Lưu thay đổi", exact=True).click()
    expect(page.get_by_role("status")).to_contain_text("Đã lưu thay đổi")
    capture(page, 8, "media-rejected-draft-saved", "EDIT", legacy="14-media-rejected-draft-saved.png")


def run():
    if not AUDIT:
        ARTIFACTS.mkdir(parents=True, exist_ok=True)
    run_id = secrets.token_hex(5)
    email = f"topic-authoring-{run_id}@example.test"
    credentials = {"email": email, "password": secrets.token_urlsafe(24)}
    original_title = f"Cafe authoring {run_id}"
    edit_title = original_title + " edited"
    checks = ["guest 401", "learner 403", "Content Manager draft 201", "PATCH revision 1", "stale 409",
              "reload preserved saved data", "MongoDB fields verified", "management read list and detail"]
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel="msedge", headless=True)
        context = browser.new_context(base_url=BASE, viewport={"width": 1440, "height": 1000})
        guest = context.request.get(BASE_API)
        assert guest.status == 401, (guest.status, guest.text())
        registration = context.request.post("/api/v1/auth/register", data=credentials)
        assert registration.status == 201, registration.text()
        assert context.request.post(BASE_API, data={"titleEn": original_title, "titleVi": "Quán cà phê"}).status == 403
        changed = mongo(f"print(db.users.updateOne({{email:{json.dumps(email)}}}, {{$set:{{role:'CONTENT_MANAGER'}}}}).modifiedCount)")
        assert changed == "1", changed
        login = context.request.post("/api/v1/auth/login", data=credentials)
        assert login.ok and login.json()["data"]["role"] == "CONTENT_MANAGER"

        page = context.new_page()
        if AUDIT:
            page.goto("/admin/topics")
            create = page.get_by_text("Tạo topic mới", exact=True)
            expect(create).to_be_visible()
            capture(page, 1, "topic-list-before-create", "VIEW")
            create.click()
        else:
            page.goto("/admin/topics/create")
        expect(page.locator("#topic-en")).to_be_visible()
        capture(page, 2, "topic-create-initial", "CREATE")
        if AUDIT:
            page.get_by_role("button", name="Lưu nháp", exact=True).click()
            capture(page, 2, "topic-create-validation", "CREATE")
        page.locator("#topic-en").fill(original_title)
        page.locator("#topic-vi").fill("Quán cà phê")
        capture(page, 2, "topic-create-populated", "CREATE")
        with page.expect_response(lambda response: response.url.endswith(BASE_API) and response.request.method == "POST") as event:
            page.get_by_role("button", name="Lưu nháp", exact=True).click()
        assert event.value.status == 201, event.value.text()
        created = event.value.json()["data"]
        assert created["status"] == "draft" and created["revision"] == 0
        assert created.get("category") is None and created.get("cefr") is None
        topic_id = created["id"]
        api = f"{BASE_API}/{topic_id}"
        edit = f"/admin/topics/{topic_id}/edit"
        page.wait_for_url(f"**{edit}")
        expect(page.locator("#topic-en")).to_have_value(original_title)
        capture(page, 2, "topic-edit-after-create", "EDIT", legacy="01-topic-created.png")

        concurrent = context.new_page()
        concurrent.goto(edit)
        expect(concurrent.locator("#topic-en")).to_have_value(original_title)
        page.locator("#topic-en").fill(edit_title)
        page.locator("#topic-category").select_option("DINING")
        page.get_by_role("radio", name="A2", exact=False).click()
        tags = page.get_by_role("textbox", name="Thêm từ khóa mới")
        tags.fill("menu")
        tags.press("Enter")
        with page.expect_response(lambda response: response.url.endswith(api) and response.request.method == "PATCH") as event:
            page.get_by_role("button", name="Lưu thay đổi", exact=True).click()
        assert event.value.ok, event.value.text()
        updated = event.value.json()["data"]
        assert updated["revision"] == 1 and updated["titleEn"] == edit_title and updated["cefr"] == "A2"

        capture(page, 8, "topic-edit-save-notification")
        concurrent.locator("#topic-en").fill("Stale local edit")
        with concurrent.expect_response(lambda response: response.url.endswith(api) and response.request.method == "PATCH") as event:
            concurrent.get_by_role("button", name="Lưu thay đổi", exact=True).click()
        assert event.value.status == 409, event.value.text()
        expect(concurrent.locator("#topic-en")).to_have_value("Stale local edit")
        expect(concurrent.get_by_role("button", name="Tải lại chủ đề")).to_be_visible()
        capture(concurrent, 8, "topic-edit-revision-conflict", "EDIT", legacy="02-topic-conflict.png")
        concurrent.once("dialog", lambda dialog: dialog.accept())
        concurrent.get_by_role("button", name="Tải lại chủ đề").click()
        expect(concurrent.locator("#topic-en")).to_have_value(edit_title)

        page.reload()
        expect(page.locator("#topic-en")).to_have_value(edit_title)
        expect(page.locator("#topic-category")).to_have_value("DINING")
        expect(page.get_by_role("radio", name="A2", exact=False)).to_have_attribute("aria-checked", "true")
        stored = json.loads(mongo(f"print(EJSON.stringify(db.topics.findOne({{title_en:{json.dumps(edit_title)}}})))"))
        assert stored["revision"] == 1 and stored["cefr_level"] == "A2" and stored["category"] == "DINING"
        assert stored["tags"] == ["menu"]
        assert stored.get("latest_published_version") is None

        capture(page, 2, "topic-edit-populated")
        if AUDIT:
            page.goto("/admin/topics")
            row = page.get_by_text(edit_title, exact=True).first
            expect(row).to_be_visible()
            capture(page, 1, "topic-list-populated", "VIEW")
            menu = page.get_by_role("button", name=f"Thao tác cho {edit_title}", exact=True)
            menu.click()
            capture(page, 1, "topic-list-row-actions", "VIEW")
            page.keyboard.press("Escape")
            search = page.get_by_placeholder("Tìm kiếm topic", exact=False)
            search.fill(edit_title)
            expect(row).to_be_visible()
            capture(page, 1, "topic-list-search", "VIEW")
            search.fill("audit-no-matching-topic")
            expect(row).not_to_be_visible()
            capture(page, 1, "topic-list-search-empty", "VIEW")
        page.goto("/admin/topics/read")
        expect(page.get_by_text(edit_title, exact=True)).to_be_visible()
        capture(page, 1, "topic-list-read-only", "VIEW")
        page.goto(f"/admin/topics/{topic_id}/view")
        expect(page.get_by_text(edit_title, exact=True).first).to_be_visible()
        capture(page, 2, "topic-detail-read-only", "VIEW", legacy="03-topic-read-after-reload.png")
        if os.environ.get("TOPIC_AUTHORING_ITEMS") == "1":
            item_id = exercise_items(page, topic_id, run_id)
            checks.extend(["create two items", "reorder persisted", "metadata edit and reload", "hide and unhide", "delete draft and verify MongoDB"])
            if os.environ.get("TOPIC_AUTHORING_CONTEXT") == "1":
                exercise_context(page, topic_id, item_id)
                checks.append("Context Save Draft, reload, repeated-speaker order and Mongo fields verified")
            if os.environ.get("TOPIC_AUTHORING_VOCABULARY") == "1":
                exercise_vocabulary(page, topic_id, item_id)
                checks.append("vocabulary draft, example and collocation persisted and reloaded")
            if os.environ.get("TOPIC_AUTHORING_EXPRESSIONS") == "1":
                exercise_expressions(page, topic_id, item_id)
                checks.append("expression group type, expression type and label persisted and reloaded")
            if os.environ.get("TOPIC_AUTHORING_QUIZZES") == "1":
                exercise_quizzes(page, topic_id, item_id)
                checks.append("Fill Blank and Situational Choice questions, answers and explanation persisted and reloaded")
            if AUDIT:
                item_path = f"/admin/topics/{topic_id}/view/items"
                page.goto(item_path)
                expect(page.get_by_text(f"Order a drink {run_id}", exact=True).first).to_be_visible()
                capture(page, 3, "items-draft-read-only", "VIEW")
                page.goto(f"{item_path}/{item_id}/context")
                for group, name, label in [(4, "context", "Bối cảnh"), (5, "vocabulary", "Từ vựng"),
                                           (6, "expressions", "Mẫu câu giao tiếp"), (7, "quiz", "Bài tập & Quiz")]:
                    tab = page.get_by_role("tab", name=label)
                    tab.click()
                    expect(tab).to_have_attribute("aria-selected", "true")
                    if name == "context":
                        expect(page.get_by_text("Tình huống", exact=True)).to_be_visible()
                        expect(page.get_by_text("Câu hỏi gợi mở", exact=True)).to_be_visible()
                        expect(page.get_by_text("At the cafe counter", exact=True)).to_be_visible()
                        expect(page.get_by_text("What would you like to order?", exact=True)).to_be_visible()
                        assert page.get_by_text("Customer", exact=True).count() == 2
                        transcript = page.get_by_label("Hội thoại").get_by_role("listitem").evaluate_all("els => els.map(el => el.innerText)")
                        assert [text for text in ("Could I have tea?", "Thank you!", "Certainly.") if any(text in turn for turn in transcript)] == ["Could I have tea?", "Thank you!", "Certainly."]
                        assert page.get_by_label("Hội thoại").get_by_text("Chưa có dữ liệu", exact=True).count() == 0
                        assert page.get_by_text("Could I have tea?", exact=True).count() == 1
                        assert page.get_by_text("Thank you!", exact=True).count() == 1
                        assert page.get_by_text("Certainly.", exact=True).count() == 1
                        expect(page.get_by_text("Chưa có âm thanh cho lời thoại này.")).to_have_count(3)
                    capture(page, group, f"{name}-draft-read-only", "VIEW")
        if os.environ.get("TOPIC_AUTHORING_READ_FIXTURE") == "1":
            exercise_read_only(page, context.request)
            checks.append("Phase 1 read-only tabs, refresh and browser Back")
        if os.environ.get("TOPIC_AUTHORING_VOCABULARY_READ") == "1":
            exercise_vocabulary_read(page, context.request)
            checks.append("Vocabulary published semantic card, optional quick check, collocation details, unresolved linked reference, missing audio and real protected-media 404")
        if os.environ.get("TOPIC_AUTHORING_READINESS") == "1":
            exercise_readiness(page, topic_id)
            checks.append("incomplete draft persisted and readiness reports field paths")
        if os.environ.get("TOPIC_AUTHORING_MEDIA") == "1":
            exercise_invalid_media(page, topic_id)
            checks.append("forged image rejected by server and unrelated draft change still saved")
            guest = browser.new_context(base_url=BASE)
            media_path = f"{BASE_API}/{topic_id}/media/image"
            forged_file = {"file": {"name": "fake.png", "mimeType": "image/png", "buffer": b"not PNG"}}
            assert guest.request.post(media_path, multipart=forged_file).status == 401
            guest.close()
            assert mongo(f"print(db.users.updateOne({{email:{json.dumps(email)}}}, {{$set:{{role:'ADMIN'}}}}).modifiedCount)") == "1"
            assert context.request.post(media_path, multipart=forged_file).status == 400
            checks.append("media rejects guests and inherits Content Manager authorization for Admin")
        if not AUDIT and os.environ.get("TOPIC_AUTHORING_MOBILE") == "1":
            page.set_viewport_size({"width": 390, "height": 844})
            page.goto(edit)
            expect(page.locator("#topic-en")).to_have_value(edit_title)
            page.screenshot(path=ARTIFACTS / "15-mobile-topic-editor.png", full_page=True)
            checks.append("Topic metadata editor opened at 390px viewport")
        browser.close()
    report_dir = Path(AUDIT) / "reports" if AUDIT else ARTIFACTS
    (report_dir / "verification.json").write_text(json.dumps({
        "topicId": topic_id,
        "checks": checks,
    }, indent=2), encoding="utf-8")


if __name__ == "__main__":
    run()
