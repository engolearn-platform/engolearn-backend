#!/usr/bin/env node
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const BACKEND_ROOT = path.resolve(SCRIPT_DIR, "..", "..");
const REPO_ROOT = path.resolve(BACKEND_ROOT, "..");
const SOURCE_ROOT = path.join(REPO_ROOT, "data", "bank-320", "package-placement-320-v3");
const SOURCE_IMPORT = path.join(SOURCE_ROOT, "placement-320-compact-v3.import.json");
const SOURCE_SCHEMA = path.join(SOURCE_ROOT, "placement-bank-v3.schema.json");
const SOURCE_VALIDATION = path.join(SOURCE_ROOT, "V3-VALIDATION-AND-TIMING.json");
const SOURCE_README = path.join(SOURCE_ROOT, "README-COMPLETE-V3.md");
const OUT_DIR = path.join(SCRIPT_DIR, "data", "seed");
const OUT_SCHEMA = path.join(OUT_DIR, "placement_questions.mongo.schema.json");
const OUT_QUESTIONS = path.join(OUT_DIR, "placement_questions.mongo.json");
const OUT_AUDIO = path.join(OUT_DIR, "placement_audio_manifest.json");
const OUT_MANIFEST = path.join(OUT_DIR, "placement_seed_manifest.json");

const EXPECTED = {
  total: 320,
  reading: 224,
  listening: 96,
  levels: {
    A1: { reading: 64, listening: 16 },
    A2: { reading: 64, listening: 16 },
    B1: { reading: 48, listening: 32 },
    B2: { reading: 48, listening: 32 },
  },
  status: "DRAFT",
  audioFiles: 96,
  audioBytes: 48428354,
};

const QUESTION_KEYS = new Set([
  "_id",
  "code",
  "skill",
  "level",
  "prompt",
  "passage",
  "options",
  "correctOptionId",
  "explanation",
  "audioUrl",
  "transcript",
  "status",
  "provenance",
]);

const PROVENANCE_KEYS = new Set([
  "standalone",
  "adaptationType",
  "author",
  "authoredOn",
  "revision",
  "previousDraftQuestionId",
  "sourceUrl",
  "sourceLevel",
  "levelStatus",
  "humanReviewStatus",
  "verifiedBy",
  "verifiedAt",
  "contentGroupId",
  "title",
  "answerEvidence",
  "answerVerification",
  "levelRationale",
  "distractorRationales",
  "timingPolicy",
  "audioGroupId",
  "audioType",
  "audioFile",
  "audioSha256",
  "audioDurationSeconds",
  "audioClipStartSeconds",
  "audioClipEndSeconds",
  "answerEvidenceStartSeconds",
  "answerEvidenceEndSeconds",
  "transcriptScope",
  "audioReviewStatus",
  "semanticFamilyId",
  "assemblyNote",
]);

const AUDIO_PROVENANCE_KEYS = [
  "audioGroupId",
  "audioType",
  "audioFile",
  "audioSha256",
  "audioDurationSeconds",
  "audioClipStartSeconds",
  "audioClipEndSeconds",
  "answerEvidenceStartSeconds",
  "answerEvidenceEndSeconds",
  "transcriptScope",
  "audioReviewStatus",
];

const B2_PROVENANCE_KEYS = ["semanticFamilyId", "assemblyNote"];
const QUESTION_ID_PATTERN = /^PLT-Q-(A1|A2|B1|B2)-[RL]-[0-9]{4}-V3$/;
const AUDIO_GROUP_PATTERN = /^PLT-(A1|A2|B1|B2)-L-G[0-9]{2}-V3$/;
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const OPTION_IDS = ["a", "b", "c", "d"];
const OPTION_LABELS = ["A", "B", "C", "D"];

function fail(message) {
  throw new Error(message);
}

function isObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function isNonEmptyString(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function isFiniteNumber(value) {
  return typeof value === "number" && Number.isFinite(value);
}

function hasOwn(object, key) {
  return Object.prototype.hasOwnProperty.call(object, key);
}

function rel(filePath) {
  return path.relative(REPO_ROOT, filePath).split(path.sep).join("/");
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function readText(filePath) {
  if (!fs.existsSync(filePath)) fail(`Missing file: ${filePath}`);
  return fs.readFileSync(filePath, "utf8");
}

function readJson(filePath) {
  try {
    return JSON.parse(readText(filePath));
  } catch (error) {
    fail(`Cannot parse JSON ${filePath}: ${error.message}`);
  }
}

function fileInfo(filePath) {
  const buffer = fs.readFileSync(filePath);
  return {
    path: rel(filePath),
    bytes: buffer.length,
    sha256: sha256(buffer),
  };
}

function stringify(value) {
  return `${JSON.stringify(value, null, 2)}\n`;
}

function assertSameKeys(object, expectedKeys, label) {
  assert.ok(isObject(object), `${label} must be an object`);
  const actual = Object.keys(object).sort();
  const expected = [...expectedKeys].sort();
  assert.deepStrictEqual(actual, expected, `${label} has unexpected or missing keys`);
}

function assertNoUndefined(value, label) {
  if (value === undefined) fail(`${label} is undefined`);
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoUndefined(item, `${label}[${index}]`));
    return;
  }
  if (isObject(value)) {
    for (const [key, item] of Object.entries(value)) {
      assertNoUndefined(item, `${label}.${key}`);
    }
  }
}

function assertNoReplacementCharacter(value, label) {
  if (typeof value === "string") {
    assert.ok(!value.includes("\uFFFD"), `${label} contains U+FFFD replacement character`);
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoReplacementCharacter(item, `${label}[${index}]`));
    return;
  }
  if (isObject(value)) {
    for (const [key, item] of Object.entries(value)) {
      assertNoReplacementCharacter(item, `${label}.${key}`);
    }
  }
}

function validateSource(source) {
  assertSameKeys(source, ["metadata", "collections"], "source");
  assertSameKeys(source.collections, ["placement_questions"], "source.collections");

  const metadata = source.metadata;
  assert.equal(metadata.name, "placement-320-compact-v3", "metadata.name");
  assert.equal(metadata.version, 3, "metadata.version");
  assert.equal(metadata.questionCount, EXPECTED.total, "metadata.questionCount");
  assert.equal(metadata.readingCount, EXPECTED.reading, "metadata.readingCount");
  assert.equal(metadata.listeningCount, EXPECTED.listening, "metadata.listeningCount");
  assert.equal(metadata.contentStatus, "DRAFT", "metadata.contentStatus");
  assert.equal(metadata.humanReviewStatus, "PENDING", "metadata.humanReviewStatus");
  assert.equal(metadata.cefrCalibration, "NOT_ESTABLISHED", "metadata.cefrCalibration");
  assert.equal(metadata.audioHostingStatus, "NOT_CONFIGURED", "metadata.audioHostingStatus");

  const questions = source.collections.placement_questions;
  assert.ok(Array.isArray(questions), "placement_questions must be an array");
  assert.equal(questions.length, EXPECTED.total, "question count");

  const ids = new Set();
  const contentGroupIds = new Set();
  const readingPassages = new Set();
  const listeningTranscripts = new Set();
  const audioHashes = new Set();
  const counts = { READING: 0, LISTENING: 0 };
  const levels = {};
  const answerDistribution = {};

  for (const [index, question] of questions.entries()) {
    const label = `question[${index}] ${question?._id ?? "unknown"}`;
    assertSameKeys(question, QUESTION_KEYS, label);
    assert.match(question._id, QUESTION_ID_PATTERN, `${label}._id`);
    assert.equal(question.code, question._id, `${label}.code`);
    assert.ok(ids.add(question._id), `${label} duplicate _id`);
    assert.ok(["READING", "LISTENING"].includes(question.skill), `${label}.skill`);
    assert.ok(["A1", "A2", "B1", "B2"].includes(question.level), `${label}.level`);
    assert.ok(isNonEmptyString(question.prompt), `${label}.prompt`);
    assert.ok(isNonEmptyString(question.explanation), `${label}.explanation`);
    assert.equal(question.status, "DRAFT", `${label}.status`);
    assert.equal(question.audioUrl, null, `${label}.audioUrl`);
    assert.ok(Array.isArray(question.options), `${label}.options`);
    assert.equal(question.options.length, 4, `${label}.options.length`);

    const optionIds = new Set();
    const optionTexts = new Set();
    for (const [optionIndex, option] of question.options.entries()) {
      assertSameKeys(option, ["_id", "text"], `${label}.options[${optionIndex}]`);
      assert.ok(OPTION_IDS.includes(option._id), `${label}.options[${optionIndex}]._id`);
      assert.ok(isNonEmptyString(option.text), `${label}.options[${optionIndex}].text`);
      assert.ok(optionIds.add(option._id), `${label} duplicate option id ${option._id}`);
      assert.ok(optionTexts.add(option.text), `${label} duplicate option text ${option.text}`);
    }
    assert.deepStrictEqual([...optionIds].sort(), OPTION_IDS, `${label} option ids`);
    assert.ok(optionIds.has(question.correctOptionId), `${label}.correctOptionId`);

    assert.ok(isObject(question.provenance), `${label}.provenance`);
    for (const key of Object.keys(question.provenance)) {
      assert.ok(PROVENANCE_KEYS.has(key), `${label}.provenance has unknown key ${key}`);
    }
    for (const key of [
      "standalone",
      "adaptationType",
      "author",
      "authoredOn",
      "revision",
      "previousDraftQuestionId",
      "sourceUrl",
      "sourceLevel",
      "levelStatus",
      "humanReviewStatus",
      "verifiedBy",
      "verifiedAt",
      "contentGroupId",
      "title",
      "answerEvidence",
      "answerVerification",
      "levelRationale",
      "distractorRationales",
      "timingPolicy",
    ]) {
      assert.ok(hasOwn(question.provenance, key), `${label}.provenance.${key} is required`);
    }
    assert.equal(question.provenance.standalone, true, `${label}.provenance.standalone`);
    assert.equal(question.provenance.adaptationType, "ORIGINAL_AUTHORED", `${label}.provenance.adaptationType`);
    assert.ok(isNonEmptyString(question.provenance.author), `${label}.provenance.author`);
    assert.match(question.provenance.authoredOn, /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/, `${label}.provenance.authoredOn`);
    assert.ok(Number.isInteger(question.provenance.revision) && question.provenance.revision >= 1,
      `${label}.provenance.revision`);
    assert.equal(question.provenance.sourceUrl, null, `${label}.provenance.sourceUrl`);
    assert.equal(question.provenance.sourceLevel, null, `${label}.provenance.sourceLevel`);
    assert.equal(question.provenance.levelStatus, "PROVISIONAL_AUTHORED", `${label}.provenance.levelStatus`);
    assert.equal(question.provenance.humanReviewStatus, "PENDING", `${label}.provenance.humanReviewStatus`);
    assert.equal(question.provenance.verifiedBy, null, `${label}.provenance.verifiedBy`);
    assert.equal(question.provenance.verifiedAt, null, `${label}.provenance.verifiedAt`);
    assert.ok(isNonEmptyString(question.provenance.contentGroupId), `${label}.provenance.contentGroupId`);
    assert.equal(question.provenance.contentGroupId, question._id, `${label}.provenance.contentGroupId`);
    assert.ok(contentGroupIds.add(question.provenance.contentGroupId), `${label} duplicate contentGroupId`);
    assert.ok(isNonEmptyString(question.provenance.title), `${label}.provenance.title`);
    assert.ok(isNonEmptyString(question.provenance.answerEvidence), `${label}.provenance.answerEvidence`);
    assert.ok(isNonEmptyString(question.provenance.answerVerification), `${label}.provenance.answerVerification`);
    assert.ok(isNonEmptyString(question.provenance.levelRationale), `${label}.provenance.levelRationale`);

    assert.ok(Array.isArray(question.provenance.distractorRationales), `${label}.provenance.distractorRationales`);
    assert.equal(question.provenance.distractorRationales.length, 4, `${label}.provenance.distractorRationales.length`);
    const distractorIds = new Set();
    for (const [distractorIndex, distractor] of question.provenance.distractorRationales.entries()) {
      assertSameKeys(distractor, ["optionId", "reason"], `${label}.provenance.distractorRationales[${distractorIndex}]`);
      assert.ok(OPTION_IDS.includes(distractor.optionId), `${label}.provenance.distractorRationales[${distractorIndex}].optionId`);
      assert.ok(isNonEmptyString(distractor.reason), `${label}.provenance.distractorRationales[${distractorIndex}].reason`);
      assert.ok(distractorIds.add(distractor.optionId), `${label} duplicate distractor option ${distractor.optionId}`);
    }
    assert.deepStrictEqual([...distractorIds].sort(), OPTION_IDS, `${label} distractor option ids`);

    assertSameKeys(question.provenance.timingPolicy,
      ["readingWordsPerMinute", "playsPerAudio", "visibleWordLimit", "humanTimingReview"],
      `${label}.provenance.timingPolicy`);
    assert.equal(question.provenance.timingPolicy.readingWordsPerMinute, 100,
      `${label}.provenance.timingPolicy.readingWordsPerMinute`);
    assert.equal(question.provenance.timingPolicy.playsPerAudio, 2,
      `${label}.provenance.timingPolicy.playsPerAudio`);
    assert.ok(Number.isInteger(question.provenance.timingPolicy.visibleWordLimit)
      && question.provenance.timingPolicy.visibleWordLimit > 0,
    `${label}.provenance.timingPolicy.visibleWordLimit`);
    assert.equal(question.provenance.timingPolicy.humanTimingReview, "PENDING",
      `${label}.provenance.timingPolicy.humanTimingReview`);

    if (question.skill === "READING") {
      assert.ok(isNonEmptyString(question.passage), `${label}.passage`);
      assert.equal(question.transcript, null, `${label}.transcript`);
      for (const key of AUDIO_PROVENANCE_KEYS) {
        assert.ok(!hasOwn(question.provenance, key), `${label}.provenance.${key} must be absent for Reading`);
      }
      assert.ok(readingPassages.add(question.passage), `${label} duplicate passage`);
    } else {
      assert.equal(question.passage, null, `${label}.passage`);
      assert.ok(isNonEmptyString(question.transcript), `${label}.transcript`);
      for (const key of AUDIO_PROVENANCE_KEYS) {
        assert.ok(hasOwn(question.provenance, key), `${label}.provenance.${key} is required for Listening`);
      }
      assert.match(question.provenance.audioGroupId, AUDIO_GROUP_PATTERN, `${label}.provenance.audioGroupId`);
      assert.equal(question.provenance.audioType, "SYNTHETIC_TTS", `${label}.provenance.audioType`);
      assert.match(question.provenance.audioFile, /^audio\/[A-Za-z0-9._-]+\.wav$/, `${label}.provenance.audioFile`);
      assert.match(question.provenance.audioSha256, SHA256_PATTERN, `${label}.provenance.audioSha256`);
      assert.ok(isFiniteNumber(question.provenance.audioDurationSeconds)
        && question.provenance.audioDurationSeconds > 0, `${label}.provenance.audioDurationSeconds`);
      assert.ok(isFiniteNumber(question.provenance.audioClipStartSeconds)
        && question.provenance.audioClipStartSeconds >= 0, `${label}.provenance.audioClipStartSeconds`);
      assert.ok(isFiniteNumber(question.provenance.audioClipEndSeconds)
        && question.provenance.audioClipEndSeconds > question.provenance.audioClipStartSeconds,
      `${label}.provenance.audioClipEndSeconds`);
      assert.ok(question.provenance.audioClipEndSeconds
        <= question.provenance.audioDurationSeconds + 0.05, `${label}.provenance.audioClipEndSeconds > duration`);
      for (const key of ["answerEvidenceStartSeconds", "answerEvidenceEndSeconds"]) {
        assert.ok(question.provenance[key] === null || isFiniteNumber(question.provenance[key]),
          `${label}.provenance.${key}`);
      }
      assert.ok(isNonEmptyString(question.provenance.transcriptScope), `${label}.provenance.transcriptScope`);
      assert.equal(question.provenance.audioReviewStatus, "PENDING", `${label}.provenance.audioReviewStatus`);
      assert.ok(listeningTranscripts.add(question.transcript), `${label} duplicate transcript`);
      assert.ok(audioHashes.add(question.provenance.audioSha256), `${label} duplicate audio hash`);
    }

    if (question.level === "B2") {
      for (const key of B2_PROVENANCE_KEYS) {
        assert.ok(hasOwn(question.provenance, key), `${label}.provenance.${key} is required for B2`);
      }
      assert.ok(isNonEmptyString(question.provenance.semanticFamilyId), `${label}.provenance.semanticFamilyId`);
      assert.ok(isNonEmptyString(question.provenance.assemblyNote), `${label}.provenance.assemblyNote`);
    } else {
      for (const key of B2_PROVENANCE_KEYS) {
        assert.ok(!hasOwn(question.provenance, key), `${label}.provenance.${key} must be absent for non-B2`);
      }
    }

    assertNoReplacementCharacter(question, label);

    counts[question.skill]++;
    levels[question.level] ??= { total: 0, reading: 0, listening: 0, answers: { a: 0, b: 0, c: 0, d: 0 } };
    levels[question.level].total++;
    levels[question.level][question.skill === "READING" ? "reading" : "listening"]++;
    levels[question.level].answers[question.correctOptionId]++;
    answerDistribution[question.level] ??= { a: 0, b: 0, c: 0, d: 0 };
    answerDistribution[question.level][question.correctOptionId]++;
  }

  assert.equal(counts.READING, EXPECTED.reading, "reading count");
  assert.equal(counts.LISTENING, EXPECTED.listening, "listening count");
  assert.equal(ids.size, EXPECTED.total, "unique question ids");
  assert.equal(contentGroupIds.size, EXPECTED.total, "unique contentGroupIds");
  assert.equal(readingPassages.size, EXPECTED.reading, "unique Reading passages");
  assert.equal(listeningTranscripts.size, EXPECTED.listening, "unique Listening transcripts");
  assert.equal(audioHashes.size, EXPECTED.listening, "unique audio hashes");

  for (const [level, expected] of Object.entries(EXPECTED.levels)) {
    assert.ok(levels[level], `missing level ${level}`);
    assert.equal(levels[level].total, expected.reading + expected.listening, `${level} total`);
    assert.equal(levels[level].reading, expected.reading, `${level} reading`);
    assert.equal(levels[level].listening, expected.listening, `${level} listening`);
    assert.deepStrictEqual(levels[level].answers, { a: 20, b: 20, c: 20, d: 20 },
      `${level} correct answer distribution`);
  }

  const promptGroups = new Map();
  for (const question of questions) {
    const key = question.prompt.trim();
    promptGroups.set(key, (promptGroups.get(key) ?? 0) + 1);
  }
  const duplicatePromptGroups = [...promptGroups.values()].filter((count) => count > 1);
  const duplicatePromptQuestions = duplicatePromptGroups.reduce((sum, count) => sum + count, 0);

  const b2 = questions.filter((question) => question.level === "B2");
  const familyCounts = new Map();
  for (const question of b2) {
    const family = question.provenance.semanticFamilyId;
    familyCounts.set(family, (familyCounts.get(family) ?? 0) + 1);
  }
  const sharedFamilies = [...familyCounts.values()].filter((count) => count > 1);
  const sharedPairs = sharedFamilies.reduce((sum, count) => sum + count / 2, 0);

  return {
    questions,
    metadata,
    counts: {
      total: questions.length,
      reading: counts.READING,
      listening: counts.LISTENING,
      levels,
    },
    integrity: {
      uniqueQuestionIds: ids.size,
      uniqueContentGroupIds: contentGroupIds.size,
      uniqueReadingPassages: readingPassages.size,
      uniqueListeningTranscripts: listeningTranscripts.size,
      uniqueAudioHashes: audioHashes.size,
      duplicatePromptGroups: duplicatePromptGroups.length,
      duplicatePromptQuestions,
      b2Questions: b2.length,
      b2UniqueSemanticFamilies: familyCounts.size,
      b2SharedFamilies: sharedFamilies.length,
      b2SharedPairs: sharedPairs,
    },
  };
}

function normalizeProvenance(provenance) {
  const normalized = {
    standalone: provenance.standalone,
    adaptation_type: provenance.adaptationType,
    author: provenance.author,
    authored_on: provenance.authoredOn,
    revision: provenance.revision,
    previous_draft_question_id: provenance.previousDraftQuestionId,
    source_url: provenance.sourceUrl,
    source_level: provenance.sourceLevel,
    level_status: provenance.levelStatus,
    human_review_status: provenance.humanReviewStatus,
    verified_by: provenance.verifiedBy,
    verified_at: provenance.verifiedAt,
    content_group_id: provenance.contentGroupId,
    title: provenance.title,
    answer_evidence: provenance.answerEvidence,
    answer_verification: provenance.answerVerification,
    level_rationale: provenance.levelRationale,
    distractor_rationales: provenance.distractorRationales.map((item) => ({
      option_id: item.optionId,
      reason: item.reason,
    })),
    timing_policy: {
      reading_words_per_minute: provenance.timingPolicy.readingWordsPerMinute,
      plays_per_audio: provenance.timingPolicy.playsPerAudio,
      visible_word_limit: provenance.timingPolicy.visibleWordLimit,
      human_timing_review: provenance.timingPolicy.humanTimingReview,
    },
  };

  if (hasOwn(provenance, "audioGroupId")) {
    normalized.audio_group_id = provenance.audioGroupId;
    normalized.audio_type = provenance.audioType;
    normalized.audio_file = provenance.audioFile;
    normalized.audio_sha256 = provenance.audioSha256;
    normalized.audio_duration_seconds = provenance.audioDurationSeconds;
    normalized.audio_clip_start_seconds = provenance.audioClipStartSeconds;
    normalized.audio_clip_end_seconds = provenance.audioClipEndSeconds;
    normalized.answer_evidence_start_seconds = provenance.answerEvidenceStartSeconds;
    normalized.answer_evidence_end_seconds = provenance.answerEvidenceEndSeconds;
    normalized.transcript_scope = provenance.transcriptScope;
    normalized.audio_review_status = provenance.audioReviewStatus;
  }

  if (hasOwn(provenance, "semanticFamilyId")) {
    normalized.semantic_family_id = provenance.semanticFamilyId;
    normalized.assembly_note = provenance.assemblyNote;
  }

  return normalized;
}

function denormalizeProvenance(provenance) {
  const denormalized = {
    standalone: provenance.standalone,
    adaptationType: provenance.adaptation_type,
    author: provenance.author,
    authoredOn: provenance.authored_on,
    revision: provenance.revision,
    previousDraftQuestionId: provenance.previous_draft_question_id,
    sourceUrl: provenance.source_url,
    sourceLevel: provenance.source_level,
    levelStatus: provenance.level_status,
    humanReviewStatus: provenance.human_review_status,
    verifiedBy: provenance.verified_by,
    verifiedAt: provenance.verified_at,
    contentGroupId: provenance.content_group_id,
    title: provenance.title,
    answerEvidence: provenance.answer_evidence,
    answerVerification: provenance.answer_verification,
    levelRationale: provenance.level_rationale,
    distractorRationales: provenance.distractor_rationales.map((item) => ({
      optionId: item.option_id,
      reason: item.reason,
    })),
    timingPolicy: {
      readingWordsPerMinute: provenance.timing_policy.reading_words_per_minute,
      playsPerAudio: provenance.timing_policy.plays_per_audio,
      visibleWordLimit: provenance.timing_policy.visible_word_limit,
      humanTimingReview: provenance.timing_policy.human_timing_review,
    },
  };

  if (hasOwn(provenance, "audio_group_id")) {
    denormalized.audioGroupId = provenance.audio_group_id;
    denormalized.audioType = provenance.audio_type;
    denormalized.audioFile = provenance.audio_file;
    denormalized.audioSha256 = provenance.audio_sha256;
    denormalized.audioDurationSeconds = provenance.audio_duration_seconds;
    denormalized.audioClipStartSeconds = provenance.audio_clip_start_seconds;
    denormalized.audioClipEndSeconds = provenance.audio_clip_end_seconds;
    denormalized.answerEvidenceStartSeconds = provenance.answer_evidence_start_seconds;
    denormalized.answerEvidenceEndSeconds = provenance.answer_evidence_end_seconds;
    denormalized.transcriptScope = provenance.transcript_scope;
    denormalized.audioReviewStatus = provenance.audio_review_status;
  }

  if (hasOwn(provenance, "semantic_family_id")) {
    denormalized.semanticFamilyId = provenance.semantic_family_id;
    denormalized.assemblyNote = provenance.assembly_note;
  }

  return denormalized;
}

function toMongoQuestion(question) {
  return {
    _id: question._id,
    code: question.code,
    skill: question.skill,
    level: question.level,
    prompt: question.prompt,
    passage: question.passage,
    options: question.options.map((option) => ({
      id: option._id,
      label: option._id.toUpperCase(),
      text: option.text,
    })),
    correct_option_id: question.correctOptionId,
    explanation: question.explanation,
    audio_url: question.audioUrl,
    transcript: question.transcript,
    status: question.status,
    provenance: normalizeProvenance(question.provenance),
  };
}

function parseWavDuration(buffer, filePath) {
  if (buffer.length < 12 || buffer.toString("ascii", 0, 4) !== "RIFF"
    || buffer.toString("ascii", 8, 12) !== "WAVE") {
    fail(`${filePath} is not a RIFF/WAVE file`);
  }

  let offset = 12;
  let byteRate = null;
  let dataSize = null;
  while (offset + 8 <= buffer.length) {
    const chunkId = buffer.toString("ascii", offset, offset + 4);
    const chunkSize = buffer.readUInt32LE(offset + 4);
    const body = offset + 8;
    if (body + chunkSize > buffer.length) {
      fail(`${filePath} has truncated ${chunkId} chunk`);
    }
    if (chunkId === "fmt ") {
      if (chunkSize < 16) fail(`${filePath} has invalid fmt chunk`);
      const audioFormat = buffer.readUInt16LE(body);
      const channels = buffer.readUInt16LE(body + 2);
      const sampleRate = buffer.readUInt32LE(body + 4);
      byteRate = buffer.readUInt32LE(body + 8);
      const blockAlign = buffer.readUInt16LE(body + 12);
      const bitsPerSample = buffer.readUInt16LE(body + 14);
      if (audioFormat !== 1 || channels < 1 || sampleRate < 1 || byteRate < 1
        || blockAlign < 1 || bitsPerSample < 1) {
        fail(`${filePath} is not a valid PCM WAV file`);
      }
    }
    if (chunkId === "data") dataSize = chunkSize;
    offset = body + chunkSize + (chunkSize % 2);
  }

  if (byteRate === null || dataSize === null) {
    fail(`${filePath} is missing fmt/data chunks`);
  }
  return dataSize / byteRate;
}

function loadAudio(questions) {
  const batchesRoot = path.join(SOURCE_ROOT, "batches");
  const batchDirectories = fs.readdirSync(batchesRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
  assert.deepStrictEqual(batchDirectories, ["A1-compact-v3", "A2-compact-v3", "B1-compact-v3", "B2-compact-v3"],
    "audio batch directories");

  const listeningById = new Map(
    questions.filter((question) => question.skill === "LISTENING").map((question) => [question._id, question]),
  );
  assert.equal(listeningById.size, EXPECTED.listening, "listening question map");

  const entries = [];
  const audioGroupIds = new Set();
  const filenames = new Set();
  const sha256Values = new Set();
  const coveredQuestionIds = new Set();
  const manifestInfos = [];

  for (const batchDirectory of batchDirectories) {
    const level = batchDirectory.split("-")[0];
    const batchRoot = path.join(batchesRoot, batchDirectory);
    const manifestPath = path.join(batchRoot, "audio-manifest.json");
    const manifest = readJson(manifestPath);
    assert.ok(isObject(manifest), `${rel(manifestPath)} must be an object`);
    assert.ok(Array.isArray(manifest.audio), `${rel(manifestPath)}.audio must be an array`);
    manifestInfos.push(fileInfo(manifestPath));

    for (const [index, item] of manifest.audio.entries()) {
      const label = `${rel(manifestPath)}.audio[${index}]`;
      assert.ok(isObject(item), `${label} must be an object`);
      assert.equal(item.level, level, `${label}.level`);
      assert.ok(Array.isArray(item.questionIds), `${label}.questionIds`);
      assert.equal(item.questionIds.length, 1, `${label}.questionIds.length`);
      const questionId = item.questionIds[0];
      const question = listeningById.get(questionId);
      assert.ok(question, `${label} references unknown or non-Listening question ${questionId}`);
      assert.ok(!coveredQuestionIds.has(questionId), `${label} duplicates question ${questionId}`);
      coveredQuestionIds.add(questionId);

      assert.match(item.audioGroupId, AUDIO_GROUP_PATTERN, `${label}.audioGroupId`);
      assert.ok(audioGroupIds.add(item.audioGroupId), `${label} duplicate audioGroupId ${item.audioGroupId}`);
      assert.equal(item.audioType, "SYNTHETIC_TTS", `${label}.audioType`);
      assert.ok(isNonEmptyString(item.transcript), `${label}.transcript`);
      assert.equal(item.transcript, question.transcript, `${label}.transcript mismatch`);
      assert.equal(item.audioUrl, null, `${label}.audioUrl`);
      assert.equal(item.hostingStatus, "NOT_CONFIGURED", `${label}.hostingStatus`);
      assert.equal(item.humanListeningReview, "PENDING", `${label}.humanListeningReview`);
      assert.equal(item.alignmentStatus, "NOT_TIME_ALIGNED", `${label}.alignmentStatus`);
      assert.ok(isNonEmptyString(item.voice), `${label}.voice`);
      assert.ok(isNonEmptyString(item.locale), `${label}.locale`);
      assert.ok(isFiniteNumber(item.rate), `${label}.rate`);

      assert.ok(isObject(item.asset), `${label}.asset`);
      assert.ok(isNonEmptyString(item.asset.filename), `${label}.asset.filename`);
      assert.ok(filenames.add(item.asset.filename), `${label} duplicate filename ${item.asset.filename}`);
      assert.ok(Number.isInteger(item.asset.bytes) && item.asset.bytes > 0, `${label}.asset.bytes`);
      assert.match(item.asset.sha256, SHA256_PATTERN, `${label}.asset.sha256`);
      assert.ok(sha256Values.add(item.asset.sha256), `${label} duplicate sha256 ${item.asset.sha256}`);
      assert.ok(isFiniteNumber(item.asset.durationSeconds) && item.asset.durationSeconds > 0,
        `${label}.asset.durationSeconds`);

      assert.equal(question.provenance.audioGroupId, item.audioGroupId, `${questionId} audioGroupId`);
      assert.equal(question.provenance.audioType, item.audioType, `${questionId} audioType`);
      assert.equal(question.provenance.audioSha256, item.asset.sha256, `${questionId} audioSha256`);
      assert.equal(question.provenance.audioDurationSeconds, item.asset.durationSeconds,
        `${questionId} audioDurationSeconds`);
      assert.equal(question.provenance.audioFile, `audio/${item.asset.filename}`, `${questionId} audioFile`);

      const filePath = path.join(batchRoot, "audio", item.asset.filename);
      if (!fs.existsSync(filePath)) fail(`Missing WAV: ${filePath}`);
      const buffer = fs.readFileSync(filePath);
      assert.equal(buffer.length, item.asset.bytes, `${filePath} byte size`);
      assert.equal(sha256(buffer), item.asset.sha256, `${filePath} SHA-256`);
      const wavDuration = parseWavDuration(buffer, filePath);
      assert.ok(Math.abs(wavDuration - item.asset.durationSeconds) <= 0.05,
        `${filePath} WAV duration ${wavDuration} does not match manifest ${item.asset.durationSeconds}`);

      entries.push({
        question_id: questionId,
        level: question.level,
        audio_group_id: item.audioGroupId,
        transcript: item.transcript,
        audio_type: item.audioType,
        voice: item.voice,
        locale: item.locale,
        rate: item.rate,
        file: `audio/${item.asset.filename}`,
        source_path: rel(filePath),
        manifest_path: rel(manifestPath),
        filename: item.asset.filename,
        bytes: item.asset.bytes,
        sha256: item.asset.sha256,
        duration_seconds: item.asset.durationSeconds,
        clip_start_seconds: question.provenance.audioClipStartSeconds,
        clip_end_seconds: question.provenance.audioClipEndSeconds,
        answer_evidence_start_seconds: question.provenance.answerEvidenceStartSeconds,
        answer_evidence_end_seconds: question.provenance.answerEvidenceEndSeconds,
        transcript_scope: question.provenance.transcriptScope,
        audio_url: null,
        hosting_status: "NOT_CONFIGURED",
        human_listening_review: "PENDING",
        alignment_status: "NOT_TIME_ALIGNED",
      });
    }
  }

  assert.equal(entries.length, EXPECTED.audioFiles, "audio manifest entries");
  assert.equal(coveredQuestionIds.size, EXPECTED.listening, "covered Listening questions");
  for (const question of listeningById.values()) {
    assert.ok(coveredQuestionIds.has(question._id), `Listening question has no audio manifest entry: ${question._id}`);
  }

  const allWavFiles = fs.readdirSync(batchesRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .flatMap((entry) => {
      const audioDirectory = path.join(batchesRoot, entry.name, "audio");
      return fs.readdirSync(audioDirectory, { withFileTypes: true })
        .filter((file) => file.isFile() && file.name.toLowerCase().endsWith(".wav"))
        .map((file) => ({ name: file.name, path: path.join(audioDirectory, file.name) }));
    });
  assert.equal(allWavFiles.length, EXPECTED.audioFiles, "WAV file count");
  assert.deepStrictEqual(
    allWavFiles.map((file) => file.name).sort(),
    [...filenames].sort(),
    "WAV filenames vs manifest",
  );
  const totalAudioBytes = allWavFiles.reduce((sum, file) => sum + fs.statSync(file.path).size, 0);
  assert.equal(totalAudioBytes, EXPECTED.audioBytes, "total WAV bytes");

  entries.sort((left, right) => left.question_id.localeCompare(right.question_id));
  assertNoUndefined(entries, "audio manifest");
  return {
    entries,
    manifestInfos,
    totalBytes: totalAudioBytes,
    uniqueAudioGroups: audioGroupIds.size,
    uniqueFilenames: filenames.size,
    uniqueHashes: sha256Values.size,
  };
}

function validateOutput(sourceQuestions, docs) {
  assert.equal(docs.length, EXPECTED.total, "output document count");
  assertNoUndefined(docs, "output");
  const sourceById = new Map(sourceQuestions.map((question) => [question._id, question]));
  const docById = new Map();
  for (const doc of docs) {
    assert.ok(!docById.has(doc._id), `duplicate output _id ${doc._id}`);
    docById.set(doc._id, doc);
  }
  assert.equal(docById.size, sourceById.size, "output ID set size");
  assert.deepStrictEqual([...docById.keys()].sort(), [...sourceById.keys()].sort(), "source/output ID sets");

  for (const [questionId, source] of sourceById) {
    const doc = docById.get(questionId);
    assert.equal(doc.code, source.code, `${questionId}.code`);
    assert.equal(doc.skill, source.skill, `${questionId}.skill`);
    assert.equal(doc.level, source.level, `${questionId}.level`);
    assert.equal(doc.prompt, source.prompt, `${questionId}.prompt`);
    assert.equal(doc.passage, source.passage, `${questionId}.passage`);
    assert.equal(doc.correct_option_id, source.correctOptionId, `${questionId}.correct_option_id`);
    assert.equal(doc.explanation, source.explanation, `${questionId}.explanation`);
    assert.equal(doc.audio_url, source.audioUrl, `${questionId}.audio_url`);
    assert.equal(doc.transcript, source.transcript, `${questionId}.transcript`);
    assert.equal(doc.status, source.status, `${questionId}.status`);
    assert.deepStrictEqual(
      doc.options.map((option) => ({ _id: option.id, text: option.text })),
      source.options,
      `${questionId}.options`,
    );
    assert.deepStrictEqual(denormalizeProvenance(doc.provenance), source.provenance, `${questionId}.provenance`);
    assert.deepStrictEqual(
      doc.options.map((option) => option.label),
      OPTION_LABELS,
      `${questionId}.option labels`,
    );
  }
}

function buildSeedManifest({ sourceInfo, source, validated, audio, questionsText, audioText, schemaInfo }) {
  return {
    schema_version: 1,
    collection: "placement_questions",
    generator: rel(path.join(SCRIPT_DIR, "build-mongo-seed.mjs")),
    source: {
      import: sourceInfo.import,
      schema: sourceInfo.schema,
      validation: sourceInfo.validation,
      readme: sourceInfo.readme,
      audio_manifests: audio.manifestInfos,
      metadata: source.metadata,
    },
    output: {
      questions: {
        path: rel(OUT_QUESTIONS),
        bytes: Buffer.byteLength(questionsText),
        sha256: sha256(Buffer.from(questionsText)),
        documents: validated.questions.length,
      },
      audio_manifest: {
        path: rel(OUT_AUDIO),
        bytes: Buffer.byteLength(audioText),
        sha256: sha256(Buffer.from(audioText)),
        assets: audio.entries.length,
      },
      schema: schemaInfo,
    },
    counts: {
      total: validated.counts.total,
      reading: validated.counts.reading,
      listening: validated.counts.listening,
      status: { DRAFT: validated.counts.total },
      levels: validated.counts.levels,
    },
    integrity: {
      ...validated.integrity,
      source_output_ids_match: true,
      source_output_content_match: true,
      audio_assets: audio.entries.length,
      audio_bytes: audio.totalBytes,
      audio_unique_groups: audio.uniqueAudioGroups,
      audio_unique_filenames: audio.uniqueFilenames,
      audio_unique_hashes: audio.uniqueHashes,
    },
    checks: {
      source_structure: "PASS",
      source_semantics: "PASS",
      source_output_integrity: "PASS",
      audio_files_checksummed: "PASS",
      audio_duration_verified_from_wav: "PASS",
      json_schema_engine: "NOT_RUN",
      mongo_writes: 0,
      database_writes: 0,
    },
    notes: [
      "Seed contains DRAFT questions only.",
      "audio_url is null until M3 hosting is configured.",
      "created_at and updated_at are intentionally absent; M2 persistence owns audit fields.",
      "Source metadata.version is recorded in this manifest, not duplicated per question.",
      "No Java, Frontend, API, Mongo repository, or MongoDB data was changed.",
    ],
  };
}

function compareOutput(filePath, expectedText) {
  if (!fs.existsSync(filePath)) {
    fail(`Missing generated file ${rel(filePath)}; run without --check to generate it.`);
  }
  const actualText = fs.readFileSync(filePath, "utf8");
  if (actualText === expectedText) return;
  const actualLines = actualText.split("\n");
  const expectedLines = expectedText.split("\n");
  const limit = Math.max(actualLines.length, expectedLines.length);
  for (let index = 0; index < limit; index++) {
    if (actualLines[index] !== expectedLines[index]) {
      fail(`Generated file is stale: ${rel(filePath)} differs at line ${index + 1}`);
    }
  }
  fail(`Generated file is stale: ${rel(filePath)} differs`);
}

function printSummary({ sourceInfo, validated, audio, check }) {
  const mode = check ? "CHECK" : "WRITE";
  console.log(`Placement M1 Mongo seed ${mode}`);
  console.log(`source import : ${sourceInfo.import.path}`);
  console.log(`source sha256 : ${sourceInfo.import.sha256}`);
  console.log(`questions     : ${validated.counts.total} (${validated.counts.reading} Reading, ${validated.counts.listening} Listening)`);
  console.log(`audio assets  : ${audio.entries.length} (${audio.totalBytes} bytes)`);
  console.log(`output        : ${rel(OUT_QUESTIONS)}`);
  console.log(`audio manifest: ${rel(OUT_AUDIO)}`);
  console.log(`seed manifest : ${rel(OUT_MANIFEST)}`);
  console.log("status        : PASS");
}

function main() {
  const args = process.argv.slice(2);
  if (args.includes("--help")) {
    console.log("Usage: node tools/placement/build-mongo-seed.mjs [--check]");
    console.log("  --check  Validate source and compare generated files without writing.");
    return;
  }
  const unknownArgs = args.filter((arg) => arg !== "--check");
  if (unknownArgs.length > 0) fail(`Unknown argument(s): ${unknownArgs.join(", ")}`);
  const check = args.includes("--check");

  if (!fs.existsSync(OUT_SCHEMA)) fail(`Missing canonical Mongo schema: ${rel(OUT_SCHEMA)}`);
  readJson(OUT_SCHEMA);

  const sourceInfo = {
    import: fileInfo(SOURCE_IMPORT),
    schema: fileInfo(SOURCE_SCHEMA),
    validation: fileInfo(SOURCE_VALIDATION),
    readme: fileInfo(SOURCE_README),
  };
  const source = readJson(SOURCE_IMPORT);
  readJson(SOURCE_SCHEMA);
  readJson(SOURCE_VALIDATION);
  const validated = validateSource(source);
  const audio = loadAudio(validated.questions);
  const docs = validated.questions.map(toMongoQuestion);
  validateOutput(validated.questions, docs);

  const questionsText = stringify(docs);
  const audioText = stringify(audio.entries);
  const schemaInfo = fileInfo(OUT_SCHEMA);
  const manifest = buildSeedManifest({
    sourceInfo,
    source,
    validated,
    audio,
    questionsText,
    audioText,
    schemaInfo,
  });
  const manifestText = stringify(manifest);

  if (check) {
    compareOutput(OUT_QUESTIONS, questionsText);
    compareOutput(OUT_AUDIO, audioText);
    compareOutput(OUT_MANIFEST, manifestText);
    printSummary({ sourceInfo, validated, audio, check });
    return;
  }

  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(OUT_QUESTIONS, questionsText, "utf8");
  fs.writeFileSync(OUT_AUDIO, audioText, "utf8");
  fs.writeFileSync(OUT_MANIFEST, manifestText, "utf8");
  printSummary({ sourceInfo, validated, audio, check });
}

try {
  main();
} catch (error) {
  console.error(`ERROR: ${error.message}`);
  process.exitCode = 1;
}
