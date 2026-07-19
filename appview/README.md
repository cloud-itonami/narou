# etzhayyim-project-narou App migration

このディレクトリは `legacy-runtime` 実装を残したまま、App 版を段階移行するための配置先です。

## 対象 App services

- `etzhayyim-manga-oq6mkdm9`
- `etzhayyim-music-2dswasjt`
- `etzhayyim-td-manga`
- `etzhayyim-td-music`

## App 実装方針

- 各 service は `projects/*/wasm/*-component` として順次実装。
- 既存 App runtime は互換運用のため維持。
- HTTP/cron/job エンドポイントから優先して移植。

## 実装済みコンポーネント

- `narou-mcp-component`
  - `POST /xrpc` を提供
  - `GET /...` で静的フロント (`svelte/build`) を配信
  - `GET /health`, `GET /healthz` を提供
