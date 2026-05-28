import "./styles.css";
import {
  getClasses,
  getState,
  newGame,
  sendTurn,
  equip,
  unequip,
  useItem,
  type ClassInfo,
  type PlayerView,
} from "./api.js";

const root = document.getElementById("app")!;

type LogEntry = { who: "player" | "dm"; text: string };
const log: LogEntry[] = [];
let player: PlayerView | null = null;
let contextUsage = 0; // токены контекста последнего хода
let refreshSheet: (() => void) | null = null; // перерисовка открытого инвентаря, если открыт
let invPage = 0; // активная страница пейджера инвентаря

// Режим сессии задаётся пунктом меню и меняет точку старта и поведение выхода.
type Mode = "story" | "combat" | "tavern" | "dungeon";
let mode: Mode = "story";
let pendingStart: string | undefined; // start_location для выбранного режима
const MODE_KEY = "mythrix_mode";

interface MenuEntry {
  mode: Mode;
  title: string;
  desc: string;
  cta: string;
  start?: string; // undefined = серверный дефолт (таверна)
  art: string; // css-класс плейсхолдера-картинки
}

const STORY: MenuEntry = {
  mode: "story",
  title: "Тень над Северным отрогом",
  desc: "Три недели назад дракон сжёг деревню. Уцелевшие не выходят после темноты. Дорога на север ведёт к логову — и почти никто не возвращается.",
  cta: "Войти в историю",
  start: "loc_village_northspur",
  art: "art--story",
};
const COMBAT: MenuEntry = {
  mode: "combat",
  title: "Бой",
  desc: "Проверка боевой системы: схватка с врагом в северном лесу. Бой закончится — вернёшься в меню.",
  cta: "В бой",
  start: "loc_north_forest",
  art: "art--combat",
};
const TAVERN: MenuEntry = {
  mode: "tavern",
  title: "Таверна",
  desc: "Магазин, дружелюбные и нейтральные NPC. Проверка диалогов и торговли.",
  cta: "Войти в таверну",
  start: undefined,
  art: "art--tavern",
};
const DUNGEON: MenuEntry = {
  mode: "dungeon",
  title: "Подземелье",
  desc: "Заваленные шахты. NPC нет, врагов нет. Проверка того, как DM подталкивает игрока, когда непонятно, что делать.",
  cta: "В подземелье",
  start: "loc_collapsed_mines",
  art: "art--dungeon",
};

function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Record<string, string> = {},
  ...children: (Node | string)[]
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === "class") node.className = v;
    else node.setAttribute(k, v);
  }
  for (const c of children) node.append(c);
  return node;
}

function clear(): void {
  root.replaceChildren();
}

function showError(msg: string): void {
  const toast = el("div", { class: "toast" }, msg);
  root.append(toast);
  setTimeout(() => toast.remove(), 4000);
}

// Нейтральное уведомление (не ошибка): например, «Бой окончен».
function notice(msg: string): void {
  const toast = el("div", { class: "toast toast--info" }, msg);
  root.append(toast);
  setTimeout(() => toast.remove(), 3000);
}

// Подтверждение действия: нативный диалог Telegram, иначе window.confirm.
function confirmAction(message: string, onYes: () => void): void {
  const tgConfirm = window.Telegram?.WebApp?.showConfirm;
  if (tgConfirm) {
    tgConfirm(message, (ok) => {
      if (ok) onYes();
    });
  } else if (window.confirm(message)) {
    onYes();
  }
}

// ---------- Главное меню ----------

interface ResumeInfo {
  player: PlayerView;
  lastNarrative: string | null;
  gameOver: boolean;
}

async function renderMenu(resume: ResumeInfo | null = null): Promise<void> {
  clear();
  log.length = 0;
  player = null;

  const wrap = el("main", { class: "screen screen--menu" });
  wrap.append(el("h1", { class: "title title--menu" }, "Mythrix"));

  if (resume && !resume.gameOver) {
    const cont = el(
      "button",
      { class: "btn btn--primary", type: "button" },
      `Продолжить — ${resume.player.name}`,
    );
    cont.addEventListener("click", () => void resumePlay(resume));
    wrap.append(cont);
  }

  wrap.append(el("h2", { class: "menu__heading" }, "Сюжеты"));
  wrap.append(storyCard(STORY));

  wrap.append(el("h2", { class: "menu__heading" }, "Тестовые сцены"));
  const tiles = el("div", { class: "menu__tiles" });
  tiles.append(modeTile(COMBAT), modeTile(TAVERN), modeTile(DUNGEON));
  wrap.append(tiles);

  root.append(wrap);
}

function storyCard(entry: MenuEntry): HTMLElement {
  const card = el(
    "button",
    { class: "story-card", type: "button" },
    el("div", { class: `story-card__art ${entry.art}` }),
    el(
      "div",
      { class: "story-card__body" },
      el("h3", { class: "story-card__title" }, entry.title),
      el("p", { class: "story-card__desc" }, entry.desc),
    ),
  );
  card.addEventListener("click", () => startMode(entry));
  return card;
}

function modeTile(entry: MenuEntry): HTMLElement {
  const tile = el(
    "button",
    { class: `menu-tile ${entry.art}`, type: "button" },
    el("span", { class: "menu-tile__title" }, entry.title),
    el("span", { class: "menu-tile__desc" }, entry.desc),
  );
  tile.addEventListener("click", () => startMode(entry));
  return tile;
}

function startMode(entry: MenuEntry): void {
  mode = entry.mode;
  pendingStart = entry.start;
  void renderClassSelect(entry.cta);
}

async function resumePlay(resume: ResumeInfo): Promise<void> {
  player = resume.player;
  await renderPlay();
  if (resume.lastNarrative) {
    log.push({ who: "dm", text: resume.lastNarrative });
    appendLog({ who: "dm", text: resume.lastNarrative });
  }
  if (resume.gameOver) renderDefeat();
}

// ---------- Экран выбора класса ----------

async function renderClassSelect(cta = "Войти в таверну"): Promise<void> {
  clear();
  log.length = 0;
  player = null;

  const wrap = el("main", { class: "screen screen--start" });
  const back = el("button", { class: "btn btn--ghost screen__back", type: "button" }, "← Меню");
  back.addEventListener("click", () => void renderMenu());
  wrap.append(back);
  wrap.append(el("h1", { class: "title" }, "Mythrix"));
  wrap.append(
    el("p", { class: "subtitle" }, "Последний привал. Дорога на север. Кто ты, путник?"),
  );

  const nameInput = el("input", {
    class: "field",
    type: "text",
    placeholder: "Имя героя",
    maxlength: "40",
  });
  wrap.append(nameInput);

  let classes: ClassInfo[] = [];
  try {
    classes = await getClasses();
  } catch (e) {
    showError((e as Error).message);
  }

  let selected = classes[0]?.id ?? "wanderer";
  const list = el("div", { class: "classes" });
  for (const c of classes) {
    const card = el(
      "button",
      { class: "class-card", "data-id": c.id, type: "button" },
      el("span", { class: "class-name" }, c.name),
      el("span", { class: "class-desc" }, c.description),
    );
    if (c.id === selected) card.classList.add("is-selected");
    card.addEventListener("click", () => {
      selected = c.id;
      list.querySelectorAll(".class-card").forEach((n) => n.classList.remove("is-selected"));
      card.classList.add("is-selected");
    });
    list.append(card);
  }
  wrap.append(list);

  const start = el("button", { class: "btn btn--primary", type: "button" }, cta);
  start.addEventListener("click", async () => {
    const name = nameInput.value.trim() || "Путник";
    start.setAttribute("disabled", "true");
    start.textContent = "…";
    try {
      const res = await newGame(selected, name, pendingStart);
      localStorage.setItem(MODE_KEY, mode);
      player = res.player;
      await renderPlay();
      await firstTurn();
    } catch (e) {
      showError((e as Error).message);
      start.removeAttribute("disabled");
      start.textContent = cta;
    }
  });
  wrap.append(start);

  root.append(wrap);
}

// ---------- Игровой экран ----------

let logEl: HTMLElement;
let barEl: HTMLElement;
let input: HTMLTextAreaElement;
let sendBtn: HTMLButtonElement;

async function renderPlay(): Promise<void> {
  clear();
  const screen = el("main", { class: "screen screen--play" });

  barEl = el("header", { class: "charbar" });
  screen.append(barEl);

  logEl = el("section", { class: "log", "aria-label": "Повествование" });
  screen.append(logEl);

  const form = el("form", { class: "composer" });
  input = el("textarea", {
    class: "field composer__input",
    placeholder: "Что ты делаешь?",
    rows: "1",
  }) as HTMLTextAreaElement;
  sendBtn = el("button", { class: "btn btn--send", type: "submit" }, "→") as HTMLButtonElement;
  form.append(input, sendBtn);
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    void submitAction();
  });
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void submitAction();
    }
  });
  screen.append(form);

  root.append(screen);
  renderBar();
  for (const entry of log) appendLog(entry);
}

function renderBar(): void {
  if (!player) return;
  const pct = Math.max(0, Math.round((player.hp / player.max_hp) * 100));
  const hp = el("span", { class: "hpbar", title: `HP ${player.hp}/${player.max_hp}` });
  hp.append(el("span", { class: "hpbar__fill", style: `width:${pct}%` }));

  const parts: Node[] = [
    el("span", { class: "charbar__name" }, `${player.name} · ${player.class_id}`),
    hp,
    el(
      "span",
      { class: "charbar__ctx", title: "Токенов контекста на последнем ходу" },
      contextUsage ? `${contextUsage} ток.` : "",
    ),
    iconBtn(
      svgIcon("M9 9V6.5A3 3 0 0 1 15 6.5V9", "M5 9H19L20 17A3 3 0 0 1 17 20H7A3 3 0 0 1 4 17Z"),
      "Снаряжение",
      openInventory,
      player.inventory_items.length,
    ),
    iconBtn(
      svgIcon("M21 4H14", "M10 4H3", "M21 12H12", "M8 12H3", "M21 20H16", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4"),
      "Настройки",
      openSettings,
    ),
  ];

  // В таверне доступен выход за порог — он завершает историю.
  if (mode === "tavern") {
    parts.push(
      iconBtn(
        svgIcon("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "M16 17l5-5-5-5", "M21 12H9"),
        "Выйти за порог",
        exitTavern,
      ),
    );
  }

  barEl.replaceChildren(...parts);
}

// Иконка-SVG со штриховым контуром (currentColor) — в тон тёмному оформлению.
function svgIcon(...paths: string[]): SVGElement {
  const ns = "http://www.w3.org/2000/svg";
  const s = document.createElementNS(ns, "svg");
  s.setAttribute("viewBox", "0 0 24 24");
  s.setAttribute("width", "18");
  s.setAttribute("height", "18");
  s.setAttribute("fill", "none");
  s.setAttribute("stroke", "currentColor");
  s.setAttribute("stroke-width", "1.6");
  s.setAttribute("stroke-linecap", "round");
  s.setAttribute("stroke-linejoin", "round");
  for (const d of paths) {
    const p = document.createElementNS(ns, "path");
    p.setAttribute("d", d);
    s.appendChild(p);
  }
  return s;
}

// Иконочная кнопка в charbar; badge — необязательный счётчик (например, число предметов).
function iconBtn(
  icon: Node,
  title: string,
  onClick: () => void,
  badge?: number,
): HTMLButtonElement {
  const btn = el(
    "button",
    { class: "charbar__icon", type: "button", title, "aria-label": title },
    icon,
  ) as HTMLButtonElement;
  if (badge && badge > 0) btn.append(el("span", { class: "charbar__badge" }, String(badge)));
  btn.addEventListener("click", onClick);
  return btn;
}

// Затемнённый оверлей с панелью (bottom sheet / диалог). Возвращает функцию закрытия.
function openOverlay(panel: HTMLElement, onClose?: () => void): () => void {
  const overlay = el("div", { class: "overlay" }, panel);
  const close = (): void => {
    overlay.classList.remove("is-open");
    onClose?.();
    setTimeout(() => overlay.remove(), 280);
  };
  overlay.addEventListener("click", (e) => {
    if (e.target === overlay) close();
  });
  root.append(overlay);
  requestAnimationFrame(() => overlay.classList.add("is-open"));
  return close;
}

// Инвентарь — bottom sheet с горизонтальным пейджером по разделам (свайп/табы).
function openInventory(): void {
  invPage = 0;
  const tabsEl = el("div", { class: "sheet__tabs" });
  const pager = el("div", { class: "sheet__pager" });

  const setActive = (idx: number): void => {
    invPage = idx;
    Array.from(tabsEl.children).forEach((t, i) =>
      (t as HTMLElement).classList.toggle("is-active", i === idx),
    );
  };

  const fill = (): void => {
    tabsEl.replaceChildren();
    pager.replaceChildren();
    CATEGORIES.forEach((cat, idx) => {
      const tab = el("button", { class: "sheet__tab", type: "button" }, cat.title);
      tab.addEventListener("click", () => {
        pager.scrollTo({ left: pager.clientWidth * idx, behavior: "smooth" });
        setActive(idx);
      });
      tabsEl.append(tab);

      const page = el("div", { class: "sheet__page" });
      const items = player ? player.inventory_items.filter((it) => it.type === cat.type) : [];
      if (items.length === 0) page.append(el("p", { class: "sheet__empty" }, "Пусто."));
      else for (const it of items) page.append(renderInvRow(it));
      pager.append(page);
    });
    setActive(Math.min(invPage, CATEGORIES.length - 1));
    requestAnimationFrame(() => {
      pager.scrollLeft = pager.clientWidth * invPage;
    });
  };

  pager.addEventListener("scroll", () => {
    const idx = Math.round(pager.scrollLeft / Math.max(1, pager.clientWidth));
    if (idx !== invPage) setActive(idx);
  });

  fill();
  refreshSheet = fill;

  const panel = el(
    "div",
    { class: "sheet", role: "dialog", "aria-label": "Снаряжение" },
    el("div", { class: "sheet__grip" }),
    el(
      "div",
      { class: "sheet__head" },
      el("h2", { class: "sheet__title" }, "Снаряжение"),
      el("span", { class: "sheet__gold" }, player ? `${player.gold} зол.` : ""),
    ),
    tabsEl,
    pager,
  );
  openOverlay(panel, () => {
    refreshSheet = null;
  });
}

// Настройки — диалог по кнопке-шестерёнке (пока: новая игра).
function openSettings(): void {
  const menuBtn = el("button", { class: "btn btn--ghost", type: "button" }, "В меню");
  const newBtn = el("button", { class: "btn btn--primary", type: "button" }, "Новая игра");
  const closeBtn = el("button", { class: "btn btn--ghost", type: "button" }, "Закрыть");
  const panel = el(
    "div",
    { class: "dialog", role: "dialog", "aria-label": "Настройки" },
    el("h2", { class: "dialog__title" }, "Настройки"),
    el("p", { class: "dialog__hint" }, "«В меню» — выйти к выбору сцены. «Новая игра» — сбросить текущий прогресс."),
    el("div", { class: "dialog__actions" }, menuBtn, newBtn, closeBtn),
  );
  const close = openOverlay(panel);
  closeBtn.addEventListener("click", close);
  menuBtn.addEventListener("click", () => {
    close();
    void renderMenu();
  });
  newBtn.addEventListener("click", () => {
    close();
    confirmAction("Начать заново? Текущий прогресс будет потерян.", () => {
      void renderMenu();
    });
  });
}

// Действие над предметом: вызывает API, обновляет игрока и перерисовывает инвентарь.
async function invAction(
  btn: HTMLButtonElement,
  run: () => Promise<{ player: PlayerView }>,
): Promise<void> {
  btn.setAttribute("disabled", "true");
  try {
    const res = await run();
    player = res.player;
    renderBar();
    refreshSheet?.();
  } catch (e) {
    showError((e as Error).message);
    btn.removeAttribute("disabled");
  }
}

// Разделы снаряжения в порядке показа.
const CATEGORIES: { type: string; title: string }[] = [
  { type: "weapon", title: "Оружие" },
  { type: "armor", title: "Броня" },
  { type: "ring", title: "Кольца" },
  { type: "consumable", title: "Расходники" },
];

// Строка снаряжения = кликабельная: снаряжение снять/надеть по клику, расходник — использовать.
function renderInvRow(it: PlayerView["inventory_items"][number]): HTMLElement {
  if (!player) throw new Error("no player");
  const slot =
    it.type === "weapon" ? "weapon" : it.type === "armor" ? "armor" : null;
  const isEquipped =
    (slot === "weapon" && player.equipped.weapon === it.id) ||
    (slot === "armor" && player.equipped.armor === it.id);
  const isConsumable = it.type === "consumable";

  let chip: string;
  if (isConsumable) chip = it.charges !== undefined ? `заряды: ${it.charges}` : "расходник";
  else if (slot) chip = isEquipped ? "надето" : "снято";
  else chip = "—";

  const row = el(
    "button",
    {
      class:
        "inv-row" +
        (isEquipped ? " is-equipped" : "") +
        (slot || isConsumable ? "" : " is-static"),
      type: "button",
      title: it.description ?? it.name,
    },
    el("span", { class: "inv-name" }, it.name),
    el("span", { class: "inv-chip" + (isEquipped ? " is-on" : "") }, chip),
  ) as HTMLButtonElement;

  if (slot) {
    row.addEventListener("click", () =>
      invAction(row, () => (isEquipped ? unequip(slot) : equip(it.id))),
    );
  } else if (isConsumable) {
    row.addEventListener("click", () => invAction(row, () => useItem(it.id)));
  }
  return row;
}

function appendLog(entry: LogEntry): void {
  const node = el(
    "div",
    { class: `bubble bubble--${entry.who}` },
    ...entry.text.split("\n").map((line) => el("p", {}, line)),
  );
  logEl.append(node);
  logEl.scrollTop = logEl.scrollHeight;
}

function setBusy(busy: boolean): void {
  input.disabled = busy;
  sendBtn.disabled = busy;
  sendBtn.textContent = busy ? "…" : "→";
}

async function firstTurn(): Promise<void> {
  setBusy(true);
  appendLog({ who: "dm", text: "…" });
  try {
    const res = await sendTurn("Я оглядываюсь по сторонам.");
    logEl.lastElementChild?.remove();
    player = res.player;
    contextUsage = res.context_usage;
    pushDm(res.narrative, res.game_over, res.combat_over);
  } catch (e) {
    logEl.lastElementChild?.remove();
    showError((e as Error).message);
  } finally {
    setBusy(false);
  }
}

async function submitAction(): Promise<void> {
  const action = input.value.trim();
  if (!action || input.disabled) return;
  input.value = "";
  log.push({ who: "player", text: action });
  appendLog({ who: "player", text: action });

  setBusy(true);
  appendLog({ who: "dm", text: "…" });
  try {
    const res = await sendTurn(action);
    logEl.lastElementChild?.remove();
    player = res.player;
    contextUsage = res.context_usage;
    pushDm(res.narrative, res.game_over, res.combat_over);
  } catch (e) {
    logEl.lastElementChild?.remove();
    showError((e as Error).message);
  } finally {
    setBusy(false);
  }
}

function pushDm(narrative: string, gameOver: boolean, combatOver = false): void {
  log.push({ who: "dm", text: narrative });
  appendLog({ who: "dm", text: narrative });
  renderBar();
  refreshSheet?.();
  if (gameOver) {
    renderDefeat();
    return;
  }
  // Режим «Бой»: схватка завершена (враг повержен) — возврат в меню.
  if (combatOver && mode === "combat") {
    setBusy(true);
    notice("Бой окончен — возврат в меню.");
    setTimeout(() => void renderMenu(), 1400);
  }
}

// Выход за порог таверны завершает историю.
function exitTavern(): void {
  confirmAction("Выйти за порог? История на этом закончится.", () => {
    renderEnd("Ты уходишь в ночь. Здесь история заканчивается.");
  });
}

function renderEnd(text: string): void {
  const overlay = el(
    "div",
    { class: "defeat" },
    el("p", { class: "defeat__text defeat__text--calm" }, text),
  );
  const back = el("button", { class: "btn btn--primary", type: "button" }, "В меню");
  back.addEventListener("click", () => void renderMenu());
  overlay.append(back);
  root.append(overlay);
}

function renderDefeat(): void {
  const overlay = el(
    "div",
    { class: "defeat" },
    el("p", { class: "defeat__text" }, "Здесь твоя дорога обрывается."),
  );
  const again = el("button", { class: "btn btn--primary", type: "button" }, "В меню");
  again.addEventListener("click", () => void renderMenu());
  overlay.append(again);
  root.append(overlay);
}

// ---------- Старт ----------

async function bootstrap(): Promise<void> {
  const tg = window.Telegram?.WebApp;
  tg?.ready();
  tg?.expand();

  try {
    const st = await getState();
    if (st.player) {
      const saved = localStorage.getItem(MODE_KEY) as Mode | null;
      if (saved === "story" || saved === "combat" || saved === "tavern" || saved === "dungeon") mode = saved;
      await renderMenu({
        player: st.player,
        lastNarrative: st.last_narrative ?? null,
        gameOver: !!st.player.game_over,
      });
      return;
    }
  } catch (e) {
    showError((e as Error).message);
  }
  await renderMenu();
}

void bootstrap();
