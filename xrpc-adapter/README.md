# narou XRPC Adapter

CF Worker that exposes the 11 kotoba commands as XRPC endpoints.

## Endpoints

**POST** methods:
- `createNovel` — register new web novel
- `createChapter` — create chapter draft
- `generateChapter` — AI-generate chapter content
- `publishChapter` — publish chapter to readers
- `createWorldSetting` — define world/lore
- `createCharacter` — register character sheet

**GET** methods:
- `getNovel?novelId=...` — fetch novel with metadata
- `listNovels?limit=...&offset=...` — paginated novels
- `searchNovels?query=...` — full-text search
- `getChapter?novelId=...&chapterNum=...` — chapter by number
- `listChapters?novelId=...` — all chapters for novel

## Setup

```bash
cd 60-apps/etzhayyim-project-narou/xrpc-adapter
npm install
```

## Development

```bash
npm run dev
# Worker listens on http://localhost:8787
```

## Deploy

```bash
wrangler deploy
# Deploys to narou.etzhayyim.com/xrpc/*
```

See ADR-2605210000 for design context.
