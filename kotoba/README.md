# narou kotoba Phase E Option B

Reference implementation of the narou (web novel platform) actor following Phase E Option B design pattern.

## Architecture

### Persistent Collections (PDS Records)

- `com.etzhayyim.narou.novel` — Novel metadata (title, description, genre, tags, chapter count, status)
- `com.etzhayyim.narou.chapter` — Chapter content (title, content, word_count, status with state machine)
- `com.etzhayyim.narou.worldSetting` — World/setting definitions (name, description)
- `com.etzhayyim.narou.character` — Character profiles (name, role, description)

### Record Identifiers (DIDs)

All records use narou-scoped DIDs:

```
did:web:narou.etzhayyim.com:novel:<slug>
did:web:narou.etzhayyim.com:chapter:<slug>
did:web:narou.etzhayyim.com:world:<slug>
did:web:narou.etzhayyim.com:character:<slug>
```

rkeys follow pattern: `<tier>-<slug>` (e.g., `novel-my-first-book`, `chapter-my-first-book-ch1`)

### Chapter Status Machine

Chapters follow a 4-state lifecycle:

```
draft → in_review → published → archived
  ↓       ↓          ↓
  archived (any state)
  draft (review back to draft)
```

Valid transitions:
- `draft` → `in_review`, `archived`
- `in_review` → `draft`, `published`, `archived`
- `published` → `archived`
- `archived` → (terminal)

## Operations

### Novels

- `createNovel(e, input)` — Create new novel (auto-slug title)
- `getNovel(e, {id})` — Retrieve novel by ID
- `listNovels(e, {genre?, status?, offset?, limit?})` — List with filters
- `searchNovels(e, {q, genre?, tag?, offset?, limit?})` — Full-text search

### Chapters

- `createChapter(e, {novel_id, chapter_num?, title, content?, ...})` — Create draft chapter
- `getChapter(e, {id})` — Retrieve chapter
- `listChapters(e, {novel_id, status?, offset?, limit?})` — List chapters for a novel
- `generateChapter(e, {chapter_id, prompt_hint?, word_count_target?})` — AI-generate chapter (placeholder)
- `publishChapter(e, {chapter_id, asset_manifest_uri?})` — Transition to published

### Worldbuilding

- `createWorldSetting(e, {novel_id, name, description?, ...})` — Define world setting
- `createCharacter(e, {novel_id, name, role?, description?, ...})` — Define character

## Data Model

### NovelRecord

```typescript
{
  did: string;
  title: string;
  description?: string;
  genre?: string;
  tags?: string[];
  status: "active" | ...;
  chapter_count: number;
  created_at: ISO8601;
}
```

### ChapterRecord

```typescript
{
  did: string;
  novel_id: string;
  chapter_num?: number;
  title: string;
  content?: string;
  status: "draft" | "in_review" | "published" | "archived";
  word_count?: number;
  published_at?: ISO8601;
  created_at: ISO8601;
}
```

### WorldSettingRecord

```typescript
{
  did: string;
  novel_id: string;
  name: string;
  description?: string;
  created_at: ISO8601;
}
```

### CharacterRecord

```typescript
{
  did: string;
  novel_id: string;
  name: string;
  role?: string;
  description?: string;
  created_at: ISO8601;
}
```

## Usage

```typescript
import { createNovel, createChapter, publishChapter } from "@etzhayyim/narou-kotoba";

const e = /* Etzhayyim instance */;

// Create novel
const novelRes = await createNovel(e, {
  title: "My First Novel",
  description: "An exciting story",
  genre: "fantasy",
});

if (novelRes.status === "registered") {
  const novelId = novelRes.id!;

  // Create chapter
  const chapRes = await createChapter(e, {
    novel_id: novelId,
    chapter_num: 1,
    title: "Chapter 1: The Beginning",
  });

  if (chapRes.status === "registered") {
    const chapterId = chapRes.id!;

    // Publish chapter
    await publishChapter(e, { chapter_id: chapterId });
  }
}
```

## Implementation Notes

- All write operations use `e.write()` via `@etzhayyim/sdk`
- Read operations use `e.read()` with type casting
- Collection names are lowercased NSID-style: `com.etzhayyim.narou.<entity>`
- Error handling: functions return `{status, error}` on failure
- Pagination: default limit 50, max 100
- Filtering: client-side post-filter after read (stateless)
- No RisingWave dependency (pure PDS + app-layer semantics)

## Next Steps

- Connect to AI model integration for `generateChapter` (currently placeholder)
- Add downstream platform dispatch (manga, syosetsu)
- Implement cross-novel reading progress tracking
- Add social features (follows, recommendations, ratings)
