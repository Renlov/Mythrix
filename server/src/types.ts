export interface Env {
  DB: D1Database;
  DEEPSEEK_API_KEY: string;
  TELEGRAM_BOT_TOKEN: string;
  DEEPSEEK_BASE_URL: string;
  DEEPSEEK_MODEL: string;
  WORLD_ID: string;
  RULESET_ID: string;
  DEV_BYPASS_AUTH?: string; // "1" — обход проверки initData для локальной разработки
}

export interface CombatStats {
  hp: number;
  ac: number;
  attack_die: string;
  attack_bonus: number;
}

export interface Atmosphere {
  mood: string;
  tags: string[];
  nuance: string;
}

export interface Location {
  id: string;
  name: string;
  parent_id: string | null;
  description: string;
  atmosphere: Atmosphere;
  tags: string[];
  connections: string[];
  npcs: string[];
  enemies?: string[];
  items: string[];
}

export interface Npc {
  id: string;
  name: string | null;
  name_reveal?: string;
  role_label: string;
  location_id: string;
  role: string;
  race?: string;
  age?: number;
  description: string;
  dialogue_style?: string;
  opening_line?: string;
  inventory: string[];
  knows_about?: string[];
  knowledge?: string[];
  combat?: CombatStats;
}

export interface Item {
  id: string;
  name: string;
  type: string;
  subtype?: string;
  damage_die?: string;
  armor_bonus?: number;
  two_handed?: boolean;
  heal_die?: string; // для расходников-лечения, например "d4+2" — бросок считает сервер
  uses?: number; // число зарядов расходника (по умолчанию 1)
  weight?: number;
  price: number;
  location_id: string | null;
  owner_id: string | null;
  description: string;
  unlocks?: string[];
}

export interface Enemy extends CombatStats {
  id: string;
  name: string;
  description: string;
}

export interface QuestDef {
  id: string;
  name: string;
  stages: { id: number; goal: string }[];
  related_npcs?: string[];
  related_locations?: string[];
  narrative_arc?: string;
}

export interface PlayerState {
  telegram_user_id: string;
  world_id: string;
  ruleset_id: string;
  class_id: string;
  name: string;
  level: number;
  hp: number;
  max_hp: number;
  location_id: string;
  gold: number;
  inventory: string[];
  equipped: { weapon?: string; armor?: string };
  item_charges: Record<string, number>; // оставшиеся заряды расходников {item_id: n}
  known_npcs: string[];
  status_effects: string[];
  combat_session: CombatSession | null;
  game_over: boolean;
}

export interface CombatSession {
  enemyId: string;
  enemyHp: number;
  playerTurn: boolean;
}

export interface QuestProgress {
  quest_id: string;
  status: string;
  stage: number;
}

// Блок [EVENTS] из ответа DM.
export interface Intent {
  type: string;
  [key: string]: unknown;
}

export interface DmEvents {
  intents: Intent[];
  location_change: string | null;
  scene_ended: boolean;
  scene_summary: string | null;
  atmosphere_shift?: string | null;
}
