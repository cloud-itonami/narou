# operator quickstart — narou

この手順は **2026-08-24 に repo tip（main）で実際に踏んで**確認した。
踏んでいない手順は書いていない。実行環境: macOS / python3 / node v26 /
npm 11.19。所要は全部で 5 分弱（npm install の 3 分が大半）。

前提はこの repo の checkout のみ。特別な credential は要らない
（deploy 系はこの quickstart の範囲外 —— 末尾「ここに書いていないもの」参照）。

## 1. 原稿 → JSON-LD bundle の生成（~1 秒）

`content/sources/*.txt` を読んで schema.org の Dataset
（`content/generated/content.bundle.jsonld`）を生成する。

```bash
python3 scripts/generate_content_jsonld.py \
  --source-dir content/sources \
  --output content/generated/content.bundle.jsonld
```

⚠ **フラグは省略できない。** argparse のデフォルトは monorepo 時代のパス
（`projects/etzhayyim-project-narou/...`）を指しており、この repo 単体では
存在しない。省略すると `--source-dir` の glob が空になり、**空の bundle が
正常終了で出る**（エラーにならない）。

**確認方法**: 生成は content について決定的。各 `hasPart` の `sha256` は
原稿ファイルの sha256 で、原稿を変えていなければ committed bundle と一致する
（実測 2026-08-24: `chapter-001` の sha `b9803bda4f79c4ca…` が一致）。
`dateModified` だけは実行時刻で毎回変わる —— sha が一致していれば
timestamp の diff は再生成の正常な痕跡。

### 定期実行（scheduler、1 回だけ回して確認: ~1 秒）

```bash
python3 scripts/schedule_jsonld_generation.py --runs 1 --interval-seconds 1 \
  --generator-script scripts/generate_content_jsonld.py \
  --source-dir content/sources \
  --output content/generated/content.bundle.jsonld
```

`--generator-script` も同じ理由で明示が必須。成功時の出力は
`[<UTC>] run=1 status=ok`。`--runs 0`（デフォルト）は無限ループなので、
動作確認は必ず `--runs 1` で行う。運用の cron 例は
`config/content_jsonld.cron`。

## 2. kotoba TS actor のテスト（install ~3 分 + test ~1 秒）

11 kotoba commands の reference 実装（`kotoba/`）の vitest。

```bash
cd kotoba
npm install --ignore-scripts --no-audit --no-fund
npm test
```

期待値: **Test Files 1 passed / Tests 18 passed**（`test/narou.test.ts`）。

- install が 3 分かかるのは `@etzhayyim/sdk` / `@etzhayyim/sdk-mock` が
  GitHub git 依存（sha pin）のため。`--ignore-scripts` でも 18 tests は
  全部通る（lifecycle script に依存しない）ことを実測済み。
- ⚠ user レベル `~/.npmrc` に `allow-scripts` 設定があるマシンでは npm 11.19 が
  `EALLOWSCRIPTS` で install 自体を拒否する（この workspace の開発機で実測）。
  その場合は user 設定を触らず、空の userconfig を挟む:

  ```bash
  touch /tmp/empty-npmrc
  NPM_CONFIG_USERCONFIG=/tmp/empty-npmrc npm install --ignore-scripts --no-audit --no-fund
  ```

## ここに書いていないもの（未検証 —— 書いていないのは怠慢ではなく境界）

- **`lg/` の server / test**: scoped `bb.edn`（`kbb -M:test` / `kbb -M:server`）だが、
  この workspace は script host として bb を退役済み（ADR-2607173000）で、
  今回は踏んでいない。エントリポイントは `lg/clj/README.md` が正本。
- **`xrpc-adapter/` / `appview/narou-mcp-component` の deploy**: Cloudflare
  credential が要る。wrangler config は各ディレクトリの `wrangler.jsonc`。
- 上記を踏んで通したら、この文書に手順と期待値を**実測してから**追記すること。
  踏んでいない手順をここに足さない。
