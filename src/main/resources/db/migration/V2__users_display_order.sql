-- 申請一覧などでのスタッフの並び順（申請一覧のドラッグで変更する）。未設定は後ろに並べる
ALTER TABLE users ADD COLUMN display_order INT;
