#!/usr/bin/env bash
# 에뮬레이터에서 앱을 설치하고 시연 흐름과 음성 실패 복구를 확인하는 검사 도구입니다.
#
# 이 스크립트가 띄우는 것은 일반 Android Automotive 에뮬레이터입니다. Pleos 에뮬레이터가
# 아닙니다. Pleos TTS, STT, NaviHelper 서비스는 없고 앱은 대체 경로로 동작합니다.
# 실제 내비 연동과 Pleos 음성 검증은 Pleos 에뮬레이터나 차량에서 따로 해야 합니다.
#
# 사용법: tools/emulator-demo-check.sh [결과를_저장할_폴더]
set -u

ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
JAVA_HOME="${JAVA_HOME:-$HOME/dev/toolchain/jdk-21.0.13+9/Contents/Home}"
export ANDROID_HOME JAVA_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
ADB="$ANDROID_HOME/platform-tools/adb"
AVD_NAME="${AVD_NAME:-pleos_check_aaos33}"
IMAGE="system-images;android-33;android-automotive;arm64-v8a"
IMAGE_DIR="$ANDROID_HOME/system-images/android-33/android-automotive/arm64-v8a"
PACKAGE="dev.changbak.explorer"
TTS_PACKAGE="com.google.android.tts"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/validation/emulator-run}"
APK="$ROOT/apk/김규범_길위의큐레이터_release.apk"

mkdir -p "$OUT"
die() { echo "중단: $*" >&2; exit 1; }
shot() { "$ADB" exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; echo "  저장: $1.png"; }

# 화면은 Canvas와 기본 View로 그려져 있고, AAOS에서는 이 앱의 창이 포커스 창이 아니어서
# uiautomator 덤프가 다른 창을 잡습니다. 그래서 1080x600 화면의 좌표로 누릅니다.
tap() { "$ADB" shell input tap "$1" "$2"; }

echo "== 1. AVD 준비 =="
# .installer 스텁만 남은 중단된 설치를 설치 완료로 착각하면 안 됩니다.
[ -f "$IMAGE_DIR/system.img" ] || die "시스템 이미지가 없습니다. sdkmanager \"$IMAGE\" 를 먼저 끝내세요."
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
if ! "$AVDMANAGER" list avd -c 2>/dev/null | grep -qx "$AVD_NAME"; then
    # -d 프로필은 이 이미지의 devices.xml 이 없어 실패하므로 쓰지 않습니다.
    echo no | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$IMAGE" --force 2>&1 | grep -v "^Loading" | tail -2
fi
"$AVDMANAGER" list avd -c 2>/dev/null | grep -qx "$AVD_NAME" || die "AVD 생성 실패."
echo "  AVD: $AVD_NAME"

echo "== 2. 부팅 =="
if ! "$ADB" devices | grep -q "emulator-5554[[:space:]]*device"; then
    if ! "$ADB" devices | grep -q emulator-5554; then
        "$ANDROID_HOME/emulator/emulator" -avd "$AVD_NAME" -no-snapshot -no-audio \
            -gpu swiftshader_indirect -no-boot-anim -port 5554 > "$OUT/emulator.log" 2>&1 &
        emulator_pid=$!
    fi
    for _ in $(seq 180); do
        "$ADB" devices | grep -q "emulator-5554[[:space:]]*device" && break
        if [ -n "${emulator_pid:-}" ] && ! kill -0 "$emulator_pid" 2>/dev/null; then
            tail -20 "$OUT/emulator.log" >&2; die "에뮬레이터가 종료되었습니다. $OUT/emulator.log 확인."
        fi
        sleep 5
    done
fi
"$ADB" devices | grep -q "emulator-5554[[:space:]]*device" || die "에뮬레이터가 adb에 연결되지 않았습니다."
# 이 이미지는 sys.boot_completed 를 설정하지 않습니다. 패키지 관리자가 응답하면 준비된 것으로 봅니다.
for _ in $(seq 60); do
    [ -n "$("$ADB" shell pm path android 2>/dev/null | tr -d '\r')" ] && break
    sleep 5
done
[ -n "$("$ADB" shell pm path android 2>/dev/null | tr -d '\r')" ] || die "패키지 관리자가 준비되지 않았습니다."
echo "  API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r') / $("$ADB" shell getprop ro.build.characteristics | tr -d '\r')"
echo "  automotive 기능: $("$ADB" shell pm list features 2>/dev/null | grep -c 'android.hardware.type.automotive' | tr -d '\r')"

echo "== 3. 설치 =="
[ -f "$APK" ] || die "APK가 없습니다: $APK"
# -g 를 쓰지 않습니다. 위치 권한은 앱이 정상 UI로 요청해야 하며, 검사 도구가 몰래
# 부여하면 실제 권한 흐름을 검증한 것이 아니게 됩니다. 시연 흐름은 권한 없이 됩니다.
"$ADB" install -r "$APK" 2>&1 | tail -1
"$ADB" shell dumpsys package "$PACKAGE" 2>/dev/null | grep -E "versionName=|versionCode=" | head -2 | tr -d '\r' | sed 's/^ */  /'
# 선언 목록이 아니라 실제 런타임 부여 상태를 봅니다. 검사 도구는 권한을 부여하지 않습니다.
"$ADB" shell dumpsys package "$PACKAGE" 2>/dev/null | grep -m1 "ACCESS_FINE_LOCATION: granted" | tr -d '\r' | sed 's/^ */  /'

echo "== 4. 시연 흐름 (손대지 않고 자동 진행) =="
"$ADB" shell am force-stop "$PACKAGE"; "$ADB" logcat -c
"$ADB" shell am start -n "$PACKAGE/.MainActivity" >/dev/null 2>&1
sleep 6
shot "a-01-시작"
[ -n "$("$ADB" shell dumpsys activity activities 2>/dev/null | grep -c "$PACKAGE/.MainActivity")" ] || die "앱이 실행되지 않았습니다."
tap 783 430   # "한강의 과학 여행"
sleep 8;  shot "a-02-예고"
sleep 24; shot "a-03-관찰"
sleep 24; shot "a-04-이야기"
sleep 28; shot "a-05-역할놀이"

echo "== 5. 음성 실패 복구 =="
# 해설 도중에 TTS 엔진을 죽입니다. 연결만 끊기므로 완료 콜백도 오류 콜백도 오지 않습니다.
# 고치기 전에는 화면이 '03 장소의 이야기'에 그대로 멈췄습니다.
"$ADB" shell am force-stop "$PACKAGE"; "$ADB" logcat -c
"$ADB" shell am start -n "$PACKAGE/.MainActivity" >/dev/null 2>&1
sleep 6; tap 783 430
sleep 52; shot "b-01-종료직전"
echo "  TTS 엔진 강제 종료"
"$ADB" shell am force-stop "$TTS_PACKAGE"
sleep 30; shot "b-02-복구후"
echo "  TTS 연결 끊김: $("$ADB" logcat -d 2>/dev/null | grep -c 'TextToSpeech: Disconnected from TTS engine')"
echo "  끊긴 뒤 다음 대사 시도: $("$ADB" logcat -d 2>/dev/null | grep -c 'playSilentUtterance failed: not bound')"
echo "  b-01 은 '03 장소의 이야기', b-02 는 '04' 여야 복구가 된 것입니다."

echo "== 6. 오류 확인 =="
echo "  FATAL EXCEPTION: $("$ADB" logcat -d 2>/dev/null | grep -c 'FATAL EXCEPTION')"
echo "  오디오 포커스 승인: $("$ADB" logcat -d 2>/dev/null | grep -c "CarAudioFocus.*$PACKAGE.*REQUEST_GRANTED")"
echo "  한국어 TTS 합성 요청: $("$ADB" logcat -d 2>/dev/null | grep -c 'Synthesis request for locale kor-KOR')"
"$ADB" logcat -d -v time 2>/dev/null | grep -E "FATAL|CarAudioFocus.*$PACKAGE|Synthesis request|not bound to TTS" > "$OUT/logcat.txt"
echo
echo "결과 폴더: $OUT"
