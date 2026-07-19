# Migration TODO — etzhayyim-project-narou

**Status**: 🔄 TRANSFORM (partial-merge) — net-new files merged from etzhayyim archive 2026-06-02.
Existing etzhayyim files were NOT overwritten (additive merge).

**Codemod pending** (substrate-boundary ADR-2605172000 / 2605172100):
- Reconcile archive-origin code with the etzhayyim kotoba/on-chain version where they overlap.
- Strip any RisingWave / fiat → AT MST + IPFS + Base L2 + USDC/ERC-4337.

## Excluded from this migration (2026-06-02)
- `ghosthacker/260208-spirit-in-physics/**` — entire embedded web-manga sub-project
  (~311 files incl ~43MB webp/jpg ComfyUI assets + an `apps/web` client that imports
  `@atproto/api` directly, tripping the substrate-boundary lint per ADR-2605172000).
  Migrate it separately after a codemod (route substrate access through `@etzhayyim/sdk`);
  decide whether the generated image assets belong in-repo. narou core (appview / lg
  LangGraph server / scripts / content bundle) was migrated.
