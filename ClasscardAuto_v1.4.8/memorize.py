from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException

NEXT_PERCENT_XPATH = '//*[@id="study_end"]/div[2]/div[3]/div[3]/div[1]/a'
CARD_SELECTOR = ".CardItem.showing.current"

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
            driver.save_screenshot("memorize_next_percent_error.png")
        except Exception:
            pass
        raise RuntimeError(
            f"{next_percent}% 버튼 자동 클릭 또는 다음 카드 화면 진입에 실패했습니다. "
            "memorize_next_percent_error.png를 확인해 주세요."
        ) from exc

script = r"""
(async()=>{
    const s=ms=>new Promise(r=>setTimeout(r,ms)),
    w=async(f,t=2000)=>{
        let x=Date.now();
        while(window.__memorizeRunning&&Date.now()-x<t){
            let r=f();
            if(r)return r;
            await s(50);
        }
        return null;
    };

    if(window.__memorizeRunning){
        console.log('⚠️ 이미 실행 중이에요.');
        return;
    }

    window.__memorizeRunning=true;
    let count=0;

    try{
        while(window.__memorizeRunning){
            const end=document.querySelector("#study_end");
            if(end && getComputedStyle(end).display!=="none" && end.getClientRects().length>0)break;

            let c=document.querySelector('.CardItem.showing.current');
            if(!c)break;

            let v=c.querySelector('.card-cover');

            if(v){
                v.click();
                let ready=await w(()=>c.querySelector('.btn_kown_o'));
                if(!ready)break;
            }

            if(!window.__memorizeRunning)break;

            let k=c.querySelector('.btn_kown_o');
            if(!k)break;

            let answerDelay=1000+Math.random()*1000;
            console.log(`⏳ 기억 여부 선택 전 ${(answerDelay/1000).toFixed(2)}초 대기`);
            await s(answerDelay);
            if(!window.__memorizeRunning)break;

            k=c.querySelector('.btn_kown_o');
            if(!k)break;
            k.click();
            count++;
            console.log(`🟢 ${count}번째 카드 처리`);

            let n=await w(()=>document.querySelector('.btnNextCard2'));
            if(!n||!window.__memorizeRunning)break;

            n.click();

            await w(()=>{
                let x=document.querySelector('.CardItem.showing.current');
                return x&&x!==c;
            });
        }
    }finally{
        window.__memorizeRunning=false;
        console.log(`✅ 암기 자동화 종료 · 처리한 카드: ${count}`);
    }
})();
"""

driver = webdriver.Chrome()

try:
    driver.get("https://www.classcard.net/")

    input(
        "Chrome에서 로그인하고 암기 학습 카드 화면까지 이동한 뒤 "
        "여기서 Enter를 누르세요..."
    )

    print(
        "\n암기 학습 목표를 선택해 주세요.\n"
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

    print(f"선택한 목표: {rounds * 100}% ({rounds}회 실제 암기 학습)")

    WebDriverWait(driver, 30).until(
        lambda d: d.find_elements(
            By.CSS_SELECTOR, ".CardItem.showing.current"
        )
    )

    for round_index in range(1, rounds + 1):
        print(f"\n===== 암기 학습 {round_index}/{rounds}회 ({round_index * 100}%) =====")
        driver.execute_script(script)
        print("🔥 암기 자동화를 시작했어요!")

        # Wait until the injected JavaScript finishes the current pass.
        WebDriverWait(driver, 60 * 60).until(
            lambda d: d.execute_script(
                "return window.__memorizeRunning === false || "
                "typeof window.__memorizeRunning === 'undefined'"
            )
        )
        print(f"✅ 암기 학습 {round_index}/{rounds}회차 종료")

        if round_index < rounds:
            advance_to_next_percent(driver, (round_index + 1) * 100)

    print(f"\n선택한 암기 학습 {rounds * 100}% 과정이 끝났습니다.")
    input("Chrome 화면을 확인한 뒤 Enter를 누르면 프로그램을 종료합니다...")

finally:
    driver.quit()