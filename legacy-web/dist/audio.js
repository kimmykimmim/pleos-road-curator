const NOTES = { c: 261.6256, d: 293.6648, e: 329.6276, g: 391.9954, a: 440 };
const KINDS = new Set(['dots', 'breeze', 'bounce']);
const LOOP_SECONDS = 6;

/** Small local-only audio engine. No microphone is opened until record() is called. */
export class OrchestraAudio {
  constructor() {
    this.context = null;
    this.master = null;
    this.compressor = null;
    this.volume = 0.65;
    this.current = null;
    this.previous = null;
    this.playing = false;
    this._voices = new Set();
    this._loopTimer = null;
    this._previewTimer = null;
    this._previewResolve = null;
    this._intent = 0;
    this._recordIntent = 0;
    this._recording = null;
  }

  async unlock() {
    if (!this.context) {
      const AudioContextClass = globalThis.AudioContext || globalThis.webkitAudioContext;
      if (!AudioContextClass) throw new Error('이 브라우저에서는 소리를 재생할 수 없어요. 다른 브라우저로 열어주세요.');
      this.context = new AudioContextClass();
      this.master = this.context.createGain();
      this.master.gain.value = this.volume;
      this.compressor = this.context.createDynamicsCompressor();
      this.compressor.threshold.value = -15;
      this.compressor.knee.value = 18;
      this.compressor.ratio.value = 4;
      this.compressor.attack.value = 0.01;
      this.compressor.release.value = 0.15;
      this.master.connect(this.compressor);
      this.compressor.connect(this.context.destination);
    }
    if (this.context.state === 'suspended') await this.context.resume();
    if (this.context.state !== 'running') throw new Error('소리를 켜려면 화면의 재생 버튼을 한 번 눌러주세요.');
    return this.context;
  }

  setVolume(value) {
    const next = Number(value);
    this.volume = Number.isFinite(next) ? Math.min(1, Math.max(0, next)) : this.volume;
    if (this.master && this.context) {
      this.master.gain.setTargetAtTime(this.volume, this.context.currentTime, 0.035);
    }
  }

  async preview(kind) {
    const selected = this._kind(kind);
    const intent = ++this._intent;
    this._silence();
    this.cancelRecording();
    await this.unlock();
    if (intent !== this._intent) return;
    this._motif(this.context, this.master, selected, null, this.context.currentTime + 0.03, 0.9, true);
    return new Promise((resolve) => {
      this._previewResolve = resolve;
      this._previewTimer = setTimeout(() => {
        this._previewTimer = null;
        this._previewResolve = null;
        resolve();
      }, 1750);
    });
  }

  async start(kind, buffer = null) {
    const selected = this._kind(kind);
    if (buffer && (!Number.isFinite(buffer.duration) || typeof buffer.getChannelData !== 'function')) {
      throw new Error('녹음한 소리를 읽을 수 없어요. 다시 녹음해 주세요.');
    }
    const intent = ++this._intent;
    this._silence();
    this.cancelRecording();
    await this.unlock();
    if (intent !== this._intent) return;
    const old = this.current;
    this.current = { kind: selected, buffer };
    this.previous = old || null;
    this.playing = true;
    this._beginLoop();
  }

  /** Edit the current scene without shifting the previous scene into history. */
  async replace(kind, buffer = null) {
    const selected = this._kind(kind);
    if (buffer && (!Number.isFinite(buffer.duration) || typeof buffer.getChannelData !== 'function')) {
      throw new Error('녹음한 소리를 읽을 수 없어요. 다시 녹음해 주세요.');
    }
    const intent = ++this._intent;
    this._silence();
    this.cancelRecording();
    await this.unlock();
    if (intent !== this._intent) return;
    this.current = { kind: selected, buffer };
    this.playing = true;
    this._beginLoop();
  }

  pause() {
    ++this._intent;
    this._silence();
    this.cancelRecording();
  }

  async resume() {
    if (!this.current) return;
    const intent = ++this._intent;
    this._silence();
    this.cancelRecording();
    await this.unlock();
    if (intent !== this._intent) return;
    this.playing = true;
    this._beginLoop();
  }

  stop() {
    this.pause();
    this.current = null;
    this.previous = null;
  }

  _kind(kind) {
    if (!KINDS.has(kind)) throw new Error('소리 예시를 먼저 골라주세요.');
    return kind;
  }

  _silence() {
    this.playing = false;
    if (this._loopTimer !== null) clearInterval(this._loopTimer);
    this._loopTimer = null;
    if (this._previewTimer !== null) clearTimeout(this._previewTimer);
    this._previewTimer = null;
    if (this._previewResolve) this._previewResolve();
    this._previewResolve = null;
    const now = this.context?.currentTime || 0;
    for (const voice of [...this._voices]) {
      try {
        voice.envelope.gain.cancelScheduledValues(now);
        voice.envelope.gain.setTargetAtTime(0, now, 0.008);
      } catch {}
      for (const source of voice.sources) {
        try { source.stop(now + 0.035); } catch {}
      }
    }
  }

  _beginLoop() {
    let next = this.context.currentTime + 0.04;
    const schedule = () => {
      if (!this.playing || !this.current) return;
      // Avoid a burst of old notes after a suspended/background tab resumes.
      if (next < this.context.currentTime - 0.1) next = this.context.currentTime + 0.04;
      if (next < this.context.currentTime + 0.2) {
        this._pad(this.context, this.master, next, 1);
        if (this.previous) this._motif(this.context, this.master, this.previous.kind, this.previous.buffer, next + 0.12, 0.24);
        this._motif(this.context, this.master, this.current.kind, this.current.buffer, next, 0.85);
        next += LOOP_SECONDS;
      }
    };
    schedule();
    this._loopTimer = setInterval(schedule, 100);
  }

  _registerVoice(ctx, sources, nodes, envelope) {
    const voice = { sources, nodes, envelope };
    if (ctx === this.context) this._voices.add(voice);
    let ended = 0;
    for (const source of sources) {
      source.onended = () => {
        ended += 1;
        if (ended !== sources.length) return;
        for (const node of nodes) { try { node.disconnect(); } catch {} }
        this._voices.delete(voice);
      };
    }
  }

  _tone(ctx, output, time, frequency, duration, amplitude, type = 'bell', targetFrequency = null) {
    const envelope = ctx.createGain();
    const base = ctx.createOscillator();
    const overtone = ctx.createOscillator();
    const overtoneGain = ctx.createGain();
    base.type = 'sine';
    overtone.type = 'sine';
    base.frequency.setValueAtTime(frequency, time);
    overtone.frequency.setValueAtTime(frequency * 2, time);
    if (targetFrequency) {
      base.frequency.exponentialRampToValueAtTime(targetFrequency, time + duration * 0.72);
      overtone.frequency.exponentialRampToValueAtTime(targetFrequency * 2, time + duration * 0.72);
    }
    overtoneGain.gain.value = type === 'wood' ? 0.19 : type === 'air' ? 0.06 : 0.1;
    const attack = type === 'air' ? Math.min(0.35, duration * 0.25) : 0.012;
    envelope.gain.setValueAtTime(0.0001, time);
    envelope.gain.exponentialRampToValueAtTime(Math.max(0.0002, amplitude), time + attack);
    if (type === 'air') {
      envelope.gain.setValueAtTime(amplitude, time + duration * 0.65);
    }
    envelope.gain.exponentialRampToValueAtTime(0.0001, time + duration);
    base.connect(envelope);
    overtone.connect(overtoneGain);
    overtoneGain.connect(envelope);
    envelope.connect(output);
    this._registerVoice(ctx, [base, overtone], [base, overtone, overtoneGain, envelope], envelope);
    base.start(time);
    overtone.start(time);
    base.stop(time + duration + 0.02);
    overtone.stop(time + duration + 0.02);
  }

  _pad(ctx, output, time, scale) {
    this._tone(ctx, output, time, NOTES.c / 2, LOOP_SECONDS - 0.15, 0.035 * scale, 'air');
    this._tone(ctx, output, time + 0.04, NOTES.g / 2, LOOP_SECONDS - 0.3, 0.024 * scale, 'air');
  }

  _motif(ctx, output, kind, buffer, time, scale = 1, preview = false) {
    if (buffer) {
      this._sample(ctx, output, buffer, time, scale);
      return;
    }
    if (kind === 'dots') {
      const events = preview ? [0, 0.2, 0.4, 0.8, 1, 1.2] : [0, 0.25, 0.5, 1.25, 1.5, 2.5, 2.75, 3];
      const pitches = [NOTES.c, NOTES.g, NOTES.e, NOTES.c, NOTES.a, NOTES.g, NOTES.e, NOTES.g];
      events.forEach((offset, i) => this._tone(ctx, output, time + offset, pitches[i], 0.2, 0.17 * scale, 'wood'));
    } else if (kind === 'breeze') {
      const length = preview ? 1.45 : 3.25;
      this._tone(ctx, output, time, NOTES.e, length, 0.085 * scale, 'air', NOTES.g);
      this._tone(ctx, output, time + 0.12, NOTES.c, length - 0.15, 0.04 * scale, 'air');
    } else {
      const events = preview ? [0, 0.45, 0.95] : [0, 0.5, 1.25, 2.25, 2.75];
      const pitches = [NOTES.c, NOTES.e, NOTES.g, NOTES.d, NOTES.a];
      events.forEach((offset, i) => this._tone(ctx, output, time + offset, pitches[i] * 0.78, 0.45, 0.11 * scale, 'bell', pitches[i]));
    }
  }

  _sample(ctx, output, buffer, time, scale) {
    const source = ctx.createBufferSource();
    const envelope = ctx.createGain();
    source.buffer = buffer;
    let peak = 0;
    let squared = 0;
    const samples = buffer.getChannelData(0);
    for (let i = 0; i < samples.length; i += 4) {
      peak = Math.max(peak, Math.abs(samples[i]));
      squared += samples[i] * samples[i];
    }
    const rms = Math.sqrt(squared / Math.max(1, Math.ceil(samples.length / 4)));
    const level = Math.min(2.5, 0.12 / Math.max(0.01, rms), 0.55 / Math.max(0.01, peak)) * scale;
    const duration = Math.max(0, Math.min(buffer.duration, 5.5));
    const attack = Math.min(0.015, duration / 4);
    const release = Math.min(0.04, duration / 4);
    envelope.gain.setValueAtTime(0, time);
    envelope.gain.linearRampToValueAtTime(level, time + attack);
    envelope.gain.setValueAtTime(level, time + Math.max(attack, duration - release));
    envelope.gain.linearRampToValueAtTime(0, time + duration);
    source.connect(envelope);
    envelope.connect(output);
    this._registerVoice(ctx, [source], [source, envelope], envelope);
    source.start(time, 0, duration);
  }

  cancelRecording() {
    ++this._recordIntent;
    const recording = this._recording;
    this._recording = null;
    if (recording) recording.cancel();
  }

  /** Finish a running take normally, keeping its recorded audio. */
  finishRecording() {
    return this._recording?.finish?.() || false;
  }

  async record(seconds = 5, onStart = () => {}) {
    this.pause();
    const intent = ++this._recordIntent;
    let stream = null;
    let recorder = null;
    let timer = null;
    const duration = Math.min(5, Math.max(1, Number(seconds) || 5));
    try {
      await this.unlock();
      if (intent !== this._recordIntent) throw new Error('녹음을 취소했어요.');
      if (!globalThis.isSecureContext || !navigator.mediaDevices?.getUserMedia || !globalThis.MediaRecorder) {
        throw new Error('이 환경에서는 녹음을 사용할 수 없어요. 소리 예시를 골라서 계속할 수 있어요.');
      }
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
        video: false,
      });
      if (intent !== this._recordIntent) throw new Error('녹음을 취소했어요.');
      // Permission may take a long time. Cue and countdown belong after it succeeds.
      this._recording = { cancel: () => { for (const track of stream.getTracks()) track.stop(); } };
      this._tone(this.context, this.master, this.context.currentTime + 0.02, NOTES.a * 2, 0.09, 0.055, 'bell');
      await new Promise(resolve => setTimeout(resolve, 200));
      if (intent !== this._recordIntent) throw new Error('녹음을 취소했어요.');
      const mimeType = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/ogg;codecs=opus']
        .find(type => MediaRecorder.isTypeSupported(type));
      recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      const chunks = [];
      const blob = await new Promise((resolve, reject) => {
        let settled = false;
        const fail = (error) => { if (!settled) { settled = true; reject(error); } };
        this._recording = {
          finish: () => {
            if (recorder.state === 'inactive') return false;
            recorder.stop();
            return true;
          },
          cancel: () => {
            fail(new Error('녹음을 취소했어요.'));
            try { if (recorder.state !== 'inactive') recorder.stop(); } catch {}
            for (const track of stream.getTracks()) track.stop();
          },
        };
        recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data); };
        recorder.onerror = () => fail(new Error('녹음 중 문제가 생겼어요. 소리 예시로 계속해 주세요.'));
        recorder.onstop = () => {
          if (settled) return;
          settled = true;
          resolve(new Blob(chunks, { type: recorder.mimeType || 'audio/webm' }));
        };
        recorder.start();
        if (typeof onStart === 'function') onStart();
        timer = setTimeout(() => { if (recorder.state !== 'inactive') recorder.stop(); }, duration * 1000);
      });
      if (intent !== this._recordIntent) throw new Error('녹음을 취소했어요.');
      if (!blob.size) throw new Error('목소리가 녹음되지 않았어요. 다시 시도해 주세요.');
      const decoded = await this.context.decodeAudioData(await blob.arrayBuffer());
      if (intent !== this._recordIntent) throw new Error('녹음을 취소했어요.');
      return this._trimRecording(decoded);
    } catch (error) {
      if (error?.name === 'NotAllowedError' || error?.name === 'SecurityError') {
        throw new Error('마이크 사용을 허용하지 않았어요. 소리 예시를 골라서 계속할 수 있어요.');
      }
      if (error?.name === 'NotFoundError' || error?.name === 'NotReadableError') {
        throw new Error('사용할 수 있는 마이크를 찾지 못했어요. 소리 예시로 계속해 주세요.');
      }
      if (error?.name === 'EncodingError' || error?.name === 'NotSupportedError') {
        throw new Error('녹음한 소리를 읽을 수 없어요. 소리 예시로 계속해 주세요.');
      }
      throw error;
    } finally {
      if (timer !== null) clearTimeout(timer);
      try { if (recorder && recorder.state !== 'inactive') recorder.stop(); } catch {}
      if (stream) for (const track of stream.getTracks()) track.stop();
      // A canceled old recording must not clear a newer recording's cancellation hook.
      if (intent === this._recordIntent) this._recording = null;
    }
  }

  _trimRecording(buffer) {
    const channel = buffer.getChannelData(0);
    let first = 0;
    let last = channel.length - 1;
    while (first < channel.length && Math.abs(channel[first]) < 0.008) first++;
    while (last > first && Math.abs(channel[last]) < 0.008) last--;
    if (first === channel.length) throw new Error('목소리가 잘 들리지 않았어요. 조금 가까이에서 다시 해볼까요?');
    first = Math.max(0, first - Math.floor(buffer.sampleRate * 0.04));
    last = Math.min(channel.length - 1, last + Math.floor(buffer.sampleRate * 0.07));
    const length = Math.min(last - first + 1, Math.floor(buffer.sampleRate * 5));
    const trimmed = this.context.createBuffer(buffer.numberOfChannels, Math.max(1, length), buffer.sampleRate);
    for (let ch = 0; ch < buffer.numberOfChannels; ch++) trimmed.copyToChannel(buffer.getChannelData(ch).subarray(first, first + length), ch);
    return trimmed;
  }

  /** Render the journey locally, one twelve-second chapter per recorded scene. */
  async exportJourney(history) {
    const OfflineContext = globalThis.OfflineAudioContext || globalThis.webkitOfflineAudioContext;
    if (!OfflineContext) throw new Error('이 브라우저에서는 음악 파일을 만들 수 없어요.');
    if (!Array.isArray(history) || !history.length) throw new Error('먼저 풍경 소리를 하나 만들어 주세요.');
    // Bound memory and rendering time for a prototype even after a very long session.
    const scenes = history.slice(-20).map(item => ({ kind: this._kind(item.kind), buffer: item.buffer || null }));
    const rate = 44100;
    const chapter = 2 * LOOP_SECONDS;
    const duration = scenes.length * chapter + 0.5;
    const offline = new OfflineContext(2, Math.ceil(duration * rate), rate);
    const master = offline.createGain();
    master.gain.value = this.volume;
    const compressor = offline.createDynamicsCompressor();
    compressor.threshold.value = -15;
    compressor.knee.value = 18;
    compressor.ratio.value = 4;
    master.connect(compressor);
    compressor.connect(offline.destination);
    scenes.forEach((scene, index) => {
      const start = index * chapter + 0.05;
      const section = offline.createGain();
      section.connect(master);
      section.gain.setValueAtTime(0, start);
      section.gain.linearRampToValueAtTime(1, start + 0.12);
      section.gain.setValueAtTime(1, start + chapter - 0.3);
      section.gain.linearRampToValueAtTime(0, start + chapter);
      for (let loop = 0; loop < 2; loop++) {
        const time = start + loop * LOOP_SECONDS;
        this._pad(offline, section, time, 1);
        if (index) {
          const before = scenes[index - 1];
          this._motif(offline, section, before.kind, before.buffer, time + 0.12, 0.24);
        }
        this._motif(offline, section, scene.kind, scene.buffer, time, 0.85);
      }
    });
    const rendered = await offline.startRendering();
    return this._wav(rendered);
  }

  _wav(buffer) {
    const channels = buffer.numberOfChannels;
    const dataBytes = buffer.length * channels * 2;
    const array = new ArrayBuffer(44 + dataBytes);
    const view = new DataView(array);
    const text = (offset, value) => { for (let i = 0; i < value.length; i++) view.setUint8(offset + i, value.charCodeAt(i)); };
    text(0, 'RIFF'); view.setUint32(4, 36 + dataBytes, true); text(8, 'WAVE');
    text(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true);
    view.setUint16(22, channels, true); view.setUint32(24, buffer.sampleRate, true);
    view.setUint32(28, buffer.sampleRate * channels * 2, true); view.setUint16(32, channels * 2, true);
    view.setUint16(34, 16, true); text(36, 'data'); view.setUint32(40, dataBytes, true);
    const samples = Array.from({ length: channels }, (_, ch) => buffer.getChannelData(ch));
    let offset = 44;
    for (let frame = 0; frame < buffer.length; frame++) {
      for (let ch = 0; ch < channels; ch++) {
        const value = Math.max(-1, Math.min(1, samples[ch][frame]));
        view.setInt16(offset, value < 0 ? value * 32768 : value * 32767, true);
        offset += 2;
      }
    }
    return new Blob([array], { type: 'audio/wav' });
  }
}
