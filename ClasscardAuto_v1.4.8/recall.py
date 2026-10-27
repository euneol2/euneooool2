from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException

NEXT_PERCENT_XPATH = '//*[@id="study_end"]/div[2]/div[3]/div[3]/div[1]/a'
CARD_SELECTOR = ".CardItem.current.showing"

def advance_to_next_percent(driver, next_percent):
    print(f"\n현재 학습 완료. {next_percent}% 버튼을 자동으로 누릅니다...")
    try:
        button = WebDriverWait(driver, 30).until(
            EC.element_to_be_clickable((By.XPATH, NEXT_PERCENT_XPATH))
        )
        button.click()
        WebDriverWait(driver, 30).until(
            EC.invisibility_of_element_located((By.ID, "study_end"))
        )
        WebDriverWait(driver, 30).until(
            lambda d: d.find_elements(By.CSS_SELECTOR, CARD_SELECTOR)
        )
        print(f"✅ {next_percent}% 학습 화면으로 이동했습니다.")
    except TimeoutException as exc:
        try:
            driver.save_screenshot("recall_next_percent_error.png")
        except Exception:
            pass
        raise RuntimeError(
            f"{next_percent}% 버튼 자동 클릭 또는 다음 카드 화면 진입에 실패했습니다. "
            "recall_next_percent_error.png를 확인해 주세요."
        ) from exc

script = r"""
(()=>{
    clearInterval(window.__recallAuto);
    clearTimeout(window.__recallNextTimer);
    clearTimeout(window.__recallAnswerTimer);
    window.__recallAuto = null;
    window.__recallRunning = true;

    let lastCard = null;
    let lastSeenAt = Date.now();
    const stop = (message) => {
        clearInterval(window.__recallAuto);
        clearTimeout(window.__recallNextTimer);
        clearTimeout(window.__recallAnswerTimer);
        window.__recallAuto = null;
        window.__recallRunning = false;
        console.log(message || '✅ 리콜 회차 종료');
    };

    const run = () => {
        const end = document.querySelector("#study_end");
        if (end && getComputedStyle(end).display !== "none" && end.getClientRects().length > 0) {
            stop("✅ 리콜 학습 완료 화면을 감지했습니다.");
            return;
        }

        const card = document.querySelector('.CardItem.current.showing');
        if (!card) {
            if (Date.now() - lastSeenAt > 3000) stop('✅ 카드 화면이 종료되어 이번 리콜 회차를 마쳤습니다.');
            return;
        }
        lastSeenAt = Date.now();
        if (card === lastCard) return;

        const choices = [...card.querySelectorAll('.cc-table.middle.fill-parent-w')];
        const answer = choices.find(item => item.classList.contains('answer'));
        if (!answer) return;

        const handler = window.jQuery?._data(answer, 'events')?.click?.[0]?.handler;
        if (!handler) return;

        lastCard = card;
        const delay = 1000 + Math.random() * 1000;
        console.log(`⏳ 정답 선택 전 ${(delay / 1000).toFixed(2)}초 대기`);

        window.__recallAnswerTimer = setTimeout(() => {
            if (!window.__recallRunning) return;
            if (document.querySelector('.CardItem.current.showing') !== card) {
                console.log('현재 카드가 바뀌어 이번 선택을 건너뜁니다.');
                return;
            }

            const event = window.jQuery.Event('click');
            event.target = answer;
            event.currentTarget = answer;
            event.which = 1;
            Object.defineProperty(event, 'originalEvent', {
                value: { type: 'click', isTrusted: true, target: answer, currentTarget: answer, which: 1 },
                configurable: true
            });

            console.log(`🟢 정답 ${choices.indexOf(answer) + 1}번 선택`);
            handler.call(answer, event);

            window.__recallNextTimer = setTimeout(() => {
                if (!window.__recallRunning) return;
                const next = document.querySelector('.btn-short-change-next');
                if (!next) {
                    stop('✅ 다음 카드 버튼이 없어 이번 리콜 회차를 종료합니다.');
                    return;
                }
                console.log('➡️ 다음 카드');
                window.jQuery(next).trigger('click');
            }, 700);
        }, delay);
    };

    run();
    window.__recallAuto = setInterval(run, 200);
    console.log('🔥 리콜 자동화 ON');
})();
"""

driver = webdriver.Chrome()

try:
    driver.get("https://www.classcard.net/")

    input(
        "Chrome에서 로그인하고 리콜학습 카드 화면까지 이동한 뒤 "
        "여기서 Enter를 누르세요..."
    )

    print(
        "\n리콜 학습 목표를 선택해 주세요.\n"
        "[1] 100% 학습 (전체 카드 1회)\n"
        "[2] 200% 학습 (전체 카드 2회)\n"
        "[3] 300% 학습 (전체 카드 3회)"
    )
    while True:
        try:
            rounds = int(input(">>> ").strip())
            if rounds in (1, 2, 3):
                break
            print("1, 2, 3 중에서 선택해 주세요.")
        except ValueError:
            print("숫자 1, 2, 3 중 하나를 입력해 주세요.")
        except KeyboardInterrupt:
            raise SystemExit(0)

    print(f"선택한 목표: {rounds * 100}% ({rounds}회 리콜 학습)")

    WebDriverWait(driver, 30).until(
        lambda d: d.find_elements(
            By.CSS_SELECTOR, ".CardItem.current.showing"
        )
    )

    for round_index in range(1, rounds + 1):
        print(f"\n===== 리콜 학습 {round_index}/{rounds}회 ({round_index * 100}%) =====")
        driver.execute_script(script)
        print("🔥 리콜 자동화를 시작했어요!")

        WebDriverWait(driver, 60 * 60).until(
            lambda d: d.execute_script(
                "return window.__recallRunning === false || "
                "typeof window.__recallRunning === 'undefined'"
            )
        )
        print(f"✅ 리콜 학습 {round_index}/{rounds}회차 종료")

        if round_index < rounds:
            advance_to_next_percent(driver, (round_index + 1) * 100)

    print(f"\n선택한 리콜 학습 {rounds * 100}% 과정이 끝났습니다.")
    input("Chrome 화면을 확인한 뒤 Enter를 누르면 프로그램을 종료합니다...")

finally:
    driver.quit()
