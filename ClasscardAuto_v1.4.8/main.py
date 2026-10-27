import time
import warnings
import sys
from bs4 import BeautifulSoup
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException
from handler.spelling_learning import SpellingLearning
from selenium.webdriver.chrome.options import Options

# 함수불러오기
from utility import (
    word_get,
    choice_set,
    choice_class,
)

warnings.filterwarnings("ignore", category=DeprecationWarning)

def choose_spelling_rounds() -> int:
    """Choose how many complete spelling-learning passes to perform.

    100% means one actual pass through the set, 200% means two passes,
    and 300% means three passes. This does not fabricate progress records.
    """
    print(
        "\n스펠 학습 목표를 선택해 주세요.\n"
        "[1] 100% 학습 (전체 단어 1회)\n"
        "[2] 200% 학습 (전체 단어 2회)\n"
        "[3] 300% 학습 (전체 단어 3회)"
    )
    while True:
        try:
            choice = int(input(">>> ").strip())
            if choice in (1, 2, 3):
                return choice
            print("1, 2, 3 중에서 선택해 주세요.")
        except ValueError:
            print("숫자 1, 2, 3 중 하나를 입력해 주세요.")
        except KeyboardInterrupt:
            raise

def prepare_set_for_next_spelling_round(browser, set_url: str) -> None:
    """Return to the selected set and select the full range before another pass."""
    browser.get(set_url)
    range_dropdown = (
        By.CSS_SELECTOR,
        "body > div.test > div.p-b-sm > div.set-body.m-t-25.m-b-lg > "
        "div.m-b-md.pos-relative > div.dropdown > a",
    )
    range_all = (
        By.CSS_SELECTOR,
        "body > div.test > div.p-b-sm > div.set-body.m-t-25.m-b-lg > "
        "div.m-b-md.pos-relative > div.dropdown.open > ul > li:nth-child(1) > a",
    )
    try:
        dropdown = WebDriverWait(browser, 15).until(EC.element_to_be_clickable(range_dropdown))
        dropdown.click()
        all_option = WebDriverWait(browser, 10).until(EC.element_to_be_clickable(range_all))
        all_option.click()
        time.sleep(0.7)
    except TimeoutException as exc:
        try:
            browser.save_screenshot("spelling_repeat_range_error.png")
        except Exception:
            pass
        raise RuntimeError(
            "다음 스펠 학습 회차를 위해 전체 학습 구간을 선택하지 못했습니다. "
            "spelling_repeat_range_error.png를 확인해 주세요."
        ) from exc

# 예외로 프로그램이 끝나더라도 터미널이 바로 닫히지 않게 유지
_original_excepthook = sys.excepthook
def _pause_on_unhandled_error(exc_type, exc_value, exc_tb):
    _original_excepthook(exc_type, exc_value, exc_tb)
    try:
        input("\n오류가 발생했습니다. 위 오류 내용을 확인한 뒤 Enter를 누르면 종료됩니다...")
    except EOFError:
        pass
sys.excepthook = _pause_on_unhandled_error

print("Chrome을 실행하고 있습니다...")

# Chrome 옵션 설정
chrome_options = Options()
chrome_options.add_argument("--disable-blink-features=AutomationControlled")
chrome_options.add_experimental_option("excludeSwitches", ["enable-automation"])
chrome_options.add_experimental_option("useAutomationExtension", False)
chrome_options.add_argument("--log-level=1")

# 브라우저를 열어 사용자가 직접 로그인하도록 함.
# 자동 입력/로그인 버튼 클릭은 사이트 로그인 화면 변경이나 경고창 문제를 일으킬 수 있어 사용하지 않음.
driver = webdriver.Chrome(options=chrome_options)
driver.get("https://www.classcard.net/Login")

print("Chrome에서 클래스카드에 직접 로그인해 주세요.")
print("로그인 후 클래스 목록이 표시되고, Chrome 창을 닫지 않은 상태에서 이 터미널로 돌아오세요.")
input("로그인을 마쳤으면 여기서 Enter를 누르세요...")

# 로그인 과정에서 탭이 바뀌었을 수 있으므로 살아 있는 창을 다시 선택함.
def switch_to_live_window(browser):
    try:
        handles = browser.window_handles
    except Exception as exc:
        raise RuntimeError(
            "Chrome 자동화 세션에 접근할 수 없습니다. Chrome을 닫지 말고 프로그램을 다시 실행해 주세요."
        ) from exc

    if not handles:
        raise RuntimeError(
            "Chrome 창이 모두 닫혀 있습니다. Chrome 창을 열어 둔 상태에서 로그인한 뒤 Enter를 눌러 주세요."
        )

    last_error = None
    for handle in reversed(handles):
        try:
            browser.switch_to.window(handle)
            browser.execute_script("return document.readyState")
            return
        except Exception as exc:
            last_error = exc

    raise RuntimeError(
        "열려 있는 Chrome 탭을 선택할 수 없습니다. Chrome을 닫지 말고 프로그램을 다시 실행해 주세요."
    ) from last_error

switch_to_live_window(driver)
wait = WebDriverWait(driver, 45)
try:
    wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, ".left-class-list, .left-menu")))
except TimeoutException:
    # 페이지 구조가 변경된 경우를 위해 실제 상태를 출력하고 스크린샷을 저장함.
    try:
        print("현재 주소:", driver.current_url)
        print("현재 페이지 제목:", driver.title)
        driver.save_screenshot("classcard_debug.png")
        print("현재 화면을 classcard_debug.png 파일로 저장했습니다.")
    except Exception as exc:
        print("페이지 상태를 읽지 못했습니다:", exc)
    raise RuntimeError(
        "로그인 후 클래스 목록을 찾지 못했습니다. Chrome에서 클래스 목록 화면이 열린 상태인지 확인해 주세요."
    )

# 클래스 선택
class_dict = {}
class_list_element = driver.find_element(
    By.CSS_SELECTOR,
    "body > div.mw-1080 > div:nth-child(6) > div > div > div.left-menu > div.left-item-group.p-t-none.p-r-lg > div.m-t-sm.left-class-list",
)
class_count = len(class_list_element.find_elements(By.TAG_NAME, "a"))
for class_item, i in zip(
    class_list_element.find_elements(By.TAG_NAME, "a"),
    range(class_count),
):
    class_temp = {}
    class_temp["class_name"] = class_item.text
    class_temp["class_id"] = class_item.get_attribute("href").split("/")[-1]
    if class_temp["class_id"] == "joinClass":
        break
    class_dict[i] = class_temp

if len(class_dict) == 0:
    print("클래스가 없습니다.")
    quit()
elif len(class_dict) == 1:
    choice_class_val = 0
else:
    choice_class_val = choice_class(class_dict=class_dict)  # 클래스 입력

class_id = class_dict[choice_class_val].get("class_id")  # 클래스 아이디 가져오기
driver.get(f"https://www.classcard.net/ClassMain/{class_id}")  # 클래스 페이지로 이동
time.sleep(1)  # 로딩 대기

# 세트 선택
sets_div = driver.find_element(
    By.XPATH, "/html/body/div[1]/div[2]/div/div/div[2]/div[3]/div"
)
sets = sets_div.find_elements(By.CLASS_NAME, "set-items")
sets_count = len(sets)
sets_dict = {}
for set_item, i in zip(sets, range(sets_count)):
    set_temp = {}
    set_temp["card_num"] = (
        set_item.find_element(By.TAG_NAME, "a").find_element(By.TAG_NAME, "span").text
    )  # 카드 개수 가져오기 예) "10 카드"
    set_temp["title"] = set_item.find_element(By.TAG_NAME, "a").text.replace(
        set_temp["card_num"], ""
    )  # 카드 개수 제거
    set_temp["set_id"] = set_item.find_element(By.TAG_NAME, "a").get_attribute(
        "data-idx"
    )  # 세트 아이디 가져오기
    sets_dict[i] = set_temp

set_choice = choice_set(sets_dict)  # 세트 입력
set_site = (f"https://www.classcard.net/set/{sets_dict[set_choice]['set_id']}/{class_id}")
driver.get(set_site)  # 세트 페이지로 이동
time.sleep(1)  # 로딩 대기

# 단어 저장
driver.find_element(
    By.CSS_SELECTOR,
    "body > div.test > div.p-b-sm > div.set-body.m-t-25.m-b-lg > div.m-b-md.pos-relative > div.dropdown > a",
).click()  # 학습구간 선택
driver.find_element(
    By.CSS_SELECTOR,
    "body > div.test > div.p-b-sm > div.set-body.m-t-25.m-b-lg > div.m-b-md.pos-relative > div.dropdown.open > ul > li:nth-child(1) > a",
).click()  # 학습구간 전체로 변경
html = BeautifulSoup(driver.page_source, "html.parser")  # 페이지 소스를 html로 파싱
cards_ele = html.find("div", class_="flip-body")  # 카드들을 찾음
num_d = len(cards_ele.find_all("div", class_="flip-card")) + 1  # 카드의 개수를 구함
time.sleep(0.5)  # 로딩 대기
word_d = word_get(driver, num_d)  # 단어를 가져옴
da_e, da_k, da_kyn = word_d


# 이 빌드는 스펠 학습만 지원합니다. 다른 학습 모드 메뉴는 표시하지 않습니다.
print("\n스펠 학습 전용 모드입니다.")
print("각 스펠 답안 입력 전 1.00~2.00초 사이에서 무작위로 대기합니다.")
round_count = choose_spelling_rounds()
target_percent = round_count * 100
print(f"선택한 목표: {target_percent}% ({round_count}회 실제 스펠 학습)")

raw_start = input("몇 번 문제부터 처리할까요? 기본값 1, 이어서 시작하려면 번호를 입력: ").strip()
try:
    start_question = int(raw_start) if raw_start else 1
except ValueError:
    print("숫자가 아니어서 기본값 1을 사용합니다.")
    start_question = 1
start_question = max(1, min(start_question, max(1, num_d - 1)))

batch_number = 1
while True:
    print(f"\n===== 스펠 학습 묶음 {batch_number} 시작: 목표 {target_percent}% =====")
    for round_index in range(1, round_count + 1):
        if round_index > 1:
            print(f"\n{round_index}회차 시작을 위해 선택한 세트를 다시 엽니다...")
            prepare_set_for_next_spelling_round(driver, set_site)

        print(f"\n===== 스펠 학습 {round_index}/{round_count}회 ({round_index * 100}% 기준) =====")
        controller = SpellingLearning(driver=driver)
        controller.run(
            num_d=num_d,
            word_d=word_d,
            start_question=start_question,
        )

        if round_index < round_count:
            print(f"{round_index * 100}% 회차가 끝났습니다. 다음 회차를 준비합니다.")
            time.sleep(1.5)

    print(f"\n선택한 스펠 학습 {target_percent}% 과정이 끝났습니다.")
    while True:
        again = input("한 번 더 할까요? (y/n): ").strip().casefold()
        if again in {"y", "yes", "네", "예", "ㅇ"}:
            print("같은 학습 과정을 다시 준비합니다...")
            prepare_set_for_next_spelling_round(driver, set_site)
            batch_number += 1
            break
        if again in {"n", "no", "아니요", "아니오", "ㄴ"}:
            print("학습을 종료합니다.")
            input("Chrome 화면을 확인한 뒤 Enter를 누르면 프로그램을 종료합니다...")
            raise SystemExit(0)
        print("y 또는 n으로 입력해 주세요.")
