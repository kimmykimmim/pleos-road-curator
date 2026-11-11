#!/usr/bin/env bash
# Pleos Connect 에뮬레이터를 설치하고 AVD를 만듭니다. 이 앱이 실제로 동작해야 하는 환경입니다.
#
# 이미지 출처는 Pleos Playground 공식 저장소입니다.
#   매니페스트: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml
#   패키지:     system-images;android-34;pleos_car_emulator;arm64-v8a  (Pleos Connect v2.1.0, arm64)
#
# 이미지에는 42dot의 SDK 라이선스가 붙어 있습니다. 내려받아 쓰는 것은 그 라이선스에
# 동의하는 행위입니다. 매니페스트 XML 안에 전문이 들어 있습니다.
#
# sdkmanager 의 사용자 저장소 등록으로는 이 패키지가 보이지 않아, 공식 zip 을 받아
# 표준 레이아웃으로 직접 설치합니다. package.xml 은 매니페스트의 내용을 그대로 옮깁니다.
set -u

ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
JAVA_HOME="${JAVA_HOME:-$HOME/dev/toolchain/jdk-21.0.13+9/Contents/Home}"
export ANDROID_HOME JAVA_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"

PKG_PATH="system-images;android-34;pleos_car_emulator;arm64-v8a"
DEST="$ANDROID_HOME/system-images/android-34/pleos_car_emulator/arm64-v8a"
ZIP_URL="https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp_car_arm64_emulator.zip"
ZIP_SHA1="24d28eb2f18eba5e87c861a7eb2bdccebb049419"
CACHE="${PLEOS_CACHE:-/tmp/pleos-img}"
ZIP="$CACHE/crp_car_arm64_emulator.zip"
AVD_NAME="${PLEOS_AVD:-pleos_connect}"

die() { echo "중단: $*" >&2; exit 1; }

echo "== 1. 이미지 내려받기 =="
mkdir -p "$CACHE"
if [ -f "$DEST/system.img" ]; then
    echo "  이미 설치되어 있습니다: $DEST"
else
    # -C - 로 이어받습니다. 4.6GB 라 중간에 끊기면 처음부터 받지 않도록 합니다.
    curl -L --fail --max-time 7200 -C - -o "$ZIP" "$ZIP_URL" || die "내려받기 실패."
    echo "  SHA-1 확인 중"
    got=$(shasum -a 1 "$ZIP" | cut -d' ' -f1)
    [ "$got" = "$ZIP_SHA1" ] || die "체크섬 불일치. 받은 값 $got, 기대값 $ZIP_SHA1"
    echo "  체크섬 일치"

    echo "== 2. 설치 =="
    # 4.4GB zip 과 4GB 이미지를 동시에 두면 디스크가 모자랍니다. 중간 사본 없이
    # 목적지로 바로 풀고, zip 은 성공한 뒤에 지웁니다.
    free=$(df -k "$ANDROID_HOME" | tail -1 | awk '{print $4}')
    [ "$free" -gt 5000000 ] || die "디스크 여유가 부족합니다. 약 5GB 가 필요합니다."
    rm -rf "$DEST"; mkdir -p "$DEST"
    unzip -q "$ZIP" -d "$DEST" || die "압축 풀기 실패."
    # 공식 zip 이 최상위 폴더 하나로 감싸 있으면 한 단계 끌어올립니다.
    if [ ! -f "$DEST/system.img" ]; then
        inner=$(find "$DEST" -maxdepth 2 -name system.img | head -1)
        [ -n "$inner" ] || die "zip 안에서 system.img 를 찾지 못했습니다."
        sub=$(dirname "$inner")
        if [ "$sub" != "$DEST" ]; then
            mv "$sub"/* "$sub"/.[!.]* "$DEST"/ 2>/dev/null
            rmdir "$sub" 2>/dev/null
        fi
    fi
    [ -f "$DEST/system.img" ] && rm -f "$ZIP"
fi

# sdkmanager 가 설치된 패키지로 인식하려면 package.xml 이 있어야 하고,
# avdmanager 는 이 파일의 path 로 시스템 이미지를 찾습니다.
if [ ! -f "$DEST/package.xml" ]; then
    echo "  package.xml 생성"
    cat > "$DEST/package.xml" <<XML
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns14="http://schemas.android.com/sdk/android/repo/sys-img2/05">
  <localPackage path="$PKG_PATH" obsolete="false">
    <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns14:sysImgDetailsType">
      <api-level>34</api-level>
      <tag><id>android-automotive</id><display>Pleos Connect v2.1.0 APIs</display></tag>
      <vendor><id>pleos</id><display>Pleos Connect v2.1.0</display></vendor>
      <abi>arm64-v8a</abi>
      <abis>arm64-v8a</abis>
    </type-details>
    <revision><major>2</major></revision>
    <display-name>Pleos Connect v2.1.0 APIs arm64 System Image</display-name>
  </localPackage>
</ns2:repository>
XML
fi
[ -f "$DEST/source.properties" ] || cat > "$DEST/source.properties" <<PROPS
Pkg.Desc=Pleos Connect v2.1.0 APIs arm64 System Image
Pkg.Revision=2
AndroidVersion.ApiLevel=34
SystemImage.Abi=arm64-v8a
SystemImage.TagId=android-automotive
SystemImage.TagDisplay=Pleos Connect v2.1.0 APIs
SystemImage.GpuSupport=true
Addon.VendorId=pleos
Addon.VendorDisplay=Pleos Connect v2.1.0
PROPS

[ -f "$DEST/system.img" ] || die "설치 후에도 system.img 가 없습니다."
echo "  설치 확인: $(du -sh "$DEST" | cut -f1)"

echo "== 3. AVD 만들기 =="
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
if ! "$AVDMANAGER" list avd -c 2>/dev/null | grep -qx "$AVD_NAME"; then
    echo no | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$PKG_PATH" --force 2>&1 | grep -v "^Loading" | tail -3
fi
"$AVDMANAGER" list avd -c 2>/dev/null | grep -qx "$AVD_NAME" || die "AVD 생성 실패."
echo "  AVD: $AVD_NAME"

echo
echo "띄우려면:"
echo "  \"$ANDROID_HOME/emulator/emulator\" -avd $AVD_NAME -no-snapshot -gpu host -no-metrics"
