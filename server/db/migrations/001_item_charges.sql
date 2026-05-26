-- Миграция для уже существующих БД: добавляет колонку зарядов расходников.
-- Для свежей БД колонка уже есть в schema.sql — эту миграцию прогонять не нужно.
-- Локально:  wrangler d1 execute mythrix --local  --file=db/migrations/001_item_charges.sql
-- Удалённо:  wrangler d1 execute mythrix --remote --file=db/migrations/001_item_charges.sql
ALTER TABLE players ADD COLUMN item_charges TEXT NOT NULL DEFAULT '{}';
