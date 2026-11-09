const NO_SPEECH = '목소리가 잘 들리지 않았어요. 다시 말해볼까요?';
const UNFINISHED_SPEECH = '말을 끝까지 듣지 못했어요. 한 번 더 들려주세요.';
const SPEECH_FALLBACK = '음성 안내가 나오지 않아요. 화면의 이야기를 읽으며 계속할 수 있어요.';

const cleanText = value => typeof value === 'string' ? value.replace(/\s+/g, ' ').trim() : '';
const recognitionClass = () => globalThis.SpeechRecognition || globalThis.webkitSpeechRecognition;

function recognitionError(code) {
  switch (code) {
    case 'not-allowed':
    case 'service-not-allowed':
    case 'NotAllowedError':
    case 'SecurityError':
      return '마이크를 사용할 수 없어요. 보호자와 마이크 허용을 확인해 주세요.';
    case 'audio-capture':
    case 'NotFoundError':
    case 'NotReadableError':
      return '마이크를 찾지 못했어요. 글로 알려줘도 괜찮아요.';
    case 'network':
      return '지금은 말을 알아듣기 어려워요. 인터넷 연결을 확인하거나 글로 알려주세요.';
    case 'language-not-supported':
    case 'bad-grammar':
      return '이 브라우저에서는 한국어 듣기를 사용할 수 없어요. 글로 알려주세요.';
    case 'no-speech':
      return NO_SPEECH;
    case 'aborted':
      return '듣기가 멈췄어요. 준비되면 다시 말해 주세요.';
    case 'InvalidStateError':
      return '잠시 후 다시 말하기를 눌러주세요.';
    default:
      return '말을 잘 듣지 못했어요. 다시 말하거나 글로 알려주세요.';
  }
}

/** Browser speech only. Construction never starts recognition or opens a microphone. */
export class StoryVoice {
  constructor({ onState, onTranscript, onError } = {}) {
    this.onState = typeof onState === 'function' ? onState : () => {};
    this.onTranscript = typeof onTranscript === 'function' ? onTranscript : () => {};
    this.onError = typeof onError === 'function' ? onError : () => {};
    this._state = 'idle';
    this._disposed = false;
    this._speech = null;
    this._recognition = null;
    this._speechGeneration = 0;
    this._recognitionGeneration = 0;
  }

  get supportedRecognition() {
    return !this._disposed && typeof recognitionClass() === 'function';
  }

  _notify(callback, ...args) {
    try { callback(...args); } catch { /* Host callbacks cannot leave the microphone active. */ }
  }

  _setState(state) {
    if (state === this._state) return;
    this._state = state;
    this._notify(this.onState, state);
  }

  _idleIfQuiet() {
    if (!this._speech && !this._recognition) this._setState('idle');
  }

  _isSpeech(run) {
    return !this._disposed && this._speech === run && run.generation === this._speechGeneration;
  }

  _isRecognition(run) {
    return !this._disposed && this._recognition === run && run.generation === this._recognitionGeneration;
  }

  /** Resolves true only when the whole spoken line completes, false on skip or cancel. */
  async speak(text, { enabled = true, volume = 0.6 } = {}) {
    this.cancelSpeech();
    const line = cleanText(text);
    if (this._disposed || !enabled || !line) return false;
    this.stopListening({ discard: true });
    const synthesis = globalThis.speechSynthesis;
    const Utterance = globalThis.SpeechSynthesisUtterance;
    if (!synthesis || typeof synthesis.speak !== 'function' || typeof Utterance !== 'function') {
      this._notify(this.onError, SPEECH_FALLBACK, { source: 'speech', code: 'unavailable' });
      return false;
    }

    return new Promise(resolve => {
      let utterance;
      try { utterance = new Utterance(line); }
      catch {
        this._notify(this.onError, SPEECH_FALLBACK, { source: 'speech', code: 'unavailable' });
        resolve(false);
        return;
      }
      const numericVolume = Number(volume);
      utterance.lang = 'ko-KR';
      utterance.rate = 0.92;
      utterance.pitch = 1;
      utterance.volume = Number.isFinite(numericVolume) ? Math.max(0, Math.min(1, numericVolume)) : 0.6;
      try {
        const koreanVoice = synthesis.getVoices?.().find(voice => /^ko(?:-|_)/i.test(voice.lang));
        if (koreanVoice) utterance.voice = koreanVoice;
      } catch { /* The browser can still use its language default. */ }

      const run = { generation: ++this._speechGeneration, utterance, synthesis, resolve, timer: null, settled: false };
      this._speech = run;
      utterance.onstart = () => {
        if (this._isSpeech(run)) this._setState('speaking');
      };
      utterance.onend = () => {
        if (this._isSpeech(run)) this._finishSpeech(run, true);
      };
      utterance.onerror = () => {
        if (this._isSpeech(run)) this._finishSpeech(run, false, SPEECH_FALLBACK, true);
      };
      // A blocked speech queue must not leave the app waiting forever.
      const timeout = Math.min(45000, Math.max(8000, 4000 + line.length * 180));
      run.timer = setTimeout(() => {
        if (this._isSpeech(run)) this._finishSpeech(run, false, SPEECH_FALLBACK, true);
      }, timeout);
      this._setState('speaking');
      if (!this._isSpeech(run)) return;
      try { synthesis.speak(utterance); }
      catch { this._finishSpeech(run, false, SPEECH_FALLBACK, true); }
    });
  }

  _finishSpeech(run, completed, message = '', cancelNative = false) {
    if (this._speech !== run || run.settled) return;
    run.settled = true;
    this._speech = null;
    if (run.timer !== null) clearTimeout(run.timer);
    // Invalidate first: some browsers emit error/end synchronously from cancel().
    ++this._speechGeneration;
    if (cancelNative) { try { run.synthesis.cancel(); } catch {} }
    this._idleIfQuiet();
    run.resolve(completed);
    if (message && !this._disposed) this._notify(this.onError, message, { source: 'speech', code: 'unavailable' });
  }

  cancelSpeech() {
    const run = this._speech;
    if (run) this._finishSpeech(run, false, '', true);
    else ++this._speechGeneration;
  }

  /** Call only after the user explicitly chooses to speak and consent is handled by the UI. */
  startListening() {
    if (this._disposed) return false;
    this.cancelSpeech();
    this.stopListening({ discard: true });
    const Recognition = recognitionClass();
    if (typeof Recognition !== 'function') {
      this._notify(this.onError, '이 브라우저에서는 말로 알려주기를 사용할 수 없어요. 글로 알려주세요.', { source: 'recognition', code: 'unsupported' });
      return false;
    }

    let recognition;
    try { recognition = new Recognition(); }
    catch {
      this._notify(this.onError, '말로 알려주기를 시작하지 못했어요. 글로 알려줘도 괜찮아요.', { source: 'recognition', code: 'start-failed' });
      return false;
    }
    recognition.lang = 'ko-KR';
    recognition.continuous = false;
    recognition.interimResults = true;
    recognition.maxAlternatives = 1;
    const run = {
      generation: ++this._recognitionGeneration,
      recognition,
      results: [],
      lastInterim: '',
      delivered: false,
      stopping: false,
      stopTimer: null,
    };
    this._recognition = run;

    recognition.onstart = () => {
      if (!this._isRecognition(run)) {
        // Permission may have completed after this turn was already canceled.
        try { recognition.abort(); } catch {}
        return;
      }
      this._setState('listening');
    };
    recognition.onresult = event => {
      if (!this._isRecognition(run) || run.delivered) return;
      const incoming = event.results;
      if (!incoming || !Number.isFinite(incoming.length)) return;
      run.results = Array.from({ length: incoming.length }, (_, index) => {
        const result = incoming[index];
        if (!result) return run.results[index] || { text: '', final: false };
        return { text: cleanText(result[0]?.transcript), final: Boolean(result.isFinal) };
      });
      const spoken = cleanText(run.results.map(result => result.text).join(' '));
      const finals = cleanText(run.results.filter(result => result.final).map(result => result.text).join(' '));
      const allFinal = run.results.length > 0 && run.results.every(result => result.final);
      if (allFinal && finals) {
        this._finishRecognition(run, { transcript: finals, abortNative: true });
      } else if (spoken && spoken !== run.lastInterim) {
        run.lastInterim = spoken;
        this._notify(this.onTranscript, spoken, { final: false });
      }
    };
    recognition.onerror = event => {
      if (!this._isRecognition(run)) return;
      this._finishRecognition(run, { message: recognitionError(event?.error), errorCode: event?.error || 'unknown', abortNative: true });
    };
    recognition.onend = () => {
      if (!this._isRecognition(run)) return;
      this._finishFromResults(run);
    };

    this._setState('starting');
    if (!this._isRecognition(run)) return false;
    try {
      recognition.start();
      return true;
    } catch (error) {
      if (this._isRecognition(run)) this._finishRecognition(run, { message: recognitionError(error?.name), errorCode: error?.name || 'start-failed', abortNative: true });
      return false;
    }
  }

  _finishFromResults(run, abortNative = false) {
    const transcript = cleanText(run.results.filter(result => result.final).map(result => result.text).join(' '));
    this._finishRecognition(run, {
      transcript,
      message: transcript ? '' : run.lastInterim ? UNFINISHED_SPEECH : NO_SPEECH,
      errorCode: run.lastInterim ? 'unfinished-speech' : 'no-speech',
      abortNative,
    });
  }

  _finishRecognition(run, { transcript = '', message = '', errorCode = 'unknown', abortNative = false } = {}) {
    if (!this._isRecognition(run)) return;
    this._recognition = null;
    ++this._recognitionGeneration;
    if (run.stopTimer !== null) clearTimeout(run.stopTimer);
    if (abortNative) { try { run.recognition.abort(); } catch {} }
    this._idleIfQuiet();
    if (transcript && !run.delivered) {
      run.delivered = true;
      this._notify(this.onTranscript, transcript, { final: true });
    } else if (message) {
      this._notify(this.onError, message, { source: 'recognition', code: errorCode });
    }
  }

  stopListening({ discard = false } = {}) {
    const run = this._recognition;
    if (!run) return;
    if (discard) {
      this._recognition = null;
      ++this._recognitionGeneration;
      if (run.stopTimer !== null) clearTimeout(run.stopTimer);
      try { run.recognition.abort(); } catch {}
      this._idleIfQuiet();
      return;
    }
    if (run.stopping) return;
    run.stopping = true;
    try { run.recognition.stop(); }
    catch {
      if (this._isRecognition(run)) this._finishFromResults(run, true);
      return;
    }
    if (this._isRecognition(run)) {
      // Normally stop() emits one final result and end. Keep a bounded fallback.
      run.stopTimer = setTimeout(() => {
        if (this._isRecognition(run)) this._finishFromResults(run, true);
      }, 5000);
    }
  }

  dispose() {
    if (this._disposed) return;
    // Prevent an idle callback from starting new work during teardown.
    this._disposed = true;
    this.cancelSpeech();
    this.stopListening({ discard: true });
    this._idleIfQuiet();
  }
}
