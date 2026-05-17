#!/usr/bin/env python3
"""
Локальный тест DM-промпта на Qwen 2.5 через Ollama.

Подготовка (один раз):
  brew install ollama
  ollama serve &              # в отдельном терминале
  ollama pull qwen2.5:1.5b    # ~1 ГБ

Запуск:
  python3 scripts/test_dm.py
  python3 scripts/test_dm.py --model qwen2.5:1.5b
  python3 scripts/test_dm.py --player Кейн

Ctrl+C — выйти.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORLD = ROOT / "game/src/main/assets/world/tavern"
PROMPT_FILE = ROOT / "game/src/main/assets/prompts/dm_system_v1.txt"
OLLAMA_URL = "http://localhost:11434/api/chat"


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def build_world_block(player_name: str) -> str:
    locations = load_json(WORLD / "locations.json")
    npcs = load_json(WORLD / "npcs.json")
    items = load_json(WORLD / "items.json")
    quests = load_json(WORLD / "quests.json")
    player = load_json(WORLD / "player.json")
    player["name"] = player_name

    loc = locations[0]
    loc_npcs = [n for n in npcs if n["location_id"] == loc["id"]]
    npc_items = [i for i in items if i.get("owner_id") in {n["id"] for n in loc_npcs}]
    player_items = [i for i in items if i.get("owner_id") == player["id"]]
    active_quests = [q for q in quests if q["status"] == "active"]

    return f"""[МИР]

[PLAYER]
{json.dumps(player, ensure_ascii=False, indent=2)}

[INVENTORY ITEMS]
{json.dumps(player_items, ensure_ascii=False, indent=2)}

[CURRENT LOCATION]
{json.dumps(loc, ensure_ascii=False, indent=2)}

[NPCS IN LOCATION]
{json.dumps(loc_npcs, ensure_ascii=False, indent=2)}

[ITEMS FOR SALE]
{json.dumps(npc_items, ensure_ascii=False, indent=2)}

[ACTIVE QUESTS]
{json.dumps(active_quests, ensure_ascii=False, indent=2)}
"""


def build_system(player_name: str) -> str:
    template = PROMPT_FILE.read_text(encoding="utf-8")
    template = template.replace("{{player_name}}", player_name)
    return template + "\n\n" + build_world_block(player_name)


def call_ollama(model: str, system: str, history: list[dict]) -> str:
    payload = {
        "model": model,
        "messages": [{"role": "system", "content": system}] + history,
        "stream": False,
        "options": {"temperature": 0.7, "top_p": 0.9, "num_ctx": 8192},
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        OLLAMA_URL, data=data, headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            body = json.loads(resp.read().decode("utf-8"))
        return body["message"]["content"]
    except urllib.error.URLError as e:
        sys.exit(
            f"Не могу достучаться до Ollama по {OLLAMA_URL}: {e}\n"
            "Запусти 'ollama serve' и убедись, что модель скачана: 'ollama pull qwen2.5:1.5b'."
        )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="qwen2.5:1.5b")
    ap.add_argument("--player", default="Кейн")
    ap.add_argument(
        "--show-system",
        action="store_true",
        help="Распечатать собранный system prompt и выйти.",
    )
    args = ap.parse_args()

    system = build_system(args.player)

    if args.show_system:
        print(system)
        return

    print(f"== DM-test ==  model={args.model}  player={args.player}")
    print("Пиши действие игрока. /exit — выход, /reset — очистить историю.\n")
    print(
        "Подсказка для первого хода: 'Открываю дверь и вхожу внутрь.' — "
        "так модель сама раскроет сцену.\n"
    )

    history: list[dict] = []
    while True:
        try:
            action = input(f"{args.player}> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return
        if not action:
            continue
        if action == "/exit":
            return
        if action == "/reset":
            history = []
            print("(история очищена)\n")
            continue

        history.append({"role": "user", "content": action})
        reply = call_ollama(args.model, system, history)
        history.append({"role": "assistant", "content": reply})
        print(f"\nDM>\n{reply}\n")


if __name__ == "__main__":
    main()
