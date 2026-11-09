/** Local observation prompts. Only the child's report records a discovery. */
export const CONTEXTS = Object.freeze([
  Object.freeze({ id: 'any', label: '두루 살피기' }),
  Object.freeze({ id: 'city', label: '도시' }),
  Object.freeze({ id: 'nature', label: '자연' }),
  Object.freeze({ id: 'tunnel', label: '터널' }),
  Object.freeze({ id: 'night', label: '밤' }),
]);

const THEMES = new Set(['color', 'shape', 'nature', 'sky', 'light', 'change']);
const RESULTS = new Set(['start', 'next', 'found', 'skip', 'changed', 'same']);
const ALL_CONTEXTS = ['city', 'nature', 'tunnel', 'night'];
const OPTIONAL_HINT = '보이지 않아도 괜찮아. 다른 걸 살펴봐도 돼.';
const DETAIL_HINT = '아직 편하게 보일 때만 살펴봐. 지나갔다면 다음 미션으로 가도 돼.';

function define(id, label, prompt, theme, contexts, detail, { easy = false, kind = 'find', hint = OPTIONAL_HINT } = {}) {
  return { id, label, prompt, hint, kind, theme, parentId: null, contexts, detail, easy };
}

// Broad prompts remain eligible in every setting, including an unfamiliar view.
// Five easy choices ensure that two skips can lower effort without repeating the last four roots.
const MISSIONS = [
  define('notice-one', '눈에 들어온 하나', '지금 창밖에서 눈에 들어오는 것 하나를 골라볼까?', 'shape', ALL_CONTEXTS,
    '아직 보이면, 가장 눈에 띄는 부분 하나를 살펴볼까?', { easy: true }),
  define('notice-color', '눈에 띄는 색', '지금 보이는 색 하나를 골라볼까? 어떤 색이어도 괜찮아.', 'color', ALL_CONTEXTS,
    '아직 보이면, 그 색 옆에는 어떤 색이 있는지 살펴볼까?', { easy: true }),
  define('notice-light', '밝음과 어두움', '지금 창밖이 밝게 보이는지 어둡게 보이는지 살펴볼까? 어느 쪽이어도 괜찮아.', 'light', ALL_CONTEXTS,
    '지금 보이는 곳에 조금 더 밝거나 어두운 부분도 있을까?', { easy: true }),
  define('notice-outline', '내가 고른 모양', '지금 보이는 것 하나는 어떤 모양일까? 네가 느낀 대로 골라도 돼.', 'shape', ALL_CONTEXTS,
    '아직 보이면, 그 모양의 끝부분도 살펴볼까?', { easy: true }),
  define('notice-change', '비슷할까 달라질까', '지금 창밖 모습이 비슷하게 이어지는지 달라지는지 살펴볼까? 그대로여도 괜찮아.', 'change', ALL_CONTEXTS,
    '지금 보이는 모습에서 눈에 들어오는 부분 하나를 골라볼까?', { easy: true, kind: 'change' }),
  define('repeated-shapes', '이어지는 같은 모양', '비슷한 모양이 나란히 이어지는 곳이 있는지 찾아볼까?', 'shape', ALL_CONTEXTS,
    '아직 보이면, 모양 사이에 빈틈이 있는지도 살펴볼까?'),
  define('round-shape', '둥근 모양', '동그랗거나 둥글게 휜 모양이 있는지 찾아볼까?', 'shape', ALL_CONTEXTS,
    '아직 보이면, 둥근 모양 옆에는 어떤 모양이 있는지 살펴볼까?'),
  define('long-line', '길게 이어진 선', '길게 이어진 선이나 모양이 있는지 찾아볼까?', 'shape', ALL_CONTEXTS,
    '아직 보이면, 곧게 이어지는지 구불구불한지 살펴볼까?'),
  define('neighbor-colors', '나란히 있는 색', '서로 다른 색이 나란히 보이는 곳이 있을까?', 'color', ALL_CONTEXTS,
    '아직 보이면, 두 색이 만나는 부분도 살펴볼까?'),
  define('tree', '나무 한 그루', '창밖에 나무가 보이면 한 그루를 골라볼까?', 'nature', ['city', 'nature'],
    '아직 보이면, 가지 사이로 하늘이 보이는지 살펴볼까?'),
  define('plants-together', '모여 있는 초록', '풀이나 나무가 모여 있는 곳이 보이는지 찾아볼까?', 'nature', ['city', 'nature'],
    '아직 보이면, 초록빛이 모두 비슷한지 살펴볼까?'),
  define('cloud', '구름 모양', '하늘에 구름이 있는지 살펴볼까?', 'sky', ['city', 'nature'],
    '아직 보이면, 구름 가장자리가 어떤 모양인지 살펴볼까?'),
  define('sky-gap', '틈 사이의 하늘', '건물이나 나무 사이로 하늘이 보이는 곳이 있을까?', 'sky', ['city', 'nature'],
    '아직 보이면, 하늘이 보이는 틈은 어떤 모양인지 살펴볼까?'),
  define('hill', '높낮이가 있는 땅', '높아지거나 낮아지는 땅의 모양이 있는지 찾아볼까?', 'nature', ['nature'],
    '아직 보이면, 땅과 하늘이 만나는 선도 살펴볼까?'),
  define('water', '물이 보이는 곳', '강이나 호수처럼 물이 보이는 곳이 있는지 찾아볼까?', 'nature', ['nature'],
    '아직 보이면, 물 옆에는 무엇이 있는지 살펴볼까?'),
  define('building', '건물의 모양', '건물이 보이면 눈에 들어오는 하나를 골라볼까?', 'shape', ['city', 'night'],
    '아직 보이면, 건물 위쪽은 어떤 모양인지 살펴볼까?'),
  define('windows', '나란히 있는 창문', '창문이 나란히 있는 건물이 보이는지 찾아볼까?', 'shape', ['city', 'night'],
    '아직 보이면, 창문 사이의 모양도 살펴볼까?'),
  define('lit-window', '불이 켜진 창문', '불이 켜진 창문이 보이는지 찾아볼까?', 'light', ['city', 'night'],
    '아직 보이면, 밝은 창문 옆에 어두운 창문도 있을까?'),
  define('lights-in-row', '이어지는 불빛', '불빛이 나란히 이어지는 곳이 있는지 찾아볼까?', 'light', ['city', 'tunnel', 'night'],
    '아직 보이면, 불빛 사이에 어두운 틈도 있는지 살펴볼까?'),
  define('wall-pattern', '벽에 있는 모양', '벽에 비슷한 모양이 이어지는 곳이 있는지 찾아볼까?', 'shape', ['tunnel'],
    '아직 보이면, 모양 사이에 틈이 있는지도 살펴볼까?'),
  define('light-change', '밝기가 바뀌는 풍경', '지금 보이는 풍경의 밝기가 달라지는지 살펴볼까? 비슷해도 괜찮아.', 'change', ['city', 'tunnel', 'night'],
    '지금 보이는 곳에서 밝은 부분이나 어두운 부분 하나를 골라볼까?', { kind: 'change' }),
  define('color-patch', '넓게 보이는 색', '한 가지 색이 넓게 보이는 곳이 있는지 찾아볼까?', 'color', ['city', 'nature'],
    '아직 보이면, 그 색이 있는 곳의 가장자리도 살펴볼까?'),
];

const BY_ID = new Map(MISSIONS.map(mission => [mission.id, mission]));
const contextId = value => CONTEXTS.some(context => context.id === value) ? value : 'any';
const shortText = value => typeof value === 'string' ? Array.from(value).slice(0, 80).join('') : '';
const validRound = value => Number.isSafeInteger(value) && value >= 0 ? Math.min(value, Number.MAX_SAFE_INTEGER - 1) : 0;

function publicMission(mission) {
  const { id, label, prompt, hint, kind, theme, parentId } = mission;
  return { id, label, prompt, hint, kind, theme, parentId };
}

function detailMission(root) {
  return {
    id: `${root.id}-detail`, label: '조금 더 살펴보기', prompt: root.detail,
    hint: DETAIL_HINT, kind: 'detail', theme: root.theme, parentId: root.id,
  };
}

function knownMission(value) {
  if (!value || typeof value !== 'object') return null;
  const root = BY_ID.get(value.id);
  if (root) return publicMission(root);
  const parent = BY_ID.get(value.parentId);
  return parent && value.id === `${parent.id}-detail` ? detailMission(parent) : null;
}

export function createMissionState(context = 'any') {
  return { context: contextId(context), current: null, history: [], discoveries: [], recent: [], round: 0 };
}

function snapshot(previous) {
  if (!previous || typeof previous !== 'object') return createMissionState();
  const state = createMissionState(previous.context);
  state.current = knownMission(previous.current);
  state.round = validRound(previous.round);
  state.history = (Array.isArray(previous.history) ? previous.history : []).slice(-40).flatMap(entry => {
    const mission = knownMission(entry);
    if (!mission || !RESULTS.has(entry.result)) return [];
    return [{ id: mission.id, label: mission.label, kind: mission.kind, theme: mission.theme, parentId: mission.parentId, result: entry.result, round: validRound(entry.round) }];
  });
  state.discoveries = (Array.isArray(previous.discoveries) ? previous.discoveries : []).slice(-30)
    .filter(entry => entry && shortText(entry.label) && THEMES.has(entry.theme))
    .map(entry => ({ label: shortText(entry.label), theme: entry.theme }));
  state.recent = (Array.isArray(previous.recent) ? previous.recent : []).filter(id => BY_ID.has(id)).slice(-4);
  return state;
}

function trailingSkips(history) {
  let count = 0;
  for (let index = history.length - 1; index >= 0 && history[index].result === 'skip'; index -= 1) count += 1;
  return count;
}

function chooseRoot(state, result) {
  let candidates = MISSIONS.filter(mission => state.context === 'any' || mission.contexts.includes(state.context));
  const currentRoot = state.current?.parentId || state.current?.id;
  const fresh = candidates.filter(mission => mission.id !== currentRoot && !state.recent.includes(mission.id));
  if (fresh.length) candidates = fresh;
  if (trailingSkips(state.history) >= 2) {
    const easy = candidates.filter(mission => mission.easy);
    if (easy.length) candidates = easy;
  }
  const anotherTheme = candidates.filter(mission => mission.theme !== state.current?.theme);
  if (anotherTheme.length) candidates = anotherTheme;
  // Stable selection depends on this session's choices, not a prescribed route.
  const contextIndex = CONTEXTS.findIndex(context => context.id === state.context);
  const resultIndex = [...RESULTS].indexOf(result);
  const seed = state.round % 100003 * 7 + state.history.length * 3 + state.discoveries.length * 11 + contextIndex * 5 + resultIndex * 13;
  return candidates[seed % candidates.length];
}

/** found/same are self-reports; skip/changed/next never add a discovery. */
export function advanceMission(previous, result = 'start') {
  const state = snapshot(previous);
  const action = RESULTS.has(result) ? result : 'next';
  const current = state.current;
  // "No change" completes a comparison, but is not a finding for other tasks.
  if (action === 'same' && current?.kind !== 'change') return state;
  if (current) {
    state.history.push({ id: current.id, label: current.label, kind: current.kind, theme: current.theme, parentId: current.parentId, result: action, round: state.round });
    state.history = state.history.slice(-40);
  }
  state.round += 1;
  if ((action === 'found' || action === 'same') && current && current.kind !== 'detail') {
    state.discoveries.push({ label: current.label, theme: current.theme });
    state.discoveries = state.discoveries.slice(-30);
    state.current = detailMission(BY_ID.get(current.id));
    return state;
  }
  const root = chooseRoot(state, action);
  state.current = publicMission(root);
  state.recent = [...state.recent, root.id].slice(-4);
  return state;
}

/** Replacing the view is never treated as finding the current target. */
export function setMissionContext(previous, context) {
  const state = snapshot(previous);
  state.context = contextId(context);
  return advanceMission(state, 'changed');
}
