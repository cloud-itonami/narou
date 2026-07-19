import type { Etzhayyim } from "@etzhayyim/sdk";

export const NAROU_DID_PREFIX = "did:web:narou.etzhayyim.com:" as const;

export type ChapterStatus =
  | "draft"
  | "in_review"
  | "published"
  | "archived";

export interface NovelRecord {
  did: string;
  title: string;
  description?: string;
  genre?: string;
  tags?: string[];
  status: string;
  chapter_count: number;
  created_at: string;
}

export interface NovelView extends NovelRecord {
  novel_uri: string;
}

export interface CreateNovelInput {
  title: string;
  novel_id?: string;
  description?: string;
  genre?: string;
  tags?: string;
  user_id?: string;
  org_id?: string;
}

export interface CreateNovelOutput {
  status: "registered" | "rejected" | "alreadyExists";
  id?: string;
  novel_uri?: string;
  error?: string;
}

export interface GetNovelInput {
  id: string;
}

export interface GetNovelOutput {
  novel?: NovelView;
  error?: string;
}

export interface ListNovelsInput {
  genre?: string;
  status?: string;
  user_id?: string;
  offset?: number;
  limit?: number;
}

export interface ListNovelsOutput {
  novels: NovelView[];
  total: number;
  offset: number;
  limit: number;
}

export interface SearchNovelsInput {
  q: string;
  genre?: string;
  tag?: string;
  offset?: number;
  limit?: number;
}

export interface SearchNovelsOutput {
  novels: NovelView[];
  total: number;
  offset: number;
  limit: number;
}

export interface ChapterRecord {
  did: string;
  novel_id: string;
  chapter_num?: number;
  title: string;
  content?: string;
  status: ChapterStatus;
  word_count?: number;
  published_at?: string;
  created_at: string;
}

export interface ChapterView extends ChapterRecord {
  chapter_uri: string;
}

export interface CreateChapterInput {
  novel_id: string;
  chapter_num?: number;
  title: string;
  content?: string;
  user_id?: string;
  org_id?: string;
}

export interface CreateChapterOutput {
  status: "registered" | "rejected" | "alreadyExists";
  id?: string;
  chapter_uri?: string;
  error?: string;
}

export interface GenerateChapterInput {
  chapter_id: string;
  prompt_hint?: string;
  word_count_target?: number;
}

export interface GenerateChapterOutput {
  chapter_id?: string;
  word_count?: number;
  status?: "completed" | "rejected";
  error?: string;
}

export interface PublishChapterInput {
  chapter_id: string;
  asset_manifest_uri?: string;
}

export interface PublishChapterOutput {
  chapter_id?: string;
  status: "published" | "rejected" | "invalidState";
  actual?: ChapterStatus;
  error?: string;
}

export interface GetChapterInput {
  id: string;
}

export interface GetChapterOutput {
  chapter?: ChapterView;
  error?: string;
}

export interface ListChaptersInput {
  novel_id: string;
  status?: ChapterStatus;
  offset?: number;
  limit?: number;
}

export interface ListChaptersOutput {
  chapters: ChapterView[];
  total: number;
  offset: number;
  limit: number;
}

export interface WorldSettingRecord {
  did: string;
  novel_id: string;
  name: string;
  description?: string;
  created_at: string;
}

export interface WorldSettingView extends WorldSettingRecord {
  world_uri: string;
}

export interface CreateWorldSettingInput {
  novel_id: string;
  name: string;
  description?: string;
  user_id?: string;
  org_id?: string;
}

export interface CreateWorldSettingOutput {
  status: "registered" | "rejected" | "alreadyExists";
  id?: string;
  world_uri?: string;
  error?: string;
}

export interface CharacterRecord {
  did: string;
  novel_id: string;
  name: string;
  role?: string;
  description?: string;
  created_at: string;
}

export interface CharacterView extends CharacterRecord {
  character_uri: string;
}

export interface CreateCharacterInput {
  novel_id: string;
  name: string;
  role?: string;
  description?: string;
  user_id?: string;
  org_id?: string;
}

export interface CreateCharacterOutput {
  status: "registered" | "rejected" | "alreadyExists";
  id?: string;
  character_uri?: string;
  error?: string;
}

export function novelSlug(novelId: string): string {
  return novelId.toLowerCase().replace(/[^a-z0-9]/g, "-");
}

export function novelDid(novelId: string): string {
  return `${NAROU_DID_PREFIX}novel:${novelSlug(novelId)}`;
}

export function novelRkey(novelId: string): string {
  return `novel-${novelSlug(novelId)}`;
}

export function chapterSlug(chapterId: string): string {
  return chapterId.toLowerCase().replace(/[^a-z0-9]/g, "-");
}

export function chapterDid(chapterId: string): string {
  return `${NAROU_DID_PREFIX}chapter:${chapterSlug(chapterId)}`;
}

export function chapterRkey(chapterId: string): string {
  return `chapter-${chapterSlug(chapterId)}`;
}

export function worldSettingSlug(worldId: string): string {
  return worldId.toLowerCase().replace(/[^a-z0-9]/g, "-");
}

export function worldSettingDid(worldId: string): string {
  return `${NAROU_DID_PREFIX}world:${worldSettingSlug(worldId)}`;
}

export function worldSettingRkey(worldId: string): string {
  return `world-${worldSettingSlug(worldId)}`;
}

export function characterSlug(characterId: string): string {
  return characterId.toLowerCase().replace(/[^a-z0-9]/g, "-");
}

export function characterDid(characterId: string): string {
  return `${NAROU_DID_PREFIX}character:${characterSlug(characterId)}`;
}

export function characterRkey(characterId: string): string {
  return `character-${characterSlug(characterId)}`;
}
