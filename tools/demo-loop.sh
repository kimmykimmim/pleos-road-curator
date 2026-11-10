#!/usr/bin/env bash
# 에뮬레이터 창에서 시연을 계속 반복해 보여 줍니다. 멈추려면 Ctrl+C 를 누르세요.
#
# 한 바퀴: 01 먼저 상상해 보기 → 02 창밖에서 만나기 → 03 장소의 이야기
#          → 04 역할 놀이 → 오늘의 발견 → 처음으로
ADB="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}/platform-tools/adb"
PKG=dev.changbak.explorer
ACT="$PKG/.MainActivity"

resumed() { "$ADB" shell dumpsys activity activities 2>/dev/null | grep -q "ResumedActivity.*$PKG"; }

round=0
while true; do
    round=$((round+1))
    "$ADB" shell am force-stop "$PKG"
    "$ADB" shell am start -n "$ACT" >/dev/null 2>&1
    # 시작을 확인한 뒤에 누릅니다. 바로 누르면 아직 그려지지 않은 화면을 눌러
    # 아무 일도 일어나지 않고 빈 화면만 남습니다.
    for _ in $(seq 20); do resumed && break; sleep 1; done
    resumed || { echo "앱이 올라오지 않았습니다. 다시 시도합니다."; sleep 3; continue; }
    sleep 3
    "$ADB" shell input tap 783 430          # "한강의 과학 여행"
    echo "$(date +%H:%M:%S)  ${round}번째 시연 시작"
    sleep 30; echo "            02 창밖에서 만나기"
    sleep 26; echo "            03 장소의 이야기"
    sleep 30; echo "            04 역할 놀이"
    sleep 8
    "$ADB" shell input tap 783 311          # 역할 놀이 선택지 하나
    sleep 16; echo "            오늘의 발견. 잠시 뒤 처음부터 다시 합니다."
    sleep 10
done
