import type { Etzhayyim } from "@etzhayyim/sdk";
import {
  chapterDid,
  chapterRkey,
  chapterSlug,
  novelRkey,
  type ChapterRecord,
  type ChapterStatus,
  type ChapterView,
  type CreateChapterInput,
  type CreateChapterOutput,
  type GenerateChapterInput,
  type GenerateChapterOutput,
  type GetChapterInput,
  type GetChapterOutput,
  type ListChaptersInput,
  type ListChaptersOutput,
  type NovelRecord,
  type PublishChapterInput,
  type PublishChapterOutput,
} from "./types.js";

const CHAPTER_COLLECTION = "com.etzhayyim.narou.chapter";
const NOVEL_COLLECTION = "com.etzhayyim.narou.novel";

const VALID_TRANSITIONS: Record<ChapterStatus, ChapterStatus[]> = {
  draft: ["in_review", "published", "archived"],
  in_review: ["draft", "published", "archived"],
  published: ["archived"],
  archived: [],
};

function isValidChapterId(id: string): boolean {
  return /^[a-z0-9-]{1,64}$/i.test(id);
}

function canTransition(from: ChapterStatus, to: ChapterStatus): boolean {
  return VALID_TRANSITIONS[from].includes(to);
}

export async function createChapter(
  e: Etzhayyim,
  input: CreateChapterInput
): Promise<CreateChapterOutput> {
  if (!input.novel_id || !input.title || input.title.trim().length === 0) {
    return { status: "rejected", error: "missingRequiredFields" };
  }

  // Validate novel_id first
  if (!isValidChapterId(input.novel_id)) {
    return { status: "rejected", error: "invalidNovelId" };
  }

  const chapterIdBase = chapterSlug(input.title).slice(0, 32);
  const chapterId = input.chapter_num
    ? `${input.novel_id}-ch${input.chapter_num}`
    : `${input.novel_id}-${chapterIdBase}`;
  if (!isValidChapterId(chapterId)) {
    return { status: "rejected", error: "invalidChapterId" };
  }
  const rkey = chapterRkey(chapterId);
  const existing = await e
    .read<ChapterRecord>({ collection: CHAPTER_COLLECTION, rkey })
    .catch(() => ({ records: [] }));
  if (existing.records[0]?.value) {
    return {
      status: "alreadyExists",
      id: chapterId,
      chapter_uri: existing.records[0].uri,
    };
  }
  const novelResp = await e
    .read<NovelRecord>({
      collection: NOVEL_COLLECTION,
      rkey: novelRkey(input.novel_id),
    })
    .catch(() => ({ records: [] }));
  if (!novelResp.records[0]?.value) {
    return { status: "rejected", error: "novelNotFound" };
  }
  const did = chapterDid(chapterId);
  const now = new Date().toISOString();
  const record: ChapterRecord = {
    did,
    novel_id: input.novel_id,
    chapter_num: input.chapter_num,
    title: input.title,
    content: input.content,
    status: "draft",
    created_at: now,
  };
  const receipt = await e.write({
    collection: CHAPTER_COLLECTION,
    record: record as unknown as Record<string, unknown>,
    rkey,
  });
  return {
    status: "registered",
    id: chapterId,
    chapter_uri: receipt.uri,
  };
}

export async function getChapter(
  e: Etzhayyim,
  input: GetChapterInput
): Promise<GetChapterOutput> {
  if (!input.id) return { error: "missingChapterId" };
  const resp = await e
    .read<ChapterRecord>({
      collection: CHAPTER_COLLECTION,
      rkey: chapterRkey(input.id),
    })
    .catch(() => ({ records: [] }));
  const r = resp.records[0];
  if (!r) return { error: "notFound" };
  const view: ChapterView = { ...r.value, chapter_uri: r.uri };
  return { chapter: view };
}

export async function listChapters(
  e: Etzhayyim,
  input: ListChaptersInput
): Promise<ListChaptersOutput> {
  if (!input.novel_id) {
    return { chapters: [], total: 0, offset: 0, limit: 0 };
  }
  const offset = input.offset ?? 0;
  const limit = Math.min(input.limit ?? 50, 100);
  const resp = await e.read<ChapterRecord>({
    collection: CHAPTER_COLLECTION,
    limit: limit + offset,
  });
  const filtered = resp.records
    .filter((r) => {
      const v = r.value;
      if (v.novel_id !== input.novel_id) return false;
      if (input.status && v.status !== input.status) return false;
      return true;
    })
    .slice(offset, offset + limit);
  const items: ChapterView[] = filtered.map((r) => ({
    ...r.value,
    chapter_uri: r.uri,
  }));
  return {
    chapters: items,
    total: filtered.length,
    offset,
    limit,
  };
}

export async function generateChapter(
  e: Etzhayyim,
  input: GenerateChapterInput
): Promise<GenerateChapterOutput> {
  if (!input.chapter_id) {
    return { status: "rejected", error: "missingChapterId" };
  }
  const rkey = chapterRkey(input.chapter_id);
  const resp = await e
    .read<ChapterRecord>({ collection: CHAPTER_COLLECTION, rkey })
    .catch(() => ({ records: [] }));
  const existing = resp.records[0]?.value;
  if (!existing) {
    return { status: "rejected", error: "chapterNotFound" };
  }
  if (existing.status !== "draft") {
    return {
      status: "rejected",
      error: "chapterNotDraft",
      chapter_id: input.chapter_id,
    };
  }
  const targetWordCount = input.word_count_target ?? 3000;
  const mockContent =
    `[AI-Generated Content]\n\nThis is a placeholder for AI-generated chapter content.\n` +
    `Target word count: ${targetWordCount}\n` +
    `Prompt hint: ${input.prompt_hint || "No hint provided"}`;
  const wordCount = Math.floor(targetWordCount * 0.95 + Math.random() * targetWordCount * 0.1);
  const merged: ChapterRecord = {
    ...existing,
    content: mockContent,
    word_count: wordCount,
  };
  await e.write({
    collection: CHAPTER_COLLECTION,
    record: merged as unknown as Record<string, unknown>,
    rkey,
  });
  return {
    status: "completed",
    chapter_id: input.chapter_id,
    word_count: wordCount,
  };
}

export async function publishChapter(
  e: Etzhayyim,
  input: PublishChapterInput
): Promise<PublishChapterOutput> {
  if (!input.chapter_id) {
    return { status: "rejected", error: "missingChapterId" };
  }
  const rkey = chapterRkey(input.chapter_id);
  const resp = await e
    .read<ChapterRecord>({ collection: CHAPTER_COLLECTION, rkey })
    .catch(() => ({ records: [] }));
  const existing = resp.records[0]?.value;
  if (!existing) {
    return { status: "rejected", error: "chapterNotFound" };
  }
  if (existing.status === "published") {
    return {
      status: "published",
      chapter_id: input.chapter_id,
    };
  }
  if (!canTransition(existing.status, "published")) {
    return {
      status: "invalidState",
      chapter_id: input.chapter_id,
      actual: existing.status,
    };
  }
  const merged: ChapterRecord = {
    ...existing,
    status: "published",
    published_at: new Date().toISOString(),
  };
  await e.write({
    collection: CHAPTER_COLLECTION,
    record: merged as unknown as Record<string, unknown>,
    rkey,
  });
  return {
    status: "published",
    chapter_id: input.chapter_id,
  };
}
