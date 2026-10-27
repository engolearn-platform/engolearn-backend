/*
 * One-topic A1 sample import for local preview only.
 *
 * Required environment:
 *   TOPIC_SAMPLE_MONGO_URI
 *   TOPIC_SAMPLE_DATABASE
 *
 * Safe default: read-only dry-run. Apply only with TOPIC_SAMPLE_APPLY=1.
 * Missing UK/US vocabulary audio blocks apply unless the explicit
 * TOPIC_SAMPLE_ALLOW_MISSING_VOCAB_AUDIO_FOR_LOCAL_PREVIEW=1 flag is set.
 */
const fs = require("fs");
const path = require("path");

const uri = process.env.TOPIC_SAMPLE_MONGO_URI;
const databaseName = process.env.TOPIC_SAMPLE_DATABASE;
const apply = process.env.TOPIC_SAMPLE_APPLY === "1";
const allowMissingAudioPreview =
  process.env.TOPIC_SAMPLE_ALLOW_MISSING_VOCAB_AUDIO_FOR_LOCAL_PREVIEW === "1";
const packPath = path.join(process.cwd(), "tools", "topic-sample-data", "a1-cafe-topic.json");

function fail(message) {
  print("ERROR: " + message);
  quit(1);
}

if (!uri) fail("Set TOPIC_SAMPLE_MONGO_URI to your local MongoDB connection URI.");
if (!databaseName) fail("Set TOPIC_SAMPLE_DATABASE explicitly; the script never guesses a database.");
if (/^(admin|config|local)$/i.test(databaseName) || /(^|[_-])prod(uction)?($|[_-])/i.test(databaseName)) {
  fail("Refusing system or production-looking database name: " + databaseName);
}
if (!/(\/\/(?:[^@/]+@)?(?:localhost|127\.0\.0\.1|host\.docker\.internal|mongo|mongodb)(?::\d+)?(?:\/|$))/i.test(uri)) {
  fail("This pack is restricted to local MongoDB hosts. Remote hosts are not accepted.");
}

let pack;
try {
  pack = JSON.parse(fs.readFileSync(packPath, "utf8"));
} catch (error) {
  fail("Cannot read or parse " + packPath + ": " + error.message);
}

const collections = pack.collections || {};
const collectionNames = ["topics", "topic_versions", "topic_items", "topic_item_versions"];
for (const name of collectionNames) {
  if (!Array.isArray(collections[name])) fail("Missing collection array: " + name);
}
if (collections.topics.length !== 1 || collections.topic_versions.length !== 1 ||
    collections.topic_items.length !== 5 || collections.topic_item_versions.length !== 5) {
  fail("Expected exactly 1 topic, 1 release, 5 topic items and 5 item versions.");
}

const allDocs = collectionNames.flatMap(name => collections[name].map(doc => ({name, doc})));
const ids = allDocs.map(row => row.doc._id);
if (ids.some(id => typeof id !== "string" || !/^[0-9a-f]{24}$/i.test(id))) {
  fail("Every top-level _id must be a 24-character hexadecimal string.");
}
if (new Set(ids).size !== ids.length) fail("Duplicate top-level _id found.");

const topic = collections.topics[0];
const release = collections.topic_versions[0];
if (topic.cefr_level !== "A1" || topic.latest_published_version !== release._id ||
    release.topic_id !== topic._id || release.status !== "PUBLISHED") {
  fail("Topic/release A1 publication pointers are inconsistent.");
}
const expectedVersionIds = collections.topic_item_versions.map(doc => doc._id);
if (JSON.stringify(release.item_version_ids) !== JSON.stringify(expectedVersionIds)) {
  fail("Release manifest must list the five item-version IDs in display order.");
}
const itemIdSet = new Set(collections.topic_items.map(doc => doc._id));
for (const version of collections.topic_item_versions) {
  if (version.topic_id !== topic._id || version.topic_version_id !== release._id ||
      !itemIdSet.has(version.topic_item_id) || version.status !== "PUBLISHED") {
    fail("Invalid topic/item/release relationship for item version " + version._id);
  }
  if (!Array.isArray(version.vocabularies) || version.vocabularies.length < 3 || version.vocabularies.length > 5) {
    fail("Each item must contain 3–5 vocabulary entries: " + version.title);
  }
  if (!Array.isArray(version.expressions) || version.expressions.length < 1 || version.expressions.length > 3) {
    fail("Each item must contain 1–3 expression groups: " + version.title);
  }
  for (const group of version.expressions) {
    if (!Array.isArray(group.purposes) || group.purposes.length !== 1 ||
        !Array.isArray(group.purposes[0].examples) ||
        group.purposes[0].examples.length < 1 || group.purposes[0].examples.length > 3) {
      fail("Each expression group must contain one purpose with 1–3 examples: " + version.title);
    }
    for (const example of group.purposes[0].examples) {
      if (!example.usage_note || !example.text_en || !example.text_vi) {
        fail("Every expression pattern needs English text, Vietnamese translation and a usage note.");
      }
    }
  }
  const expectedQuizTypes = ["FILL_BLANK", "SITUATIONAL_CHOICE"];
  if (!Array.isArray(version.quizzes) || version.quizzes.length !== 2 ||
      version.quizzes.map(q => q.quiz_type).join(",") !== expectedQuizTypes.join(",")) {
    fail("Each item must have FILL_BLANK followed by SITUATIONAL_CHOICE.");
  }
  for (const module of version.quizzes) {
    if (!Array.isArray(module.questions) || module.questions.length < 3 || module.questions.length > 5) {
      fail("Each formal quiz type must contain 3–5 questions: " + version.title + " / " + module.quiz_type);
    }
    for (const question of module.questions) {
      const options = question.options || [];
      const correctOptions = options.filter(option => option.is_correct === true);
      if (options.length !== 4 || correctOptions.length !== 1 ||
          !options.some(option => option.label === question.correct && option.is_correct === true)) {
        fail("Each formal question needs four choices and exactly one matching correct option: " + question.prompt);
      }
      if (!question.explanation_ok || !question.explanation_ng || !question.sentence ||
          !question.sentence.text_en || !question.sentence.meaning_vi) {
        fail("Each formal question needs a completed sentence and Vietnamese explanations.");
      }
      if (module.quiz_type === "FILL_BLANK" &&
          (!question.prompt.includes("___") || !Array.isArray(question.quiz_data.word_bank) ||
           question.quiz_data.word_bank.length !== 4 || !question.quiz_data.word_bank.includes(question.answer_data.answer))) {
        fail("Each FILL_BLANK question needs exactly one blank and a four-entry word bank.");
      }
    }
  }
}

const vocabularies = collections.topic_item_versions.flatMap(item => item.vocabularies || []);
const missingAudio = vocabularies.filter(v => !v.snapshot ||
  !v.snapshot.audio_en_url || !v.snapshot.audio_us_url);
const connection = connect(uri);
const targetDb = connection.getSiblingDB(databaseName);

function materialize(name, source) {
  const doc = Object.assign({}, source, {_id: new ObjectId(source._id)});
  for (const field of ["created_at", "updated_at", "published_at"]) {
    if (typeof doc[field] === "string") doc[field] = new Date(doc[field]);
  }
  return doc;
}
function canonical(value) {
  if (value && typeof value.toHexString === "function") return {$oid: value.toHexString()};
  if (value instanceof Date) return {$date: value.toISOString()};
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === "object") {
    const result = {};
    Object.keys(value).filter(key => key !== "_class").sort().forEach(key => {
      result[key] = canonical(value[key]);
    });
    return result;
  }
  return value;
}
function sameDocument(existing, expected) {
  return JSON.stringify(canonical(existing)) === JSON.stringify(canonical(expected));
}

const expectedRows = allDocs.map(row => ({
  name: row.name,
  source: row.doc,
  expected: materialize(row.name, row.doc)
}));
const existingRows = [];
for (const row of expectedRows) {
  const existing = targetDb.getCollection(row.name).findOne({_id: row.expected._id});
  existingRows.push({row, existing});
  if (existing && !sameDocument(existing, row.expected)) {
    fail("ID collision with different data in " + row.name + " for _id=" + row.source._id +
      ". No writes or overwrites were performed.");
  }
}
const existingTopic = targetDb.topics.findOne({title_en: topic.title_en});
if (existingTopic && String(existingTopic._id) !== topic._id &&
    !(existingTopic._id && existingTopic._id.toHexString && existingTopic._id.toHexString() === topic._id)) {
  fail("A different topic already uses the title '" + topic.title_en + "'. No writes were performed.");
}
const namespaceTopic = targetDb.topics.findOne({tags: pack.meta.pack_id});
if (namespaceTopic && namespaceTopic._id.toHexString() !== topic._id) {
  fail("The sample namespace already belongs to a different topic. No writes were performed.");
}

const presentCount = existingRows.filter(entry => entry.existing).length;
const topicPresent = Boolean(existingRows.find(entry => entry.row.name === "topics").existing);
if (topicPresent && presentCount !== expectedRows.length) {
  fail("The topic is already visible but one or more pack documents are missing. No writes were performed; inspect the local database.");
}
if (presentCount === expectedRows.length) {
  print("Sample pack is already present and matches the file. No changes made.");
  print("Database: " + databaseName + " | topic: " + topic.title_en + " | items: 5 | vocabulary: " + vocabularies.length + " | formal quiz questions: 30");
  quit(0);
}

printjson({
  mode: apply ? "APPLY (local only)" : "DRY RUN (read-only)",
  database: databaseName,
  topic: topic.title_en,
  topicLevel: topic.cefr_level,
  collections: {
    topics: collections.topics.length,
    topic_versions: collections.topic_versions.length,
    topic_items: collections.topic_items.length,
    topic_item_versions: collections.topic_item_versions.length
  },
  vocabularies: vocabularies.length,
  expressionGroups: collections.topic_item_versions.reduce((sum, item) => sum + item.expressions.length, 0),
  formalQuizQuestions: collections.topic_item_versions.reduce((sum, item) =>
    sum + item.quizzes.reduce((n, module) => n + module.questions.length, 0), 0),
  missingUkUsAudio: missingAudio.length,
  existingMatchingDocuments: presentCount,
  note: missingAudio.length ? "Preview-only: required UK/US vocabulary audio is missing." : "Vocabulary audio check passed."
});

if (!apply) {
  print("Dry run complete. Set TOPIC_SAMPLE_APPLY=1 only when you explicitly want to write this local sample.");
  quit(0);
}
if (missingAudio.length && !allowMissingAudioPreview) {
  fail("Apply blocked: " + missingAudio.length + " vocabulary entries lack required UK/US audio. For a local UI preview only, explicitly set TOPIC_SAMPLE_ALLOW_MISSING_VOCAB_AUDIO_FOR_LOCAL_PREVIEW=1. This does not make the content business-publish-ready.");
}
if (missingAudio.length && allowMissingAudioPreview) {
  print("WARNING: explicit local-preview override enabled. This will create a PUBLISHED local sample with missing audio; it is not business-publish-ready.");
}

// The public topic document is written last, so a partial import remains undiscoverable.
for (const name of ["topic_items", "topic_item_versions", "topic_versions"]) {
  const rows = expectedRows.filter(row => row.name === name && !existingRows.find(e =>
    e.row.name === name && e.row.source._id === row.source._id && e.existing));
  if (rows.length) targetDb.getCollection(name).insertMany(rows.map(row => row.expected));
}
const topicRow = expectedRows.find(row => row.name === "topics");
if (!existingRows.find(e => e.row.name === "topics" && e.existing)) {
  targetDb.topics.insertOne(topicRow.expected);
}
print("Import complete. The topic pointer was published last; no existing documents were updated or deleted.");
print("Verify with: db.topics.findOne({tags: '" + pack.meta.pack_id + "'}) and GET /api/v1/topics?level=A1.");
