import "./styles.css";
import {
  getClasses,
  getState,
  newGame,
  sendTurn,
  equip,
  unequip,
  useItem,
  dropItem,
  type ClassInfo,
  type PlayerView,
} from "./api.js";

const root = document.getElementById("app")!;

type LogEntry = { who: "player" | "dm"; text: string };
const log: LogEntry[] = [];
let player: PlayerView | null = null;
let contextUsage = 0; // токены контекста последнего хода

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

// ---------- Экран выбора класса ----------

async function renderClassSelect(): Promise<void> {
  clear();
  log.length = 0;
  player = null;

  const wrap = el("main", { class: "screen screen--start" });
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

  const start = el("button", { class: "btn btn--primary", type: "button" }, "Войти в таверну");
  start.addEventListener("click", async () => {
    const name = nameInput.value.trim() || "Путник";
    start.setAttribute("disabled", "true");
    start.textContent = "…";
    try {
      const res = await newGame(selected, name);
      player = res.player;
      await renderPlay();
      await firstTurn();
    } catch (e) {
      showError((e as Error).message);
      start.removeAttribute("disabled");
      start.textContent = "Войти в таверну";
    }
  });
  wrap.append(start);

  root.append(wrap);
}

// ---------- Игровой экран ----------

let logEl: HTMLElement;
let barEl: HTMLElement;
let invEl: HTMLElement;
let input: HTMLTextAreaElement;
let sendBtn: HTMLButtonElement;

async function renderPlay(): Promise<void> {
  clear();
  const screen = el("main", { class: "screen screen--play" });

  barEl = el("header", { class: "charbar" });
  screen.append(barEl);

  logEl = el("section", { class: "log", "aria-label": "Повествование" });
  screen.append(logEl);

  invEl = el("section", { class: "inventory" });
  screen.append(invEl);

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
  renderInventory();
  for (const entry of log) appendLog(entry);
}

function renderBar(): void {
  if (!player) return;
  const pct = Math.max(0, Math.round((player.hp / player.max_hp) * 100));
  barEl.replaceChildren(
    el("span", { class: "charbar__name" }, `${player.name} · ${player.class_id}`),
    (() => {
      const hp = el("span", { class: "hpbar", title: `HP ${player.hp}/${player.max_hp}` });
      hp.append(el("span", { class: "hpbar__fill", style: `width:${pct}%` }));
      return hp;
    })(),
    el("span", { class: "charbar__gold" }, `${player.gold} зол.`),
    el(
      "span",
      { class: "charbar__ctx", title: "Токенов контекста на последнем ходу" },
      contextUsage ? `${contextUsage} ток.` : "",
    ),
    (() => {
      const btn = el(
        "button",
        { class: "charbar__new", type: "button", title: "Начать новую игру" },
        "Новая игра",
      );
      btn.addEventListener("click", () =>
        confirmAction("Начать заново? Текущий прогресс будет потерян.", () => {
          void renderClassSelect();
        }),
      );
      return btn;
    })(),
  );
}

function renderInventory(): void {
  if (!player) return;
  invEl.replaceChildren();
  if (player.inventory_items.length === 0) return;
  invEl.append(el("div", { class: "inventory__title" }, "Инвентарь"));
  for (const it of player.inventory_items) {
    invEl.append(renderInvRow(it));
  }
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
    renderInventory();
  } catch (e) {
    showError((e as Error).message);
    btn.removeAttribute("disabled");
  }
}

function ghostBtn(label: string, run: (b: HTMLButtonElement) => Promise<void>): HTMLButtonElement {
  const btn = el("button", { class: "btn btn--ghost", type: "button" }, label) as HTMLButtonElement;
  btn.addEventListener("click", () => void run(btn));
  return btn;
}

const typeLabel: Record<string, string> = {
  weapon: "оружие",
  armor: "броня",
  consumable: "расходник",
};

function renderInvRow(it: PlayerView["inventory_items"][number]): HTMLElement {
  if (!player) throw new Error("no player");
  const slot = it.type === "weapon" ? "weapon" : it.type === "armor" ? "armor" : null;
  const isEquipped =
    (slot === "weapon" && player.equipped.weapon === it.id) ||
    (slot === "armor" && player.equipped.armor === it.id);

  const meta = it.type === "consumable" && it.charges !== undefined ? ` · заряды: ${it.charges}` : "";
  const row = el(
    "div",
    { class: "inv-row" + (isEquipped ? " is-equipped" : "") },
    el(
      "span",
      { class: "inv-info" },
      el("span", { class: "inv-name" }, it.name),
      el("span", { class: "inv-tag" }, (typeLabel[it.type] ?? it.type) + meta),
    ),
  );

  const actions = el("span", { class: "inv-actions" });
  if (slot) {
    actions.append(
      ghostBtn(isEquipped ? "Снять" : "Надеть", (b) =>
        invAction(b, () => (isEquipped ? unequip(slot) : equip(it.id))),
      ),
    );
  }
  if (it.type === "consumable") {
    actions.append(ghostBtn("Использовать", (b) => invAction(b, () => useItem(it.id))));
  }
  actions.append(ghostBtn("Выбросить", (b) => invAction(b, () => dropItem(it.id))));
  row.append(actions);
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
    pushDm(res.narrative, res.game_over);
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
    pushDm(res.narrative, res.game_over);
  } catch (e) {
    logEl.lastElementChild?.remove();
    showError((e as Error).message);
  } finally {
    setBusy(false);
  }
}

function pushDm(narrative: string, gameOver: boolean): void {
  log.push({ who: "dm", text: narrative });
  appendLog({ who: "dm", text: narrative });
  renderBar();
  renderInventory();
  if (gameOver) renderDefeat();
}

function renderDefeat(): void {
  const overlay = el(
    "div",
    { class: "defeat" },
    el("p", { class: "defeat__text" }, "Здесь твоя дорога обрывается."),
  );
  const again = el("button", { class: "btn btn--primary", type: "button" }, "Начать заново");
  again.addEventListener("click", () => void renderClassSelect());
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
      player = st.player;
      await renderPlay();
      if (st.last_narrative) {
        log.push({ who: "dm", text: st.last_narrative });
        appendLog({ who: "dm", text: st.last_narrative });
      }
      if (st.player.game_over) renderDefeat();
      return;
    }
  } catch (e) {
    showError((e as Error).message);
  }
  await renderClassSelect();
}

void bootstrap();
