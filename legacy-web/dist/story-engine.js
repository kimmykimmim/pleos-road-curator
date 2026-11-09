/**
 * Local, deterministic story demo. This is not a language model or a scene sensor.
 * Only supplied observations and the child's own statements enter the snapshot.
 * All returned strings are plain text; the view must escape them before using HTML.
 */
const INPUT_LIMIT = 600;
const WORLD_LIMIT = 8;
const MAX_TURN = 1000000;
const MOODS = new Set(['curious', 'excited', 'calm', 'brave']);
const DEFAULT_SCENE = { key: 'road', label: '아직 정하지 않은 길' };
const SCENES = [
  ['tunnel', '터널', /터널/g],
  ['forest', '나무와 숲', /나무|숲/g],
  ['sky', '하늘', /구름|(?<!밤)하늘/g],
  ['city', '도시', /건물|도시|빌딩/g],
  ['water', '물가', /바다|호수|물가|강물/g],
  ['mountain', '산', /산봉우리|산속|산길|산(?=에|으로|이|은|을|\s|$)/g],
  ['night', '밤하늘', /밤하늘|밤(?=이|에|은|을|\s|$)/g],
  ['space', '우주', /우주(?!선|인)|행성/g],
  ['road', '길', /도로|(?<!산)길(?=에|이|은|을|\s|$)/g],
];
const SCENE_KEYS = new Set([...SCENES.map(([key]) => key), 'custom']);
const ROLE_WORDS = ['악당', '친구', '요정', '고래', '공주', '왕자', '로봇', '우주선', '괴물', '용'];
const plain = (value, limit = INPUT_LIMIT) => typeof value === 'string' ? Array.from(value.trim()).slice(0, limit).join('') : '';
const compact = value => plain(value).replace(/\s+/g, ' ');
const quoted = value => `“${plain(value, 76).replace(/[?？]/g, '')}${value.length > 76 ? '…' : ''}”`;

function particle(word, consonant, vowel) {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  return code >= 0 && code <= 11171 && code % 28 ? consonant : vowel;
}
const topic = word => word + particle(word, '은', '는');
const subject = word => word === '너' ? '네가' : word === '나' ? '내가' : word + particle(word, '이', '가');

export function createStoryState() {
  return {
    title: '우리의 창밖 이야기',
    reply: '눈에 들어오는 걸 네 이야기로 바꿔볼까?',
    scene: { ...DEFAULT_SCENE },
    world: [],
    focus: '',
    mood: 'curious',
    event: '',
    suggestions: ['구름은 고래야', '내가 먼저 이야기할래'],
    turn: 0,
  };
}

function snapshot(previous) {
  const base = createStoryState();
  if (!previous || typeof previous !== 'object') return base;
  const world = Array.isArray(previous.world) ? previous.world : [];
  return {
    ...base,
    title: plain(previous.title, 40) || base.title,
    reply: plain(previous.reply, 180) || base.reply,
    scene: SCENE_KEYS.has(previous.scene?.key)
      ? { key: previous.scene.key, label: plain(previous.scene.label, 40) || base.scene.label }
      : { ...base.scene },
    world: world.slice(-WORLD_LIMIT).filter(item => item && typeof item === 'object')
      .map(item => ({ subject: plain(item.subject, 40), meaning: plain(item.meaning, 100) }))
      .filter(item => item.subject && item.meaning),
    focus: plain(previous.focus, 80),
    mood: MOODS.has(previous.mood) ? previous.mood : ['happy', 'wonder'].includes(previous.mood) ? 'excited' : base.mood,
    event: plain(previous.event, 140),
    suggestions: Array.isArray(previous.suggestions)
      ? previous.suggestions.filter(item => typeof item === 'string').slice(0, 3).map(item => plain(item, 60)).filter(Boolean)
      : [...base.suggestions],
    turn: Number.isSafeInteger(previous.turn) && previous.turn >= 0 ? Math.min(previous.turn, MAX_TURN) : 0,
  };
}

function normalizeEntity(value) {
  let result = compact(value).replace(/^[“”"'‘’]+|[“”"'‘’]+$/g, '');
  result = result.replace(/^(?:아까|방금|지금|사실)\s*/, '');
  if (/^(?:뒤에\s*(?:있는|오는)\s*차|뒤쪽\s*차|뒷차)$/.test(result)) return '뒤차';
  result = result.replace(/^(?:저기|저쪽|저|그|이)\s*(?=건물|빌딩|나무|구름|자동차|차|친구|요정|악당|우주선|고래)/, '');
  return plain(result, 40);
}

function resolveEntity(state, value) {
  const name = normalizeEntity(value);
  const exact = state.world.find(item => item.subject === name);
  if (exact) return exact.subject;
  const aliases = state.world.filter(item => item.meaning === name || ROLE_WORDS.includes(name) && item.meaning.includes(name));
  if (aliases.length === 1) return aliases[0].subject;
  if (aliases.some(item => item.subject === state.focus)) return state.focus;
  const focused = state.world.find(item => item.subject === state.focus);
  if (/^(?:그\s*친구|그\s*애|걔|그것|아까\s*그\s*친구)$/.test(compact(value))
    && focused && /친구|요정|악당|고래|공주|왕자|로봇|괴물|용/.test(focused.subject + focused.meaning)) return state.focus;
  return name;
}

function remember(state, name, meaning) {
  const key = plain(resolveEntity(state, name), 40);
  const value = plain(meaning, 90);
  if (!key || !value) return;
  const existing = state.world.find(item => item.subject === key);
  if (existing) existing.meaning = value;
  else {
    state.world.push({ subject: key, meaning: value });
    if (state.world.length > WORLD_LIMIT) state.world.shift();
  }
  state.focus = key;
}

function negatedAt(text, index, word) {
  const after = text.slice(index + word.length, index + word.length + 28);
  return /^(?:은|는|이|가|도|에|으로|로)?\s*(?:아니|아닌|없|말고|안\s+(?:들어|가|왔|보|지나))/.test(after);
}

function detectScene(text) {
  const matches = [];
  const denied = new Set();
  for (const [key, label, pattern] of SCENES) {
    for (const match of text.matchAll(new RegExp(pattern.source, 'g'))) {
      if (negatedAt(text, match.index, match[0])) denied.add(key);
      else matches.push({ key, label, index: match.index });
    }
  }
  // A concrete object takes precedence over a fictional character's name.
  matches.sort((a, b) => a.index - b.index);
  const selected = matches.at(-1);
  return { selected: selected ? { key: selected.key, label: selected.label } : null, denied };
}

function assignments(text) {
  const results = [];
  for (const clause of text.split(/[.!?\n,，]+|\s+그리고\s+/)) {
    const clean = clause.trim().replace(/뒤에\s*(?:있는|오는)\s*차/g, '뒤차');
    if (!clean || /(?:아니|아닌|않|하지\s*말|말고)/.test(clean)) continue;
    const match = clean.match(/^(.{1,55}?)(?:은|는|이|가)\s*(?:사실\s*)?(.{1,90}?)(?:이었어|였어|이야|야|라고\s*하자|라고\s*할래)$/)
      || clean.match(/^(.{1,55}?)(?:을|를)\s*(.{1,90}?)(?:으로|로)\s*(?:하자|할래|바꿀래|바꾸자)$/);
    if (!match) continue;
    const meaning = compact(match[2]);
    // Avoid reading Korean descriptive verb endings as a new identity.
    if (/^(?:아니|뭐|어디|누구)|(?:했|졌|갔|왔|있|없|싶|할\s*거)$/.test(meaning)) continue;
    results.push({ subject: compact(match[1]), meaning });
  }
  return results;
}

function focusFromText(state, text) {
  const explicit = state.world.filter(item => text.includes(item.subject));
  if (explicit.length) return explicit.at(-1).subject;
  for (const word of ROLE_WORDS) {
    if (text.includes(word)) {
      const resolved = resolveEntity(state, word);
      if (state.world.some(item => item.subject === resolved)) return resolved;
    }
  }
  if (/(?:그\s*친구|그\s*애|걔|그것|요정)/.test(text)) return state.focus;
  return state.focus;
}

function contextualSuggestions(state) {
  const current = state.world.find(item => item.subject === state.focus);
  if (current?.meaning.includes('친구') || current?.meaning.includes('요정')) {
    return ['그 친구에게 이름을 지어줄래', '그 친구와 함께 떠났어'];
  }
  if (current?.meaning.includes('악당') && !current.meaning.includes('물러난')) {
    return ['사실 악당은 친구였어', '우리가 물리쳤어'];
  }
  if (current?.meaning.includes('고래')) return ['고래가 하늘을 헤엄쳤어', '고래에게 이름을 지어줄래'];
  if (current?.meaning.includes('성')) return ['성 안에서 친구를 만났어', '성에는 내가 만든 비밀이 있어'];
  if (state.scene.key === 'tunnel') return ['여기는 비밀 동굴이야', '우리가 불빛을 켰어'];
  if (state.focus) return [`${topic(state.focus)} 다른 모습으로 변했어`, '내가 다음 이야기를 할래'];
  return ['눈에 보이는 걸 이야기로 바꿀래', '내가 먼저 이야기할래'];
}

function storyName(entry) {
  if (!entry) return '';
  if (entry.subject === '나') return '너';
  if (entry.subject === '우리') return '너희';
  const meaning = entry.meaning;
  if (Array.from(meaning).length <= 24 && !/(?:했|었|갔|왔|있|없|내렸|물러난)|(?:어|해|다)$/.test(meaning)) return meaning;
  return entry.subject;
}

function isPlace(name) {
  return /(?:성|빵집|정거장|왕국|동굴|집|가게|학교|도서관)$/.test(name);
}

function questionFor(entry) {
  const name = storyName(entry);
  return isPlace(name) ? `${name}에는 무엇이 있을까?` : `${topic(name)} 무엇을 하고 있을까?`;
}

function helpReply(state) {
  const current = state.world.find(item => item.subject === state.focus);
  if (!current) return '눈에 보이는 것 하나가 아주 작아진다고 상상해볼까?';
  const name = storyName(current);
  if (isPlace(name)) return `${name}에 작은 비밀 문이 있다고 상상해볼까?`;
  if (/악당/.test(current.meaning) && !/물러난/.test(current.meaning)) return '이야기 속 악당이 무언가를 잃어버렸다면, 무엇일까?';
  if (/고래/.test(current.meaning)) return '고래가 숨을 내쉴 때 무언가 나온다면, 무엇일까?';
  if (/친구|요정/.test(current.subject + current.meaning)) return `이야기 속 ${subject(name)} 작은 선물을 건넨다고 상상해볼까?`;
  return `${topic(name)} 아주 작은 소리를 듣는다고 상상해볼까?`;
}

function reflect(state, text, withQuestion = true) {
  const current = state.world.find(item => item.subject === state.focus);
  const first = `${quoted(text)}라고 이야기했구나.`;
  if (!withQuestion) return first;
  if (current) return `${first} ${topic(storyName(current))} 이제 어떻게 될까?`;
  return `${first} 그다음에는 어떤 일이 생길까?`;
}

/**
 * Make a fresh, bounded snapshot. `scene` input is an optional supplied observation;
 * it never invents a sensor reading or overrides a child's established setting.
 */
export function advanceStory(previous, text, kind = 'child') {
  const state = snapshot(previous);
  const input = compact(text);
  if (!input || state.turn >= MAX_TURN) return state;
  const hadStory = state.turn > 0;
  const previousEvent = state.event;
  state.turn += 1;
  if (kind === 'help') {
    state.reply = helpReply(state);
    state.suggestions = contextualSuggestions(state);
    return snapshot(state);
  }
  state.event = plain(input, 140);

  if (kind === 'scene') {
    const { selected } = detectScene(input);
    if (!hadStory && selected) state.scene = selected;
    const current = state.world.find(item => item.subject === state.focus);
    const name = storyName(current);
    const fading = current && input.includes(current.subject) && /멀어|사라|안\s*보/.test(input);
    const prompt = fading ? `이야기 속 ${topic(name)} 어디로 가는 걸까?`
      : current && !isPlace(name) ? `이야기 속 ${topic(name)} 어떻게 느낄까?` : '우리 이야기에서는 어떤 장면일까?';
    state.reply = `${quoted(input)}라고 알려줬구나. ${prompt}`;
    state.suggestions = [selected ? `우리 이야기에도 ${subject(selected.label)} 나타났어` : '이 변화는 이야기에도 생겼어', '지금 이야기를 계속할래'];
    return snapshot(state);
  }

  const detected = detectScene(input);
  const acceptsObservation = /^(?:이|그)\s*변화(?:는|가)\s*(?:우리\s*)?이야기에도\s*(?:생겼어|나타났어|넣을래)[.!]?$/.test(input);
  const selected = detected.selected || (acceptsObservation ? detectScene(previousEvent).selected : null);
  const denied = detected.denied;
  if (selected) state.scene = selected;
  else if (denied.has(state.scene.key)) state.scene = { key: 'road', label: '다시 정하는 길' };

  // Reject an asserted identity without replacing it with another invented one.
  const rejection = input.match(/^(.{1,55}?)(?:은|는)\s*(.{1,70}?)(?:이|가)\s*아니(?:야|었어|라고)$/);
  if (rejection) {
    const target = resolveEntity(state, rejection[1]);
    state.world = state.world.filter(item => !(item.subject === target && item.meaning.includes(rejection[2])));
    if (state.focus === target && !state.world.some(item => item.subject === target)) state.focus = '';
    state.reply = `알겠어, ${topic(target)} ${rejection[2]}라는 설정을 빼둘게.`;
    state.suggestions = [`${topic(target)} 내가 새로 정할래`, '다른 이야기를 계속할래'];
    return snapshot(state);
  }

  const assigned = assignments(input);
  const correctionPrefix = /^(?:잘못\s*(?:들었어|알았어|봤어)[,，.!]?\s*|아니(?:야)?[,，.!]?\s*|사실\s*)/;
  const correctionBody = input.replace(correctionPrefix, '').replace(/^사실\s*/, '');
  const shortCorrection = correctionPrefix.test(input) && !/아니|아닌|않|없|[?？]/.test(correctionBody)
    ? correctionBody.match(/^(.{1,70}?)(?:이었어|였어|이야|야)[.!]?$/)
    : input.match(/^(친구|요정|악당|고래|로봇|공주|왕자|성|빵집|정거장)(?:이었어|였어|이야|야)[.!]?$/);
  if (!assigned.length && shortCorrection && state.world.some(item => item.subject === state.focus)) {
    assigned.push({ subject: state.focus, meaning: shortCorrection[1] });
  }
  if (assigned.length) {
    const corrected = /아까|사실|바꾸|바꿀/.test(input)
      || assigned.some(item => state.world.some(old => old.subject === resolveEntity(state, item.subject)));
    for (const item of assigned) remember(state, item.subject, item.meaning);
    const last = state.world.find(item => item.subject === state.focus);
    if (state.title === '우리의 창밖 이야기' || state.title.endsWith(`된 ${last.subject}`)) {
      state.title = plain(`${subject(last.meaning)} 된 ${last.subject}`, 40);
    }
    state.mood = 'excited';
    const addressed = last.subject === '나' ? '너' : last.subject === '우리' ? '너희' : last.subject;
    const prefix = /차/.test(last.subject) ? '이야기 속 ' : '';
    const confirmation = corrected
      ? `알겠어! 이제 ${prefix}${topic(addressed)} ${last.meaning}${particle(last.meaning, '이야', '야')}.`
      : `${prefix}${topic(addressed)} ${last.meaning}${particle(last.meaning, '이구나', '구나')}!`;
    const question = !corrected && state.turn % 2 ? ` ${questionFor(last)}` : '';
    state.reply = Array.from(confirmation + question).length <= 180 ? confirmation + question : confirmation;
    state.suggestions = contextualSuggestions(state);
    return snapshot(state);
  }

  if (/(?:물리쳤|이겼|쫓아냈|해치웠)/.test(input) && !/(?:못|안\s|않)/.test(input)) {
    const antagonist = state.world.find(item => /악당|괴물/.test(item.meaning) && !item.meaning.includes('물러난'));
    if (antagonist) {
      antagonist.meaning = `물러난 ${antagonist.meaning}`;
      state.focus = antagonist.subject;
      state.mood = 'brave';
      state.reply = '너희가 이야기 속 악당을 물리쳤구나! 다음 장면도 네가 정해줘.';
      state.suggestions = ['이제 친구들이 쉬었어', '새로운 곳으로 떠났어'];
      return snapshot(state);
    }
  }

  if (denied.size && !selected) {
    state.reply = '알겠어, 네가 알려준 대로 고칠게. 어떤 모습인지 다시 들려줄래?';
    state.suggestions = ['내가 본 건 다른 모습이야', '지금 이야기를 계속할래'];
    return snapshot(state);
  }

  // Narrow, explicit introduction patterns. Their actions do not replace an existing role.
  const presence = input.match(/^(.{1,35}?)(?:에는|에)\s*(.{1,35}?)(?:이|가)\s*있(?:었)?어[.!]?$/);
  if (presence) {
    const name = resolveEntity(state, presence[2]);
    if (!state.world.some(item => item.subject === name)) remember(state, name, `${presence[1]}에 있어`);
    state.focus = name;
    const person = storyName(state.world.find(item => item.subject === name));
    state.reply = `${presence[1]}에 ${subject(person)} 있구나! ${topic(person)} 무엇을 하고 있을까?`;
    state.suggestions = contextualSuggestions(state);
    return snapshot(state);
  }
  const action = input.match(/^(.{1,35}?)(?:이|가|은|는)\s*(.{0,55}?)(내렸어|탔어|나왔어|날아갔어|웃었어|달려갔어|도와줬어)[.!]?$/);
  if (action) {
    const name = resolveEntity(state, action[1]);
    if (!state.world.some(item => item.subject === name) && !['나', '우리'].includes(name)) remember(state, name, `${action[2]}${action[3]}`);
    state.focus = name;
    const person = storyName(state.world.find(item => item.subject === name)) || (name === '나' ? '너' : name === '우리' ? '너희' : name);
    state.reply = `${subject(person)} ${action[2]}${action[3].replace(/어$/, '구나')}! 다음 행동도 네가 정해줘.`;
    state.suggestions = contextualSuggestions(state);
    return snapshot(state);
  }

  // Remember an explicitly stated arrival without assigning an invented role.
  const arrival = input.match(/^(.{1,35}?)(?:이|가|은|는)\s*(.{1,35}?)(?:에|으로|로)\s*(도착했어|들어갔어|갔어|왔어)$/);
  if (arrival) {
    const actor = resolveEntity(state, arrival[1]);
    const destination = compact(arrival[2]);
    if (!state.world.some(item => item.subject === actor)) remember(state, actor, `${destination}에 ${arrival[3]}`);
    state.focus = actor;
    if (!selected) state.scene = { key: 'custom', label: plain(destination, 40) };
    state.reply = `${subject(actor)} ${destination}에 ${arrival[3].replace(/어$/, '구나')}! 거기에는 무엇이 있을까?`;
  } else {
    state.focus = focusFromText(state, input);
    state.reply = reflect(state, input, state.turn % 2 === 1);
  }
  state.suggestions = contextualSuggestions(state);
  return snapshot(state);
}
