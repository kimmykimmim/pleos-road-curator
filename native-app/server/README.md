# 구름 친구 로컬 대화 서버

이 서버는 성인이 가상의 문장으로 확인하는 합성 시연 전용입니다. 실제 13세 미만 아동의 대화나 개인정보를 입력하면 안 됩니다. 실제 아동 대상 운영은 OpenAI의 Zero Data Retention[서비스 측 데이터 무보관] 승인과 별도의 아동 개인정보 검토가 필요합니다. `store: false` 설정만으로 Zero Data Retention이 적용되지는 않습니다.

## 실행

Node.js 24 이상에서 프로젝트 폴더를 기준으로 실행합니다. 서버는 의존성 설치가 필요 없습니다. API 키를 환경변수 `OPENAI_API_KEY`에 설정하는 작업은 별도로 진행합니다. 서버는 환경 파일을 직접 읽거나 쓰지 않습니다.

```powershell
node server/local-companion.mjs
```

- 기본 모델은 `gpt-6.1-sol`입니다. 필요하면 실행 전에 `OPENAI_MODEL` 환경변수로 바꿉니다.
- 실행 주소는 `127.0.0.1:8791`로 고정됩니다. 외부 네트워크 공개와 실제 서비스 배포를 위한 서버가 아닙니다.
- Android 에뮬레이터는 `adb reverse tcp:8791 tcp:8791` 연결 후 `http://127.0.0.1:8791`을 사용합니다. 장치가 여러 개이면 `adb -s <장치 ID> reverse tcp:8791 tcp:8791`로 대상을 지정합니다.
- 모든 요청에 `X-Scenery-Client: pleos-local-v1` 헤더가 필요합니다. `Origin` 헤더가 있는 브라우저 요청은 거절합니다. 이 헤더는 접근 제한 보조 수단이며 사용자 인증이나 비밀 키가 아닙니다.

## 요청 흐름

`GET /health`는 `{ "ready": true, "mode": "adult-demo", "model": "gpt-6.1-sol" }` 형식입니다. `ready`는 환경변수 설정 여부이며 API 키의 유효성이나 결제 가능 여부를 보증하지 않습니다.

`POST /chat`에는 `Content-Type: application/json` 헤더와 다음 JSON을 보냅니다.

```json
{
  "message": "할머니 집에 가",
  "history": [{ "role": "assistant", "content": "오늘 어디 가?" }],
  "friend": {
    "name": "뭉치",
    "appearance": "선택된 친구는 토끼. 하늘에는 얼굴 없는 고래 배경 구름도 있음.",
    "memories": [{ "source": "구름", "part": "몸", "detail": "구름에서 떠올린 토끼 모습" }]
  }
}
```

1. 요청의 크기와 형식을 확인합니다. 본문은 32 KiB, 새 문장은 500자, 대화 기록은 최대 12개 메시지입니다. 각 기록은 500자 이하의 `user` 또는 `assistant`입니다. 친구 이름은 40자, 모습은 1,000자, 기억은 최대 24개입니다.
2. 입력 전체를 `omni-moderation-latest`로 검토합니다. 문제가 감지되면 고정된 안전 지원 문장으로 답하며 자유대화를 생성하지 않습니다.
3. Responses API에 현재 요청의 기록과 친구 자료만 전달합니다. `store: false`, `reasoning.effort: low`, 최대 출력 700토큰과 엄격한 JSON Schema를 사용합니다.
4. 생성된 문장을 다시 검토한 뒤 `{ "reply": "...", "mood": "happy", "source": "openai" }` 형식으로 반환합니다. 검토에 걸리면 고정 지원 문장으로 바꿉니다. `source`는 OpenAI 대화 경로를 뜻하며, 검토로 대체된 문장은 서버에 정해진 문장입니다.

`mood`는 `neutral`, `happy`, `sad`, `excited`, `sleepy` 중 하나입니다. 대화가 앱의 친구 모습이나 기억을 자동 수정하지 않습니다.

## 중단과 오류

- 전체 대화 요청은 20초 안에 끝나야 합니다. 앱의 연결 취소는 진행 중인 API 호출도 취소합니다. 자동 재시도는 없습니다.
- 분당 최대 20개 대화 요청과 최대 2개 동시 요청을 허용합니다. 초과하면 HTTP 429를 반환합니다.
- 형식 오류는 400, 연결 제한은 403, 너무 큰 요청은 413, 준비되지 않은 키 설정은 503, 서비스 또는 검토 실패는 502, 시간 초과는 504입니다.
- 오류 형식은 `{ "error": { "code": "timeout", "message": "고정된 한국어 안내" } }`입니다. 서비스의 원본 오류, API 키, 발화는 오류 응답이나 로그에 넣지 않습니다. 검토 서비스가 실패했을 때 검토 없이 대답하지 않습니다.
- 대화, 세션, API 응답 ID를 파일이나 서버 메모리 세션으로 저장하지 않습니다. 요청 처리 동안에만 입력과 응답이 존재합니다. 다만 OpenAI에 전달된 데이터의 보관 정책은 별개입니다.

## 모의 테스트

```powershell
node --test server/local-companion.test.mjs
```

테스트는 가짜 키와 모의 API 응답만 사용합니다. 실제 OpenAI API 호출이나 환경 파일 접근은 없습니다. 접근 제한, 입력 크기, 구조화 응답, 기억 근거 전달, 기록 분리, 입력과 출력 검토, 오류 정보 차단, 시간 초과, 연결 취소와 요청 제한을 확인합니다.

## 공식 참고 자료

- [GPT-6.1 Sol 모델](https://developers.openai.com/api/docs/models/gpt-6.1-sol)
- [Responses 구조화 출력](https://developers.openai.com/api/docs/guides/structured-outputs)
- [입력과 출력 검토 API](https://developers.openai.com/api/docs/guides/moderation)
- [18세 미만 이용자 관련 지침](https://developers.openai.com/api/docs/guides/safety-checks/under-18-api-guidance)
- [API 데이터 보관 정책](https://developers.openai.com/api/docs/guides/your-data)
