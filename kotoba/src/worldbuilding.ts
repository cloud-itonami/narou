import type { Etzhayyim } from "@etzhayyim/sdk";
import {
  characterDid,
  characterRkey,
  characterSlug,
  novelRkey,
  worldSettingDid,
  worldSettingRkey,
  worldSettingSlug,
  type CharacterRecord,
  type CharacterView,
  type CreateCharacterInput,
  type CreateCharacterOutput,
  type CreateWorldSettingInput,
  type CreateWorldSettingOutput,
  type NovelRecord,
  type WorldSettingRecord,
  type WorldSettingView,
} from "./types.js";

const WORLD_SETTING_COLLECTION = "com.etzhayyim.narou.worldSetting";
const CHARACTER_COLLECTION = "com.etzhayyim.narou.character";
const NOVEL_COLLECTION = "com.etzhayyim.narou.novel";

function isValidWorldId(id: string): boolean {
  return /^[a-z0-9-]{1,64}$/i.test(id);
}

function isValidCharacterId(id: string): boolean {
  return /^[a-z0-9-]{1,64}$/i.test(id);
}

export async function createWorldSetting(
  e: Etzhayyim,
  input: CreateWorldSettingInput
): Promise<CreateWorldSettingOutput> {
  if (!input.novel_id || !input.name || input.name.trim().length === 0) {
    return { status: "rejected", error: "missingRequiredFields" };
  }
  const worldIdBase = worldSettingSlug(input.name).slice(0, 32);
  const worldId = `${input.novel_id}-${worldIdBase}`;
  if (!isValidWorldId(worldId)) {
    return { status: "rejected", error: "invalidWorldId" };
  }
  const rkey = worldSettingRkey(worldId);
  const existing = await e
    .read<WorldSettingRecord>({
      collection: WORLD_SETTING_COLLECTION,
      rkey,
    })
    .catch(() => ({ records: [] }));
  if (existing.records[0]?.value) {
    return {
      status: "alreadyExists",
      id: worldId,
      world_uri: existing.records[0].uri,
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
  const did = worldSettingDid(worldId);
  const now = new Date().toISOString();
  const record: WorldSettingRecord = {
    did,
    novel_id: input.novel_id,
    name: input.name,
    description: input.description,
    created_at: now,
  };
  const receipt = await e.write({
    collection: WORLD_SETTING_COLLECTION,
    record: record as unknown as Record<string, unknown>,
    rkey,
  });
  return {
    status: "registered",
    id: worldId,
    world_uri: receipt.uri,
  };
}

export async function createCharacter(
  e: Etzhayyim,
  input: CreateCharacterInput
): Promise<CreateCharacterOutput> {
  if (!input.novel_id || !input.name || input.name.trim().length === 0) {
    return { status: "rejected", error: "missingRequiredFields" };
  }
  const characterIdBase = characterSlug(input.name).slice(0, 32);
  const characterId = `${input.novel_id}-${characterIdBase}`;
  if (!isValidCharacterId(characterId)) {
    return { status: "rejected", error: "invalidCharacterId" };
  }
  const rkey = characterRkey(characterId);
  const existing = await e
    .read<CharacterRecord>({
      collection: CHARACTER_COLLECTION,
      rkey,
    })
    .catch(() => ({ records: [] }));
  if (existing.records[0]?.value) {
    return {
      status: "alreadyExists",
      id: characterId,
      character_uri: existing.records[0].uri,
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
  const did = characterDid(characterId);
  const now = new Date().toISOString();
  const record: CharacterRecord = {
    did,
    novel_id: input.novel_id,
    name: input.name,
    role: input.role,
    description: input.description,
    created_at: now,
  };
  const receipt = await e.write({
    collection: CHARACTER_COLLECTION,
    record: record as unknown as Record<string, unknown>,
    rkey,
  });
  return {
    status: "registered",
    id: characterId,
    character_uri: receipt.uri,
  };
}
