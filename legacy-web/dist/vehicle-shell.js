const iconPaths = {
  battery: '<rect x="2.5" y="6.5" width="17" height="11" rx="2"/><path d="M22 10v4M6 9.5v5M9 9.5v5M12 9.5v5"/>',
  belt: '<circle cx="11.5" cy="4.5" r="2"/><path d="m8.5 8-2 6 2 7m6-13 2 6-2 7M9 9l6 8M7 16l10-6M6 14h11"/>',
  hatch: '<path d="m5 10 2-5h10l2 5v9H5v-9Z"/><path d="M8 10h8M4 4l4-2h8l4 2M8 19v2m8-2v2"/>',
  charge: '<path d="m13 2-8 12h6l-1 8 9-13h-6l1-7Z"/>',
  door: '<path d="M5 21V8l5-5h9v18H5Z"/><path d="M8 10V8l3-3h5v5H8Zm5 4h3"/>',
  doorRight: '<path d="M19 21V8l-5-5H5v18h14Z"/><path d="M16 10V8l-3-3H8v5h8Zm-5 4H8"/>',
  seat: '<path d="M7 3v8a3 3 0 0 0 3 3h8M7 8H4v8a3 3 0 0 0 3 3h12M10 14v5m8-5 3 5"/><path d="M6 3h3v3H6"/>',
  steering: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="2"/><path d="m4 8 6 3m10-3-6 3m-2 3v7"/>',
  lights: '<path d="M14 5a7 7 0 0 1 0 14V5ZM3 6h7M2 10h8M2 14h8M3 18h7"/>',
  settings: '<path d="M4 7h16M4 17h16"/><circle cx="9" cy="7" r="2.5" fill="currentColor" stroke="none"/><circle cx="15" cy="17" r="2.5" fill="currentColor" stroke="none"/>',
  lock: '<rect x="6" y="10" width="12" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 2v4"/>',
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
  bluetooth: '<path d="m8 6 10 12-6 4V2l6 4L8 18"/>',
  signal: '<path d="M4 20v-3m5 3v-7m5 7V9m5 11V4" stroke-width="2.7"/>',
  wifi: '<path d="M3 8a14 14 0 0 1 18 0M6 12a9 9 0 0 1 12 0m-9 4a4 4 0 0 1 6 0"/><circle cx="12" cy="20" r="1" fill="currentColor" stroke="none"/>',
  car: '<path d="m4 10 2-5h12l2 5v8H4v-8Z"/><path d="M4 11h16M6 18v3m12-3v3M7 14h2m6 0h2"/>',
  nav: '<path d="m12 2 8 19-8-5-8 5 8-19Z"/>',
  phone: '<path d="m7 3 3 4-2 3a14 14 0 0 0 6 6l3-2 4 3-2 4C10 22 2 14 3 5l4-2Z"/>',
  grid: '<rect x="3" y="3" width="6" height="6" rx="1.4"/><rect x="15" y="3" width="6" height="6" rx="1.4"/><rect x="3" y="15" width="6" height="6" rx="1.4"/><rect x="15" y="15" width="6" height="6" rx="1.4"/>',
  music: '<circle cx="12" cy="12" r="9"/><path d="m16 8-3 5-5 3 3-5 5-3Z"/>',
  defrost: '<path d="M3 17V7a23 23 0 0 1 18 0v10H3ZM7 14V9m5 5V8m5 6V9m-12 2 2-2 2 2m1-1 2-2 2 2m1 1 2-2 2 2"/>',
  heatSeat: '<path d="M5 4v8a4 4 0 0 0 4 4h11M4 12v5a3 3 0 0 0 3 3h13M12 3c-4 3 3 3 0 6m5-6c-4 3 3 3 0 6"/>',
  fan: '<circle cx="12" cy="12" r="2"/><path d="M11 10C5 6 9 1 13 3c3 2 0 5 0 7m1 1c5-4 9 1 6 4-3 2-5-1-6-1m-3 0c1 7-5 8-6 4-1-3 3-4 5-5"/>',
  chevron: '<path d="m8 10 4 4 4-4"/>',
};

const icon = (name, className = '') => `<svg class="${className}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.55" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${iconPaths[name] || ''}</svg>`;
const decorative = (name, className = '') => `<span class="${className}" aria-hidden="true">${icon(name)}</span>`;
let previousCleanup = null;

/** Mount an illustrative vehicle shell. The car controls and launchers do not operate a car. */
export function mountVehicleShell() {
  previousCleanup?.();
  const vehicle = document.getElementById('vehicle-panel');
  const system = document.getElementById('system-bar');
  const dock = document.getElementById('vehicle-dock');

  if (vehicle) {
    vehicle.innerHTML = `
      <div class="vehicle-shell">
        <div class="vehicle-statistics" aria-hidden="true">
          <div class="vehicle-range">${icon('battery')}<span>0<span class="vehicle-unit">km</span></span></div>
          <div class="vehicle-speed"><strong>0</strong><span>km/h</span></div>
          <div class="vehicle-gears"><span class="vehicle-gear-current">P</span><span>R</span><span>N</span><span>D</span></div>
        </div>
        <div class="vehicle-belt" aria-hidden="true">${icon('belt')}<span>안전벨트를 매주세요</span></div>
        <div class="vehicle-stage">
          <div class="vehicle-picture"><img src="./assets/vehicle.png" alt="차량 외관 예시" decoding="async" draggable="false"><span class="vehicle-image-fallback" hidden>차량 이미지 준비 중</span></div>
          <div class="vehicle-actions" aria-hidden="true">${['hatch', 'charge', 'door', 'doorRight'].map(name => decorative(name, 'vehicle-action')).join('')}</div>
        </div>
        <div class="vehicle-utility" aria-hidden="true">${['seat', 'steering', 'lights', 'settings'].map(name => decorative(name, 'vehicle-utility-icon')).join('')}</div>
        <p class="vehicle-example">차량 화면 예시</p>
      </div>`;
  }

  if (system) {
    system.innerHTML = `
      <div class="system-left" aria-hidden="true">${decorative('lock', 'system-icon')}${decorative('search', 'system-icon')}</div>
      <div class="system-right"><span class="system-profile" aria-hidden="true">연</span><div class="system-connections" aria-hidden="true">${icon('bluetooth')}${icon('signal')}${icon('wifi')}</div><time class="system-clock" aria-label="한국 시간"></time></div>`;
  }

  if (dock) {
    dock.innerHTML = `
      <div class="dock-shell" aria-label="작동하지 않는 차량 공조와 앱 아이콘 예시">
        <div class="dock-climate dock-climate-left" aria-hidden="true"><span class="dock-temperature">23.0<span>°</span></span><span class="dock-ac">A/C</span>${decorative('heatSeat', 'dock-climate-icon')}${decorative('defrost', 'dock-climate-icon')}</div>
        <div class="dock-launchers" aria-hidden="true">${decorative('car', 'dock-app')}${decorative('nav', 'dock-app')}${decorative('phone', 'dock-app')}${decorative('grid', 'dock-app')}<span class="dock-app dock-app-active">${icon('music')}</span></div>
        <div class="dock-climate dock-climate-right" aria-hidden="true">${decorative('fan', 'dock-climate-icon')}${decorative('heatSeat', 'dock-climate-icon')}<span class="dock-temperature">24.0<span>°</span></span>${decorative('chevron', 'dock-chevron')}</div>
      </div>`;
  }

  const carImage = vehicle?.querySelector('.vehicle-picture img');
  const imageFallback = vehicle?.querySelector('.vehicle-image-fallback');
  const imageFailed = () => {
    carImage.hidden = true;
    if (imageFallback) imageFallback.hidden = false;
  };
  carImage?.addEventListener('error', imageFailed, { once: true });
  if (carImage?.complete && !carImage.naturalWidth) imageFailed();

  const clock = system?.querySelector('.system-clock');
  const formatter = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
  const updateClock = () => {
    if (!clock) return;
    const now = new Date();
    clock.textContent = formatter.format(now);
    clock.dateTime = now.toISOString();
  };
  updateClock();
  const timer = clock ? setInterval(updateClock, 30000) : null;
  previousCleanup = () => {
    if (timer !== null) clearInterval(timer);
    carImage?.removeEventListener('error', imageFailed);
  };
  return previousCleanup;
}
