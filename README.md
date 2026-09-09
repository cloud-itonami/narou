# narou — web 小説・漫画自動生成プラットフォーム（app repo）

**名乗り**: `narou` は「小説家になろう」系 web 小説プラットフォームから借りた
メタファ名で、名前は機能を示さない。実体は **`narou.etzhayyim.com` で動く
小説・漫画自動生成プラットフォームの application repository** である。
生成ワークフローを Matrix protocol + BPMN2 で制御し、作品ごとの AI 著者
persona（`did:web:narou.etzhayyim.com:work:{work_id}`）を持つ。詳細な
コンポーネント表・DID 構造・Lexicon collection は `CLAUDE.md` を参照。

⚠ 本 repo 内の `kotoba/` ディレクトリは narou の **11 kotoba commands の
TypeScript reference 実装**（`@etzhayyim/narou-kotoba`）であって、
`kotoba-lang` org の Kotoba 言語 toolchain とは無関係。名前だけで判断しない。

## 出自

etzhayyim monorepo `60-apps/etzhayyim-project-narou`（source revision
`011cf6e383bd`）から 2026-06-02 に partial-merge で抽出（`migration.edn` /
`MIGRATION-TODO.md`）。埋め込み web-manga sub-project
（`ghosthacker/260208-spirit-in-physics/**`、~43MB の画像 asset を含む）は
substrate-boundary lint（ADR-2605172000）のため**この migration から除外**
されており、この repo には入っていない。ライセンスは Apache-2.0 +
etzhayyim Charter Compliance Rider v3.1（`NOTICE`）。

## 実際のディレクトリ構成

| path | 中身 |
|---|---|
| `kotoba/` | 11 kotoba commands の TS reference 実装 + vitest（`test/narou.test.ts`、18 tests） |
| `lg/` | LangGraph graphs + server の **langgraph-clj port**（ADR-2606280030。clj twin が canonical、python twin は削除済み。`lg/clj/README.md`） |
| `xrpc-adapter/` | kotoba commands を XRPC endpoint として公開する Cloudflare Worker（TS） |
| `appview/` | App 版への段階移行の配置先（`narou-mcp-component`: reagent + re-frame（cljs、jp-go-dds） + wrangler） |
| `scripts/` | `content/sources/*.txt` → JSON-LD bundle の生成器（python、`docs/operator-quickstart.md` 参照） |
| `content/` | `sources/`（原稿 txt）と `generated/`（JSON-LD bundle、生成物） |
| `bpmn/` | 生成ワークフローの BPMN2 定義（`narou.bpmn`） |
| `config/` | cron 連携サンプル（`content_jsonld.cron`） |
| `docs/` | WebManga agent 設計（`260304-webmanga-agent-design.md`）・operator quickstart |

旧 README が記述していた `apps/server` / `apps/web` / `apps/legacy-runtime`
構成は monorepo 時代のもので、**この repo には存在しない**（2026-08-24 に
実構成へ書き直した）。

## 動かす・検査する

検証済みの手順（実行コマンド・所要・確認方法つき）は
**[`docs/operator-quickstart.md`](docs/operator-quickstart.md)** に一本化した。
最短:

```bash
# 原稿 → JSON-LD bundle（決定的: content sha は committed bundle と一致する）
python3 scripts/generate_content_jsonld.py \
  --source-dir content/sources \
  --output content/generated/content.bundle.jsonld

# TS actor のテスト（vitest、18 件）
cd kotoba && npm install --ignore-scripts --no-audit --no-fund && npm test
```

⚠ python script の argparse **デフォルトパスは monorepo 時代のもの**
（`projects/etzhayyim-project-narou/...`）なので、この repo 単体では上記の
とおり `--source-dir` / `--output` を必ず明示する。

## 隣接 repo との境界

- 生成エンジン・LLM 呼び出しの実体はこの repo に無い。`lg/` は
  `com-junkawasaki/langgraph-clj` / `langchain-clj` を pin して使う消費者。
- `kotoba/` の依存 `@etzhayyim/sdk`（+ dev の `@etzhayyim/sdk-mock`）は
  etzhayyim org の git 依存で、substrate access はすべて SDK 経由
  （ADR-2605172000 の boundary をこの repo 側では越えない）。
- 投稿先 `manga.etzhayyim.com` との contract は
  `docs/260304-webmanga-agent-design.md` が正本。
