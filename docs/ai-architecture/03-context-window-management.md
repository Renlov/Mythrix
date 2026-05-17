# 03. Управление контекстным окном (Вариант D)

Окно Qwen 2.5 1.5B — 32 768 токенов. Чтобы не упереться, используется
гибридная стратегия: скользящее окно внутри сцены + полная очистка
с архивацией саммари на границе сцены.

## Внутри сцены — скользящее окно + саммаризация

- Держим последние ~10 реплик дословно.
- При приближении к лимиту самые старые 5-6 реплик сжимаются в 1-2
  предложения и кладутся в блок `[SCENE SUMMARY]`.
- Исходные реплики удаляются из контекста (но остаются в SQLite-логе).

## На границе сцены — финализация и очистка

Триггеры границы:
- изменился `player.location_id`
- начался/закончился диалог с NPC
- начался/закончился бой
- прошло игровое время (сон, путешествие)
- DM прислал `events.scene_ended = true`

При срабатывании триггера:
1. Финализируется саммари сцены (1-3 предложения).
2. Саммари сохраняется в RAG как чанк типа `event`, привязанный к
   `location_id` сцены.
3. Скользящее окно реплик очищается.

## При возврате в локацию

RAG-поиск по `entity_type=event` + `location_id` автоматически подтянет
саммари прошлых визитов. DM узнает, что «игрок украл кошелёк у бармена
2 сцены назад», без хранения этого в активном контексте.

## Что НЕ стирается никогда

- `player.json` (это состояние, не контекст)
- Активные квесты
- Любые саммари (они в RAG)

## Псевдокод цикла хода

```
on player_action:
    if scene_window.token_count > THRESHOLD:
        old = scene_window.take_oldest(6)
        summary = summarize(old)
        scene_summary.append(summary)
    
    prompt = assemble_context(player, scene, rag, scene_summary, scene_window, action)
    response = model.generate(prompt)
    narrative, events = parse(response)
    
    apply_events(events)
    scene_window.append(action, narrative)
    
    if events.scene_ended:
        final_summary = scene_summary + summarize(scene_window)
        rag.add_chunk(type="event", location_id=current_loc, text=final_summary)
        scene_window.clear()
        scene_summary.clear()
```
