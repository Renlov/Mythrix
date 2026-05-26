// Клиент к Worker. Ключ DeepSeek живёт только на сервере — здесь его нет.
const API_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? "http://localhost:8787";

export interface ClassInfo {
  id: string;
  name: string;
  description: string;
  avatar?: string;
}

export interface InventoryItem {
  id: string;
  name: string;
  type: string;
  subtype?: string;
  description?: string;
  price: number;
  damage_die?: string;
  armor_bonus?: number;
  two_handed?: boolean;
  uses?: number;
  charges?: number; // оставшиеся заряды расходника
}

export interface PlayerView {
  name: string;
  class_id: string;
  level: number;
  hp: number;
  max_hp: number;
  location_id: string;
  gold: number;
  inventory: string[];
  equipped: { weapon?: string; armor?: string };
  game_over: boolean;
  inventory_items: InventoryItem[];
}

function initData(): string {
  return window.Telegram?.WebApp?.initData ?? "";
}

async function post<T>(path: string, body: Record<string, unknown>): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ initData: initData(), ...body }),
  });
  const data = (await res.json()) as T & { error?: string };
  if (!res.ok) throw new Error(data.error ?? `HTTP ${res.status}`);
  return data;
}

export async function getClasses(): Promise<ClassInfo[]> {
  const res = await fetch(`${API_URL}/classes`);
  const data = (await res.json()) as { classes: ClassInfo[] };
  return data.classes;
}

export interface StateResponse {
  player: PlayerView | null;
  last_narrative?: string | null;
  game_over?: boolean;
}

export const getState = () => post<StateResponse>("/state", {});

export interface NewGameResponse {
  ok: boolean;
  player: PlayerView;
}

export const newGame = (classId: string, name: string) =>
  post<NewGameResponse>("/new-game", { class_id: classId, name });

export interface TurnResponse {
  narrative: string;
  player: PlayerView;
  game_over: boolean;
  warnings: string[];
  context_usage: number;
}

export const sendTurn = (action: string) => post<TurnResponse>("/turn", { action });

export const equip = (itemId: string) =>
  post<{ ok: boolean; player: PlayerView }>("/equip", { item_id: itemId });

export const unequip = (slot: "weapon" | "armor") =>
  post<{ ok: boolean; player: PlayerView }>("/unequip", { slot });

export const useItem = (itemId: string) =>
  post<{ ok: boolean; player: PlayerView; healed: number }>("/use", { item_id: itemId });

export const dropItem = (itemId: string) =>
  post<{ ok: boolean; player: PlayerView }>("/drop", { item_id: itemId });
