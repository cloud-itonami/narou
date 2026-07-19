# etzhayyim-narou WebManga Agent Design

## 1. Goal
- `etzhayyim-narou` 上で小説/プロットから WebManga を自動生成する。
- 生成した作品を `manga.etzhayyim.com` に Matrix command で投稿し、Query API で状態確認する。
- 失敗時に再実行しても重複投稿しない（idempotent）。

## 2. Agent Name and Responsibility
- Agent ID: `narou.webmanga.publisher.v1`
- Responsibility:
  - 入力（novel_id, chapter群, style）から漫画投稿 payload を生成。
  - 品質ゲートを通過したものだけ `manga.etzhayyim.com` に投稿。
  - 投稿結果（content_id）を narou 側に記録。

## 3. Input / Output Contract
Input
```json
{
  "project_id": "proj-...",
  "novel_id": "novel-...",
  "target_chapter_ids": ["chapter-001", "chapter-002"],
  "style_profile": "webtoon-cinematic",
  "target_genre": "fantasy",
  "priority": "normal",
  "request_id": "req-..."
}
```

Output
```json
{
  "workflow_id": "wf-...",
  "status": "submitted|failed|gated",
  "manga_content_id": "manga-...",
  "idempotency_key": "narou:novel-...:chapter-...:v1",
  "published_event": "org.etzhayyim.command.manga.submit-from-narou",
  "errors": []
}
```

## 4. Sub-Agent Topology
- `planner.agent`
  - chapter群から漫画化対象を決定（話数圧縮・分割方針）。
- `script.agent`
  - ネーム用 script（ページ/コマ単位）を生成。
- `storyboard.agent`
  - panel plan（カメラ、セリフ配置、見開き）を生成。
- `render.agent`
  - 画像生成要求（prompt/style）を作成し page asset を生成。
- `qc.agent`
  - 禁止表現、年齢レーティング、画像品質、可読性を検査。
- `publish.agent`
  - `narou.manga.submit` を実行して `manga.etzhayyim.com` に投稿。
- `audit.agent`
  - 投稿証跡（request/response、hash、timing）を保存。

## 5. Workflow State Machine
1. `QUEUED`
2. `PLANNED`
3. `SCRIPTED`
4. `STORYBOARDED`
5. `RENDERED`
6. `QC_PASSED` or `QC_FAILED`
7. `SUBMITTING`
8. `SUBMITTED` or `SUBMIT_FAILED`
9. `COMPLETED`

Rule
- 状態遷移の書き込みは `orchestrator` のみ（single-writer）。
- 各 sub-agent はイベントのみを emit する。

## 6. Publish Contract (Narou -> Manga)
投稿は `narou.manga.submit` ツールを経由して `Command=Matrix`, `Query=XRPC` で実施する。

Required fields
- `title`
- `idempotency_key`

Recommended fields
- `project_id`, `novel_id`, `author`, `genre`, `synopsis`, `tags`

Write Contract
- room: `#manga-commands:etzhayyim.com`
- event: `org.etzhayyim.command.manga.submit-from-narou`
- content:
  - `service: etzhayyim.manga.v1.MangaCommandService`
  - `method: SubmitFromNarou`
  - `payload: {...}`
  - `source_app: narou.etzhayyim.com`
- compatibility ingress:
  - `POST https://manga.etzhayyim.com/xrpc/etzhayyim.manga.v1.MangaCommandService/SubmitFromNarou`

Read Endpoint (polling)
- `POST https://manga.etzhayyim.com/xrpc/etzhayyim.manga.v1.MangaQueryService/GetSubmissionStatus`

Idempotency key policy
- format: `narou:{novel_id}:{chapter_range}:{script_version}`
- 同一 key で再送時は同一 `content_id` を期待する。

## 7. Quality Gates
Hard gate (publish block)
- 画像欠落なし（page_count > 0）
- セリフ可読性（最小フォント相当閾値）
- レーティング判定が未設定でない
- NGタグ（規約違反）なし

Soft gate (warn only)
- スタイル一貫性スコア
- 1ページあたり情報密度

## 8. Retry / Failure Strategy
- `render.agent`: max 3 retries, backoff `30s/90s/270s`
- `publish.agent`: max 3 retries, same `idempotency_key`
- `SUBMIT_FAILED` 時:
  - HTTP 4xx: payload修正タスクへ
  - HTTP 5xx/network: retry queueへ

## 9. Security and Policy
- `SubmitFromNarou` の正規 caller は Matrix room membership / app credential を前提にする。
- compatibility ingress には将来的に `Authorization` 必須化（JWT or shared secret）。
- 投稿 payload に PII を含めない。
- 監査ログは `request_id`, `workflow_id`, `idempotency_key`, `content_id` を保存。

## 10. Observability
Metrics
- `webmanga_generation_latency_seconds`
- `webmanga_qc_pass_rate`
- `webmanga_submit_success_rate`
- `webmanga_submit_deduplicated_rate`

SLO
- Submit success rate >= 99.5% / day
- P95 submit latency < 5s

## 11. Implementation Plan (Incremental)
1. Add `narou.webmanga.publisher.v1` orchestration entrypoint in `narou-mcp-component`.
2. Add persistent workflow store (`workflow_id`, state, checkpoints).
3. Bind `publish.agent` to existing `narou.manga.submit` tool.
4. Add QC evaluator and gate reasons to response payload.
5. Add metrics export and alert thresholds.

## 12. MVP Acceptance Criteria
- novel 1本から manga submit が end-to-end で成功する。
- 同一 `idempotency_key` 再実行時に重複投稿されない。
- QC fail 時は submit が実行されない。
- 実行結果に `manga_content_id` と `workflow_id` が残る。
