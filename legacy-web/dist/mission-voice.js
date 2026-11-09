import { StoryVoice } from './voice.js';

const RETRY_DELAY_MS = 600;
const MAX_RETRIES = 2;
const COMMAND_GUIDE = '“찾았어”, “못 찾았어”, “힌트”, “쉬자”처럼 짧게 말해줘요.';
const LISTENING_BREAK = '천천히 둘러봐요. 다시 말하고 싶으면 마이크를 눌러줘요.';

// Match complete, short commands. A negative answer must never become "found".
const COMMANDS = [
  ['skip', new Set(['못찾았어', '못찾았어요', '못찾겠어', '못찾겠어요', '못봤어', '못봤어요', '안보여', '안보여요', '넘어가', '넘어가자', '넘겨줘', '넘겨주세요', '다음으로'])],
  ['pause', new Set(['쉬자', '쉬어요', '쉴래', '잠깐멈춰', '잠깐멈춰줘', '잠깐만', '잠깐쉬자', '멈춰', '멈춰줘', '그만', '그만할래'])],
  ['found', new Set(['찾았어', '찾았어요', '찾았다', '봤어', '봤어요', '발견했어', '발견했어요'])],
  ['same', new Set(['그대로야', '그대로예요', '비슷해', '비슷해요', '똑같아', '똑같아요', '아직그대로야'])],
  ['different', new Set(['달라졌어', '달라졌어요', '바뀌었어', '바뀌었어요', '풍경달라졌어', '풍경달라졌어요', '풍경이달라졌어', '풍경이달라졌어요', '풍경바뀌었어', '풍경바뀌었어요', '풍경이바뀌었어', '풍경이바뀌었어요'])],
  ['changed', new Set(['지나갔어', '지나갔어요'])],
  ['hint', new Set(['힌트', '힌트줘', '힌트주세요', '힌트알려줘'])],
  ['repeat', new Set(['다시들려줘', '다시들려주세요', '다시말해줘', '다시말해주세요', '한번더', '한번더들려줘'])],
  ['undo', new Set(['잘못눌렀어', '잘못눌렀어요', '잘못말했어', '잘못말했어요', '방금취소', '방금거취소'])],
];

export function parseMissionAction(text) {
  if (typeof text !== 'string' || text.length > 80) return null;
  const command = text.normalize('NFKC').replace(/[\s\p{P}]/gu, '');
  if (!command) return null;
  for (const [action, phrases] of COMMANDS) {
    if (phrases.has(command)) return action;
  }
  return null;
}

function friendlyError(code) {
  if (['not-allowed', 'service-not-allowed', 'NotAllowedError', 'SecurityError'].includes(code)) {
    return '마이크를 사용할 수 없어요. 보호자와 마이크 허용을 확인해 주세요.';
  }
  if (['audio-capture', 'NotFoundError', 'NotReadableError'].includes(code)) {
    return '마이크를 찾지 못했어요. 화면의 버튼으로 이어갈 수 있어요.';
  }
  if (['unsupported', 'language-not-supported', 'bad-grammar'].includes(code)) {
    return '이 브라우저에서는 말로 하기를 사용할 수 없어요. 화면의 버튼으로 이어가요.';
  }
  return '지금은 목소리를 듣기 어려워요. 화면의 버튼으로 이어갈 수 있어요.';
}

/** Microphone opt-in belongs to an explicit user action calling enable(). */
export class MissionVoice {
  constructor({ onState, onAction, onError, onText } = {}) {
    this.onState = typeof onState === 'function' ? onState : () => {};
    this.onAction = typeof onAction === 'function' ? onAction : () => {};
    this.onError = typeof onError === 'function' ? onError : () => {};
    this.onText = typeof onText === 'function' ? onText : () => {};
    this._enabled = false;
    this._disposed = false;
    this._suspending = false;
    this._state = 'idle';
    this._generation = 0;
    this._turn = null;
    this._speechToken = null;
    this._timer = null;
    this._failures = 0;
    this._guided = false;
    this._document = globalThis.document;
    this._voice = new StoryVoice({
      onState: state => this._handleState(state),
      onTranscript: (text, options) => this._handleText(text, options),
      onError: (message, metadata) => this._handleError(message, metadata),
    });
    this._onVisibility = () => { if (this._document?.hidden) this.suspend(); };
    this._document?.addEventListener?.('visibilitychange', this._onVisibility);
  }

  get supportedRecognition() { return !this._disposed && this._voice.supportedRecognition; }
  get enabled() { return this._enabled && !this._disposed; }

  _notify(callback, ...args) {
    try { callback(...args); } catch { /* Host callbacks cannot strand an active microphone. */ }
  }

  _setState(state) {
    if (state === this._state || this._disposed) return;
    this._state = state;
    this._notify(this.onState, state);
  }

  _handleState(state) {
    if (state === 'speaking' && this._speechToken !== this._generation) return;
    if ((state === 'starting' || state === 'listening') && !this._currentTurn()) return;
    this._setState(state);
  }

  _canListen(token = this._generation) {
    return this.enabled && !this._suspending && !this._document?.hidden && token === this._generation;
  }

  _currentTurn() {
    return this._turn && !this._turn.settled && this._canListen(this._turn.generation) ? this._turn : null;
  }

  _clearTimer() {
    if (this._timer !== null) clearTimeout(this._timer);
    this._timer = null;
  }

  _cancelCurrent() {
    const token = ++this._generation;
    this._clearTimer();
    this._turn = null;
    this._speechToken = null;
    this._voice.stopListening({ discard: true });
    if (token === this._generation) this._voice.cancelSpeech();
    return token;
  }

  /** Explicit opt-in only. True also includes listening queued after an active prompt. */
  enable() {
    if (this._disposed || this._suspending || this._document?.hidden) return false;
    if (this._speechToken === this._generation) {
      if (!this.supportedRecognition) {
        this._enabled = false;
        this._notify(this.onError, friendlyError('unsupported'), { source: 'recognition', code: 'unsupported' });
        return false;
      }
      // say(prompt); enable() keeps the prompt audible and delays the first mic request.
      this._enabled = true;
      this._failures = 0;
      this._guided = false;
      return true;
    }
    const token = this._cancelCurrent();
    if (token !== this._generation || this._disposed) return false;
    this._enabled = true;
    this._failures = 0;
    this._guided = false;
    return this._startListening(token);
  }

  _startListening(token) {
    if (!this._canListen(token) || this._speechToken !== null) return false;
    this._clearTimer();
    const turn = { generation: token, settled: false };
    this._turn = turn;
    const started = this._voice.startListening();
    if (!started && this._turn === turn && this._canListen(token)) {
      this._disableWithMessage(friendlyError('start-failed'), 'start-failed');
    }
    return started && this._canListen(token);
  }

  _scheduleListening(token) {
    if (!this._canListen(token) || this._speechToken !== null) return;
    this._clearTimer();
    this._timer = setTimeout(() => {
      this._timer = null;
      if (this._canListen(token)) this._startListening(token);
    }, RETRY_DELAY_MS);
  }

  async say(text, { enabled = true, volume = 0.6 } = {}) {
    if (this._disposed) return false;
    if (this._document?.hidden) { this.suspend(); return false; }
    const token = this._cancelCurrent();
    if (token !== this._generation || this._disposed) return false;
    this._speechToken = token;
    const completed = await this._voice.speak(text, { enabled, volume });
    if (token !== this._generation || this._disposed) return false;
    this._speechToken = null;
    this._scheduleListening(token);
    return completed;
  }

  _handleText(text, { final = false } = {}) {
    const turn = this._currentTurn();
    if (!turn) return;
    if (final) {
      turn.settled = true;
      this._turn = null;
    }
    this._notify(this.onText, text, { final });
    if (!final || !this._canListen(turn.generation)) return;
    const action = parseMissionAction(text);
    if (!action) { this._recover('unrecognized', turn.generation); return; }
    this._failures = 0;
    this._guided = false;
    this._notify(this.onAction, action, { text });
    // A host's say() or suspend() changes the generation and owns the next step.
    this._scheduleListening(turn.generation);
  }

  _handleError(_message, { source = 'recognition', code = 'unknown' } = {}) {
    if (this._disposed) return;
    if (source === 'speech') {
      if (this._speechToken === this._generation) {
        this._notify(this.onError, '음성 안내가 나오지 않아요. 화면을 보며 이어갈 수 있어요.', { source, code });
      }
      return;
    }
    const turn = this._currentTurn();
    if (!turn) return;
    turn.settled = true;
    this._turn = null;
    if (code === 'no-speech' || code === 'unfinished-speech') {
      this._recover(code === 'no-speech' ? 'silence' : 'unrecognized', turn.generation);
      return;
    }
    this._disableWithMessage(friendlyError(code), code);
  }

  _recover(reason, token) {
    if (!this._canListen(token)) return;
    this._failures += 1;
    if (this._failures > MAX_RETRIES) {
      this._disableWithMessage(LISTENING_BREAK, 'retry-limit');
      return;
    }
    if (reason === 'unrecognized' && !this._guided) {
      this._guided = true;
      this._notify(this.onError, COMMAND_GUIDE, { source: 'recognition', code: 'unrecognized' });
    }
    this._scheduleListening(token);
  }

  _disableWithMessage(message, code) {
    this.suspend();
    if (!this._disposed) this._notify(this.onError, message, { source: 'recognition', code });
  }

  suspend() {
    if (this._disposed) return;
    this._enabled = false;
    this._suspending = true;
    try {
      this._cancelCurrent();
      this._setState('idle');
    } finally { this._suspending = false; }
  }

  dispose() {
    if (this._disposed) return;
    this.suspend();
    this._disposed = true;
    this._document?.removeEventListener?.('visibilitychange', this._onVisibility);
    this._voice.dispose();
  }
}
