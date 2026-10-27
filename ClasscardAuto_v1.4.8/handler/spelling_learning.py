import re
import time
import tempfile
from pathlib import Path
from typing import Optional

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException, NoSuchElementException, StaleElementReferenceException
from utility import randomized_answer_delay


class SpellingLearning:
    """Spelling handler with selectors for both the older and newer Classcard UI."""

    def __init__(self, driver: webdriver.Chrome):
        self.driver = driver

    def _snapshot(self, label: str) -> None:
        safe_label = re.sub(r"[^a-zA-Z0-9_-]+", "_", label)[:50]
        try:
            print(f"현재 주소: {self.driver.current_url}")
            print(f"현재 페이지 제목: {self.driver.title}")
        except Exception as exc:
            print(f"페이지 상태 확인 실패: {exc}")

        png_name = f"spelling_debug_{safe_label}.png"
        try:
            self.driver.save_screenshot(png_name)
            print(f"진단용 화면 캡처 저장: {png_name}")
        except Exception as exc:
            print(f"화면 캡처 저장 실패: {exc}")

        # 저장 권한이 없거나 같은 이름의 파일이 잠겨 있으면 임시 폴더로 재시도.
        html_name = f"spelling_debug_{safe_label}.html"
        try:
            Path(html_name).write_text(self.driver.page_source, encoding="utf-8")
            print(f"진단용 HTML 저장: {html_name}")
        except Exception as first_exc:
            try:
                fallback = Path(tempfile.gettempdir()) / f"{Path(html_name).stem}_{int(time.time())}.html"
                fallback.write_text(self.driver.page_source, encoding="utf-8")
                print(f"기본 폴더에는 저장할 수 없어 임시 폴더에 HTML을 저장했습니다: {fallback}")
            except Exception as second_exc:
                print(f"진단 HTML 저장 실패: {first_exc}; 임시 폴더 저장도 실패: {second_exc}")

    @staticmethod
    def _norm(value: str) -> str:
        return re.sub(r"\s+", " ", (value or "")).strip().casefold()

    def _visible(self, selectors, *, enabled=False):
        for by, selector in selectors:
            try:
                for element in self.driver.find_elements(by, selector):
                    if element.is_displayed() and (not enabled or element.is_enabled()):
                        return element
            except (StaleElementReferenceException, NoSuchElementException):
                continue
        return None

    def _click_start(self) -> None:
        driver = self.driver
        # These are the two buttons in the older set-learning flow.
        button = WebDriverWait(driver, 15).until(
            EC.element_to_be_clickable((By.XPATH, "/html/body/div[2]/div/div[2]/div[1]/div[3]"))
        )
        button.click()
        time.sleep(0.8)
        start_selectors = [
            (By.CSS_SELECTOR, "#wrapper-learn > div.start-opt-body a"),
            (By.XPATH, "/html/body/div[2]/div[2]/div/div/div/div[4]/a"),
        ]
        start = WebDriverWait(driver, 15).until(lambda d: self._visible(start_selectors, enabled=True))
        start.click()
        # Wait for one of the known question layouts to appear.
        WebDriverWait(driver, 15).until(
            lambda d: self._visible([
                (By.CSS_SELECTOR, ".CardItem.current.showing"),
                (By.CSS_SELECTOR, "#wrapper-learn input[name='input_answer']"),
                (By.XPATH, "//*[@id='wrapper-learn']/div[1]/div/div[2]/div[2]/div[1]/div[1]/div/div/div/div[1]/span"),
            ]) is not None
        )

    def _answer_for(self, prompt: str, da_e, da_k) -> Optional[str]:
        prompt_norm = self._norm(prompt)
        upper = min(len(da_e), len(da_k))
        # Exact match first, because definitions may include punctuation.
        for idx in range(1, upper):
            en = str(da_e[idx] or "").strip()
            ko = str(da_k[idx] or "").strip()
            if prompt_norm and prompt_norm == self._norm(ko):
                return en
            if prompt_norm and prompt_norm == self._norm(en):
                return ko
        # Fallback: question labels sometimes wrap the answer in an extra line.
        first_line = self._norm((prompt or "").splitlines()[0] if prompt else "")
        if first_line and first_line != prompt_norm:
            for idx in range(1, upper):
                en = str(da_e[idx] or "").strip()
                ko = str(da_k[idx] or "").strip()
                if first_line == self._norm(ko):
                    return en
                if first_line == self._norm(en):
                    return ko
        return None

    def _modern_card(self):
        return self._visible([(By.CSS_SELECTOR, ".CardItem.current.showing")])

    def _modern_prompt(self, card) -> str:
        selectors = [
            (By.CSS_SELECTOR, ".spell-content.font-32"),
            (By.CSS_SELECTOR, ".spell-content"),
            (By.CSS_SELECTOR, ".card-quest-front"),
        ]
        for by, selector in selectors:
            try:
                for element in card.find_elements(by, selector):
                    if element.is_displayed() and element.text.strip():
                        return element.text.strip()
            except StaleElementReferenceException:
                continue
        # Last-resort readable text of the current card, useful for diagnostics.
        return card.text.strip()

    def _modern_input(self):
        selectors = [
            (By.CSS_SELECTOR, ".CardItem.current.showing .spell-input:not(.hidden) input[name='input_answer']"),
            (By.CSS_SELECTOR, ".CardItem.current.showing input[name='input_answer']"),
            (By.CSS_SELECTOR, "input[name='input_answer']"),
            (By.CSS_SELECTOR, "#wrapper-learn input[type='text']"),
        ]
        element = self._visible(selectors, enabled=True)
        if element:
            return element
        # Some builds use a class-based input without the name attribute.
        return self._visible([
            (By.CSS_SELECTOR, ".CardItem.current.showing input.input-answer"),
            (By.CSS_SELECTOR, "input.form-control.input-lg.input-answer"),
        ], enabled=True)

    def _dismiss_feedback(self) -> None:
        buttons = [
            (By.CSS_SELECTOR, ".modal-content .btn-ok"),
            (By.XPATH, "//button[normalize-space()='알겠습니다!']"),
            (By.XPATH, "//a[normalize-space()='알겠습니다!']"),
        ]
        button = self._visible(buttons, enabled=True)
        if button:
            try:
                button.click()
                time.sleep(0.25)
            except Exception:
                pass

    def _submit_modern(self, input_element) -> None:
        submit_selectors = [
            (By.CSS_SELECTOR, ".btn-confirm"),
            (By.CSS_SELECTOR, "button[type='submit']"),
            (By.CSS_SELECTOR, "#wrapper-learn .btn-primary"),
        ]
        submit = self._visible(submit_selectors, enabled=True)
        if submit:
            submit.click()
        else:
            input_element.send_keys(Keys.ENTER)
        time.sleep(0.6)
        self._dismiss_feedback()

    def _next_modern(self) -> bool:
        next_selectors = [
            (By.CSS_SELECTOR, ".btn-short-change-next"),
            (By.CSS_SELECTOR, "#wrapper-learn .btn-next"),
            (By.XPATH, "//*[@id='wrapper-learn']/div/div/div[3]/div[2]"),
        ]
        button = self._visible(next_selectors, enabled=True)
        if not button:
            return False
        try:
            button.click()
            time.sleep(0.5)
            return True
        except Exception:
            return False

    def _run_modern(self, num_d: int, da_e, da_k, start_question: int = 13) -> None:
        processed = set()
        max_questions = max(1, num_d - 1)
        start_question = max(1, min(start_question, max_questions))
        if start_question > 1:
            print(f"{start_question}번 문제부터 이어서 처리하도록 설정했습니다.")
            print(f"새 스펠 화면은 처음 문제부터 시작하므로, {start_question - 1}번까지는 정답 목록으로 자동 처리해 다음 문제로 이동합니다.")
        for step in range(max_questions):
            card = WebDriverWait(self.driver, 15).until(lambda d: self._modern_card())
            prompt = self._modern_prompt(card)
            card_id = card.get_attribute("data-idx") or card.get_attribute("id") or ""
            identity = (card_id, self._norm(prompt))
            if identity in processed:
                print("현재 문제가 반복되어 중단했습니다. 같은 문제를 무한히 제출하지 않습니다.")
                self._snapshot(f"repeat_{step + 1}")
                return
            processed.add(identity)

            answer = self._answer_for(prompt, da_e, da_k)
            if step + 1 < start_question:
                print(f"{step + 1}/{start_question - 1}번 준비 단계: 정답 목록으로 자동 처리 중 — {prompt[:80]!r}")
            else:
                print(f"스펠 문제 {step + 1}/{max_questions}: {prompt[:100]!r}")
            if answer is None:
                print("단어 목록에서 정확히 일치하는 정답을 찾지 못했습니다.")
                self._snapshot(f"unmatched_{step + 1}")
                print("현재 문제는 직접 처리한 뒤, 다음 문제부터 자동 진행을 재개할 수 있습니다.")
                try:
                    input_element = WebDriverWait(self.driver, 10).until(lambda d: self._modern_input())
                except TimeoutException:
                    print("현재 문제의 입력칸을 찾지 못했습니다. 화면을 확인한 뒤 프로그램을 종료합니다.")
                    return

                manual_answer = input(
                    "터미널에 정답을 입력하면 프로그램이 그대로 입력·제출합니다. "
                    "Chrome에서 직접 답하려면 여기서 Enter만 누르세요: "
                ).strip()
                if manual_answer:
                    # 사용자가 제공한 값은 추측하거나 정규화하지 않고 그대로 사용.
                    answer = manual_answer
                else:
                    try:
                        input_element.click()
                    except Exception:
                        pass
                    input(
                        "Chrome에서 현재 문제의 답을 직접 입력하고 제출한 뒤, "
                        "다음 문제가 화면에 나타나면 이 터미널에서 Enter를 누르세요..."
                    )
                    next_card = self._modern_card()
                    if next_card is None:
                        print("다음 문제를 확인할 수 없습니다. 자동 진행을 종료합니다.")
                        self._snapshot(f"manual_next_missing_{step + 1}")
                        return
                    next_prompt = self._modern_prompt(next_card)
                    next_card_id = next_card.get_attribute("data-idx") or next_card.get_attribute("id") or ""
                    next_identity = (next_card_id, self._norm(next_prompt))
                    if next_identity == identity:
                        print("아직 같은 문제가 표시됩니다. 답을 제출하고 다음 문제로 이동한 뒤 다시 실행해 주세요.")
                        self._snapshot(f"manual_not_advanced_{step + 1}")
                        return
                    # 사용자가 현재 문제를 직접 처리했으므로 다음 반복은 다음 문제부터 시작.
                    continue

            input_element = WebDriverWait(self.driver, 10).until(lambda d: self._modern_input())
            randomized_answer_delay(1.0, 2.0)
            input_element.click()
            input_element.clear()
            input_element.send_keys(str(answer))
            self._submit_modern(input_element)

            if step + 1 < max_questions:
                if not self._next_modern():
                    # Some versions advance automatically when the answer is checked.
                    try:
                        WebDriverWait(self.driver, 4).until(
                            lambda d: (lambda c: c is not None and (
                                (c.get_attribute("data-idx") or c.get_attribute("id") or "", self._norm(self._modern_prompt(c))) != identity
                            ))(self._modern_card())
                        )
                    except TimeoutException:
                        print("제출 후 다음 문제로 넘어가지 않았습니다. 화면 구조를 저장합니다.")
                        self._snapshot(f"no_next_{step + 1}")
                        return
        print("스펠 화면의 문제 처리를 마쳤습니다.")

    def _run_legacy(self, num_d: int, da_e, da_k, start_question: int = 13) -> None:
        driver = self.driver
        start_question = max(1, min(start_question, max(1, num_d - 1)))
        if start_question > 1:
            print(f"구형 화면에서는 {start_question}번 문제부터 처리합니다.")
        for i in range(start_question, num_d):
            prompt_xpath = (
                "//*[@id='wrapper-learn']/div[1]/div/div[2]/div[2]/"
                f"div[{i}]/div[1]/div/div/div/div[1]/span"
            )
            try:
                prompt_el = WebDriverWait(driver, 6).until(
                    EC.visibility_of_element_located((By.XPATH, prompt_xpath))
                )
            except TimeoutException:
                print(f"구형 화면에서 {i}번 문제를 찾지 못했습니다.")
                self._snapshot(f"legacy_question_{i}")
                raise RuntimeError("스펠 화면은 열렸지만 문제 요소를 찾지 못했습니다.")

            prompt = prompt_el.text.strip()
            answer = self._answer_for(prompt, da_e, da_k)
            input_xpath = (
                "/html/body/div[2]/div[1]/div/div[2]/div[2]/"
                f"div[{i}]/div[2]/div/div/div/div[2]/input"
            )
            if answer is None:
                print(f"정답을 단어 목록에서 찾지 못했습니다: {prompt!r}")
                self._snapshot(f"legacy_unmatched_{i}")
                print("터미널에 정답을 입력하면 그대로 제출합니다. Enter만 누르면 Chrome에서 직접 입력할 수 있습니다.")
                manual_answer = input("정답 입력: ").strip()
                if manual_answer:
                    answer = manual_answer
                else:
                    input_element = WebDriverWait(driver, 8).until(
                        EC.element_to_be_clickable((By.XPATH, input_xpath))
                    )
                    try:
                        input_element.click()
                    except Exception:
                        pass
                    input("Chrome에서 답을 직접 입력·제출하고 다음 문제로 넘어간 뒤 Enter를 누르세요...")
                    continue

            input_element = WebDriverWait(driver, 8).until(
                EC.element_to_be_clickable((By.XPATH, input_xpath))
            )
            randomized_answer_delay(1.0, 2.0)
            input_element.click()
            input_element.clear()
            input_element.send_keys(answer)

            submit = self._visible([
                (By.CSS_SELECTOR, "#wrapper-learn .btn-confirm"),
                (By.XPATH, "//*[@id='wrapper-learn']/div/div/div[3]"),
            ], enabled=True)
            if submit:
                submit.click()
            else:
                input_element.send_keys(Keys.ENTER)
            time.sleep(1)
            self._dismiss_feedback()
            self._next_modern()
        print("스펠 화면의 문제 처리를 마쳤습니다.")

    def run(self, num_d: int, word_d: list, start_question: Optional[int] = None) -> None:
        da_e, da_k, _ = word_d
        print("스펠 학습 시작 버튼을 누릅니다...")
        try:
            self._click_start()
        except TimeoutException as exc:
            print("스펠 학습 시작 화면 또는 문제 화면을 찾지 못했습니다.")
            self._snapshot("start_screen")
            raise RuntimeError("스펠 학습 화면의 셀렉터가 사이트 화면과 일치하지 않습니다.") from exc

        time.sleep(0.5)
        # main.py가 회차 반복을 실행할 때는 시작 번호를 전달해 질문을 반복하지 않습니다.
        if start_question is None:
            raw_start = input("몇 번 문제부터 처리할까요? 기본값 13, 처음부터는 1 입력: ").strip()
            try:
                start_question = int(raw_start) if raw_start else 13
            except ValueError:
                print("숫자가 아니어서 기본값 13을 사용합니다.")
                start_question = 13
        start_question = max(1, min(int(start_question), max(1, num_d - 1)))

        # Prefer the current-card layout when available; the indexed XPath layout is a fallback.
        if self._modern_card() is not None:
            print("새 스펠 화면 구조를 감지했습니다.")
            self._run_modern(num_d, da_e, da_k, start_question=start_question)
        else:
            print("기존 스펠 화면 구조를 감지했습니다.")
            self._run_legacy(num_d, da_e, da_k, start_question=start_question)
