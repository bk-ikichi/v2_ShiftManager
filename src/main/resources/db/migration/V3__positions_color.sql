-- 転記画面・日別一覧のバーの色（util/BarColor のキー）。既存のポジションはグレー
ALTER TABLE positions ADD COLUMN color VARCHAR(20) NOT NULL DEFAULT 'gray';
