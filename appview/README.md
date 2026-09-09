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
  - `GET /...` で静的フロント（reagent + re-frame + jp-go-dds、`cljs/public`
    をビルド出力先とする shadow-cljs バンドル）を配信
  - ⚠ 2026-09-10 の Svelte → cljs 移行で `wrangler.jsonc` の `main` を
    撤去した（`src/app.ts` は `env.ASSETS.fetch()` を呼ばないため、`main`
    に指定すると assets の手前に Worker が立って静的配信自体が壊れる）。
    結果として、この wrangler config は**現在 assets 配信専用**であり、
    `POST /xrpc`・`GET /health`・`GET /healthz` はこの Worker からは
    提供されない（`src/app.ts` 自体は健在で `/health`・`/xrpc/com.etzhayyim.narou.*`
    を実装しているが、移行前から `main` には指定されておらず未配線のまま）。
    旧 SvelteKit 側の XRPC handler（AgentGateway MCP router へのプロキシ）は
    `svelte/src/routes/xrpc/[...path]/+server.ts` として動いていたが、
    `svelte/` 撤去に伴い `src/xrpc-proxy.ts` へ無改造で退避し、未配線のまま
    保存してある。どちらの実装を配線するかはフロントエンド移行の範囲外の
    決定として残す（wrangler config の変更自体は UNVERIFIED —— `wrangler deploy`
    は未実行）。
