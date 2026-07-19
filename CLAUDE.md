# etzhayyim-project-narou

小説・漫画自動生成プラットフォーム (`narou.etzhayyim.com`)。
Matrix protocol + BPMN2 で生成ワークフローを制御し、作品ごとに自己進化するエージェントを持つ。

## Reactive Runtime (Design D 準拠)

- **Input**: `subscribe-repos.handle-repo-commit` (`handleComAtprotoSyncSubscribeReposCommit`) で `generation_task` commit を即時処理
- **Processing**: `generation_task(create)` を検知して `run-generation-pipeline` を reactive 起動
- **Output (stream)**: `serve.handle-stream("stream-chapters")` を subscriber role + trust level で配信
- **Output (event cards)**: `wPublish(...)` で `narou-feed` / `narou-works` に即時反映
- **方針**: batch polling は導入しない。イベント駆動 (`handleComAtprotoSyncSubscribeReposCommit`) を主経路にする

## App Components

| Component | nanoid | 役割 |
|---|---|---|
| `etzhayyim-wasm-narou-nr0uxk2p` | `nr0uxk2p` | Main control plane + UI |

## DID Structure

| DID | 用途 |
|---|---|
| `did:web:narou.etzhayyim.com` (primary) | Platform agent |
| `did:web:narou.etzhayyim.com:work:{work_id}` (path) | 作品ごとの AI 著者 persona |
| `did:web:narou.etzhayyim.com:generator` (path) | テキスト/画像生成 agent |

## Lexicon Collections

| NSID | WRecord kind | SQL Label |
|---|---|---|
| `com.etzhayyim.narou.novel` | `novel` | `:Novel` |
| `com.etzhayyim.narou.chapter` | `chapter` | `:Chapter` |
| `com.etzhayyim.narou.character` | `character` | `:Character` |
| `com.etzhayyim.narou.world_setting` | `world_setting` | `:WorldSetting` |
| `com.etzhayyim.narou.generation_task` | `generation_task` | `:GenerationTask` |
| `com.etzhayyim.narou.agent_persona` | `agent_persona` | `:AgentPersona` |
| `com.etzhayyim.narou.image_asset` | `image_asset` | `:ImageAsset` |

## Domain WIT

- `etzhayyim:narou/generation@1.0.0` — create-work, generate-chapter, evolve-persona, publish-chapter
- `etzhayyim:narou/catalog@1.0.0` — get/list/search works+chapters

## Cross-App Invoke

- `Invoke("did:web:manga.etzhayyim.com", "submit-from-narou", params)` — 画像付きエピソード配信
- `Invoke("did:web:syosetsu.etzhayyim.com", "publish-chapter", params)` — テキストリーダー配信

## 設計権威ソース

`90-docs/260313-narou-novel-manga-generation-design.md`

## Key Architecture

- **テキスト生成**: OpenRouter `anthropic/claude-opus-4-6` (murakumo の public default override)
- **画像生成**: Murakumo animage `https://murakumo.etzhayyim.com/api/openai/v1`
- **生成ワークフロー**: BPMN2 `narou-generation-pipeline` (per chapter)
- **品質ゲート**: DMN `narou-quality-gating`
- **自己進化**: 作品ごとに `narou_agent_persona_current` で persona を管理し、章生成後に style_vector / character_rules_json / world_rules_json を更新
- **永続化**: kotodama WIT bindings + Arrow schema (詳細スキーマは設計権威ソース §6)
- **Entity Graph**: `narou:Work / narou:Character / narou:World / narou:Chapter` ノードを `entity_nodes_current` に登録

## Matrix Commands

| Event type | 説明 |
|---|---|
| `org.etzhayyim.command.narou.create_work` | 作品・世界観・初期 persona 作成 |
| `org.etzhayyim.command.narou.generate_chapter` | 章生成 BPMN 起動 |
| `org.etzhayyim.command.narou.evolve_agent` | persona 強制進化 (human feedback) |
| `org.etzhayyim.command.narou.publish_chapter` | 章を published 状態へ遷移 |

## Arrow Tables

| Table | 用途 |
|---|---|
| `narou_works_current` | 作品 current state |
| `narou_works_events` | 作品イベント append log |
| `narou_chapters_current` | 章 current state |
| `narou_chapters_events` | 章イベント append log |
| `narou_agent_persona_current` | 作品ごとの進化 persona (style_vector Float32[128]) |
| `narou_generation_tasks_current` | 生成タスクキュー + BPMN instance 追跡 |
| `narou_image_assets_current` | 画像アセットメタデータ |

RLS: 全テーブルに `org_id`, `user_id`, `actor_id` 必須 (詳細: `60-apps/CLAUDE.md`)。

## API Endpoints

- App: `https://nr0uxk2p.etzhayyim.com`
- XRPC: `https://nr0uxk2p.etzhayyim.com/xrpc`

## Smoke Test

```bash
curl https://nr0uxk2p.etzhayyim.com/health
curl -X POST https://nr0uxk2p.etzhayyim.com/xrpc/etzhayyim.narou.v1.NarouQueryService/ListWorks \
  -H "Content-Type: application/json" -d '{"org_id":"anon","limit":10,"offset":0}'
```

## Quality Evaluation

| 評価 | 関数 | 用途 |
|---|---|---|
| **章一貫性スコア** | `evaluateChapterCoherence()` | LLM 4 軸評価 (文体一貫性/キャラクター整合性/プロット進行/読みやすさ, 各 25%)。章生成後に実行、`coherence_score` に保存 |
| **ペルソナ適応度** | `computePersonaFitness()` | 直近 10 章の coherence_score 加重平均 (指数減衰)。`fitness_score` に保存 |

**禁止**: `fitness_score` / `coherence_score` のハードコード値。初期値 `0.0` のみ許可。

## LLM Override ルール

OpenRouter claude-opus-4-6 は `60-apps/CLAUDE.md` §Public LLM Standard の override 扱い。
narou 以外のコードにこの override を伝播しない。
