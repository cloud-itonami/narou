# etzhayyim-project-narou (ghosthacker-based refactor)

`ghosthacker/apps` の責務分割をベースに、Narouプロジェクトを次の構造へ整理しました。

## New structure

- `apps/server`: API・ジョブ管理層
- `apps/web`: Studio UI層
- `apps/legacy-runtime`: legacy runtime統合層
- `scripts`: JSON-LD生成・スケジューリング
- `content/sources`: 原稿ソース（txt）
- `content/generated`: JSON-LD出力

構造定義は `apps/PROJECT_STRUCTURE.jsonld` に記述しています。

## JSON-LD content 自動生成

### 1) 単発実行

```bash
python3 60-apps/etzhayyim-project-narou/70-tools/70-tools/70-tools/scripts/generate_content_jsonld.py
```

### 2) スケジューラー実行（例: 10分ごとに3回）

```bash
python3 60-apps/etzhayyim-project-narou/70-tools/70-tools/70-tools/scripts/schedule_jsonld_generation.py \
  --interval-seconds 600 \
  --runs 3
```

### 3) cron 連携サンプル

`config/content_jsonld.cron` を利用して、運用環境では cron/systemd timer から定期実行できます。
