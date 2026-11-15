# 서명 키 안내

이 폴더의 키스토어[앱 서명에 쓰는 비밀 키 파일] 자체와 비밀번호는 저장소에 올라가지
않습니다. `.gitignore`가 `*.jks`와 `keystore.properties`를 막고 있습니다.

## 지금 쓰는 키

- 파일: `signing/contest-release.jks`
- 형식: PKCS12, RSA 4096비트, 유효 기간 10950일
- 별칭: `contest`
- 소유자: `CN=Kyubeom Kim, OU=Road Curator, O=Individual Entry, L=Seoul, C=KR`
- 인증서 SHA-256: `ffab697cfd4305fa022824870aca11dd02dc536ec89ab20f149b5c63aabfed42`

제출한 APK가 이 키로 서명되었는지 확인하는 방법입니다.

```bash
apksigner verify --print-certs apk/김규범_창밖을보라_release.apk
```

## 다른 컴퓨터에서 release 빌드를 하려면

1. 키스토어 파일 `contest-release.jks`를 이 `signing` 폴더에 둡니다. 이 파일은 깃으로
   오지 않으므로 별도로 옮겨야 합니다.
2. `native-app/keystore.properties`를 아래 내용으로 만듭니다. 비밀번호는 키를 만든
   사람에게 받습니다.

```properties
storeFile=../signing/contest-release.jks
storePassword=받은_비밀번호
keyAlias=contest
keyPassword=받은_비밀번호
```

3. 빌드합니다.

```bash
cd native-app
./gradlew assembleRelease
```

`keystore.properties`가 없으면 빌드는 성공하지만 APK가 서명되지 않아 설치할 수 없습니다.
`app/build.gradle.kts`가 파일 유무를 보고 서명 설정을 붙입니다.

## 키를 새로 만들어야 하면

기존 키를 잃으면 같은 서명으로 업데이트할 수 없습니다. 공모전 제출용이라 새로 만들어도
문제는 없지만, 설치된 앱을 덮어쓰려면 기기에서 먼저 지워야 합니다.

```bash
keytool -genkeypair -v \
  -keystore signing/contest-release.jks -storetype PKCS12 \
  -alias contest -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=이름, OU=Road Curator, O=Individual Entry, L=Seoul, C=KR"
```

비밀번호는 명령이 물어볼 때 입력합니다. 명령줄에 적으면 셸 기록에 남습니다.
