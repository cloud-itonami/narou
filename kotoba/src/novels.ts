import type { Etzhayyim } from "@etzhayyim/sdk";
import {
  novelDid,
  novelRkey,
  novelSlug,
  type CreateNovelInput,
  type CreateNovelOutput,
  type GetNovelInput,
  type GetNovelOutput,
  type ListNovelsInput,
  type ListNovelsOutput,
  type NovelRecord,
  type NovelView,
  type SearchNovelsInput,
  type SearchNovelsOutput,
} from "./types.js";

const NOVEL_COLLECTION = "com.etzhayyim.narou.novel";

function isNovelId(id: string): boolean {
  return /^[a-z0-9-]{1,32}$/i.test(id);
}

export async function createNovel(
  e: Etzhayyim,
  input: CreateNovelInput
): Promise<CreateNovelOutput> {
  if (!input.title || input.title.trim().length === 0) {
    return { status: "rejected", error: "missingTitle" };
  }
  const novelId = input.novel_id
    ? input.novel_id
    : novelSlug(input.title).slice(0, 32);
  if (!isNovelId(novelId)) {
    return { status: "rejected", error: "invalidNovelId" };
  }
  const rkey = novelRkey(novelId);
  const existing = await e
    .read<NovelRecord>({ collection: NOVEL_COLLECTION, rkey })
    .catch(() => ({ records: [] }));
  if (existing.records[0]?.value) {
    return {
      status: "alreadyExists",
      id: novelId,
      novel_uri: existing.records[0].uri,
    };
  }
  const did = novelDid(novelId);
  const now = new Date().toISOString();
  const tags = input.tags ? input.tags.split(",").map((t) => t.trim()) : [];
  const record: NovelRecord = {
    did,
    title: input.title,
    description: input.description,
    genre: input.genre,
    tags,
    status: "active",
    chapter_count: 0,
    created_at: now,
  };
  const receipt = await e.write({
    collection: NOVEL_COLLECTION,
    record: record as unknown as Record<string, unknown>,
    rkey,
  });
  return {
    status: "registered",
    id: novelId,
    novel_uri: receipt.uri,
  };
}

export async function getNovel(
  e: Etzhayyim,
  input: GetNovelInput
): Promise<GetNovelOutput> {
  if (!input.id) return { error: "missingNovelId" };
  const resp = await e
    .read<NovelRecord>({
      collection: NOVEL_COLLECTION,
      rkey: novelRkey(input.id),
    })
    .catch(() => ({ records: [] }));
  const r = resp.records[0];
  if (!r) return { error: "notFound" };
  const view: NovelView = { ...r.value, novel_uri: r.uri };
  return { novel: view };
}

export async function listNovels(
  e: Etzhayyim,
  input: ListNovelsInput = {}
): Promise<ListNovelsOutput> {
  const offset = input.offset ?? 0;
  const limit = Math.min(input.limit ?? 50, 100);
  const resp = await e.read<NovelRecord>({
    collection: NOVEL_COLLECTION,
    limit: limit + offset,
  });
  const filtered = resp.records
    .filter((r) => {
      const v = r.value;
      if (input.genre && v.genre !== input.genre) return false;
      if (input.status && v.status !== input.status) return false;
      return true;
    })
    .slice(offset, offset + limit);
  const items: NovelView[] = filtered.map((r) => ({
    ...r.value,
    novel_uri: r.uri,
  }));
  return {
    novels: items,
    total: filtered.length,
    offset,
    limit,
  };
}

export async function searchNovels(
  e: Etzhayyim,
  input: SearchNovelsInput
): Promise<SearchNovelsOutput> {
  if (!input.q || input.q.trim().length === 0) {
    return { novels: [], total: 0, offset: 0, limit: 0 };
  }
  const offset = input.offset ?? 0;
  const limit = Math.min(input.limit ?? 50, 100);
  const q = input.q.toLowerCase();
  const resp = await e.read<NovelRecord>({
    collection: NOVEL_COLLECTION,
    limit: limit + offset,
  });
  const filtered = resp.records
    .filter((r) => {
      const v = r.value;
      const titleMatch =
        v.title.toLowerCase().includes(q) ||
        (v.description && v.description.toLowerCase().includes(q));
      if (!titleMatch) return false;
      if (input.genre && v.genre !== input.genre) return false;
      if (input.tag && (!v.tags || !v.tags.includes(input.tag)))
        return false;
      return true;
    })
    .slice(offset, offset + limit);
  const items: NovelView[] = filtered.map((r) => ({
    ...r.value,
    novel_uri: r.uri,
  }));
  return {
    novels: items,
    total: filtered.length,
    offset,
    limit,
  };
}
