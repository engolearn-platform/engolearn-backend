/*
 * EngoLearn CEFR local-preview importer.
 * Compatible with mongosh in the existing Docker Compose network.
 * The JSON pack is local-preview content only; audio is intentionally null.
 * No existing document is updated or deleted. Identical documents are skipped.
 */
var stage = "starting";
(function () {
  try {
    var fs = require("fs");
    var path = require("path");

    function stop(message) { throw new Error(message); }
    function isHexId(value) { return typeof value === "string" && /^[0-9a-f]{24}$/i.test(value); }
    function idAsHex(value) {
      if (value && typeof value.toHexString === "function") return value.toHexString().toLowerCase();
      if (isHexId(value)) return value.toLowerCase();
      return null;
    }
    function isLocalUri(value) {
      return typeof value === "string" && /:\/\/(?:[^@/]+@)?(?:localhost|127\.0\.0\.1|host\.docker\.internal|mongo|mongodb)(?::\d+)?(?:\/|$)/i.test(value);
    }
    function hasValue(list, value) {
      return Array.isArray(list) && list.some(function (item) { return item === value; });
    }
    function normalize(value) {
      if (value === null || typeof value === "undefined") return value;
      if (value instanceof Date) return { __date: value.toISOString() };
      if (value && typeof value.toHexString === "function") return { __objectId: value.toHexString().toLowerCase() };
      if (Array.isArray(value)) return value.map(normalize);
      if (typeof value === "object") {
        var out = {};
        Object.keys(value).filter(function (key) { return key !== "_class"; }).sort().forEach(function (key) {
          out[key] = normalize(value[key]);
        });
        return out;
      }
      return value;
    }
    function sameDocument(a, b) {
      return JSON.stringify(normalize(a)) === JSON.stringify(normalize(b));
    }
    function materialize(source) {
      var doc = Object.assign({}, source, { _id: new ObjectId(source._id) });
      ["created_at", "updated_at", "published_at"].forEach(function (field) {
        if (typeof doc[field] === "string") {
          var parsed = new Date(doc[field]);
          if (isNaN(parsed.getTime())) stop("Invalid date " + field + " on document " + source._id);
          doc[field] = parsed;
        }
      });
      return doc;
    }
    function printSummary(value) {
      if (typeof printjson === "function") printjson(value);
      else print(JSON.stringify(value, null, 2));
    }

    stage = "reading configuration";
    var uri = process.env.TOPIC_SAMPLE_MONGO_URI;
    var databaseName = process.env.TOPIC_SAMPLE_DATABASE;
    var apply = process.env.TOPIC_SAMPLE_APPLY === "1";
    var expectedPackId = "engolearn-all-cefr-samples-v2";
    if (!uri) stop("TOPIC_SAMPLE_MONGO_URI is missing.");
    if (!databaseName) stop("TOPIC_SAMPLE_DATABASE is missing.");
    if (/^(admin|config|local)$/i.test(databaseName) || /(^|[_-])prod(uction)?($|[_-])/i.test(databaseName)) {
      stop("Refusing system or production-looking database: " + databaseName);
    }
    if (!isLocalUri(uri)) stop("Only a local MongoDB host is allowed for this preview importer.");

    stage = "locating JSON pack";
    var candidates = [
      path.join(process.cwd(), "tools", "topic-sample-data", "all-cefr-topic-samples-v2.json"),
      "/workspace/tools/topic-sample-data/all-cefr-topic-samples-v2.json"
    ];
    var packPath = null;
    for (var pi = 0; pi < candidates.length; pi++) {
      if (fs.existsSync(candidates[pi])) { packPath = candidates[pi]; break; }
    }
    if (!packPath) stop("Cannot find all-cefr-topic-samples-v2.json. Checked: " + candidates.join("; "));

    stage = "parsing JSON pack";
    var pack = JSON.parse(fs.readFileSync(packPath, "utf8"));
    if (!pack || !pack.meta || pack.meta.pack_id !== expectedPackId) stop("Unexpected sample pack id.");
    var collections = pack.collections;
    var names = ["topics", "topic_versions", "topic_items", "topic_item_versions"];
    if (!collections || typeof collections !== "object") stop("JSON pack has no collections object.");
    names.forEach(function (name) {
      if (!collections[name] || !Array.isArray(collections[name])) stop("Missing collection array: " + name);
    });
    if (collections.topics.length !== 6 || collections.topic_versions.length !== 6 ||
        collections.topic_items.length !== 30 || collections.topic_item_versions.length !== 30) {
      stop("Unexpected counts. Expected 6 topics, 6 releases, 30 items, and 30 item versions.");
    }

    stage = "checking IDs and content references";
    var allRows = [];
    names.forEach(function (name) {
      collections[name].forEach(function (doc) { allRows.push({ name: name, source: doc }); });
    });
    var seenIds = Object.create(null);
    allRows.forEach(function (row) {
      var id = row.source && row.source._id;
      if (!isHexId(id)) stop("Invalid root _id in " + row.name + ".");
      var lowerId = id.toLowerCase();
      if (seenIds[lowerId]) stop("Duplicate root _id in pack: " + id);
      seenIds[lowerId] = true;
    });
    var topicsById = new Map(collections.topics.map(function (doc) { return [doc._id, doc]; }));
    var releasesById = new Map(collections.topic_versions.map(function (doc) { return [doc._id, doc]; }));
    var itemsById = new Map(collections.topic_items.map(function (doc) { return [doc._id, doc]; }));
    var versionsById = new Map(collections.topic_item_versions.map(function (doc) { return [doc._id, doc]; }));
    collections.topics.forEach(function (topic) {
      if (!Array.isArray(topic.tags) || !hasValue(topic.tags, expectedPackId)) stop("Topic has no pack namespace tag: " + topic.title_en);
      var release = releasesById.get(topic.latest_published_version);
      if (!release || release.topic_id !== topic._id || release.status !== "PUBLISHED") stop("Invalid latest release pointer for: " + topic.title_en);
      if (!Array.isArray(release.item_version_ids) || release.item_version_ids.length !== 5) stop("Release must contain exactly five item versions: " + topic.title_en);
      release.item_version_ids.forEach(function (id) {
        if (typeof id !== "string" || !versionsById.has(id)) stop("Unresolved release item-version reference: " + id);
      });
    });
    collections.topic_versions.forEach(function (release) {
      if (!topicsById.has(release.topic_id)) stop("Unresolved topic_version.topic_id: " + release._id);
    });
    collections.topic_items.forEach(function (item) {
      if (!topicsById.has(item.topic_id)) stop("Unresolved topic_item.topic_id: " + item._id);
    });
    var questionCount = 0;
    var vocabCount = 0;
    collections.topic_item_versions.forEach(function (iv) {
      var topic = topicsById.get(iv.topic_id);
      var release = releasesById.get(iv.topic_version_id);
      var item = itemsById.get(iv.topic_item_id);
      if (!topic || !release || !item || item.topic_id !== iv.topic_id || release.topic_id !== iv.topic_id) {
        stop("Broken topic/item/release reference for item version: " + iv._id);
      }
      if (!Array.isArray(iv.vocabularies) || !Array.isArray(iv.expressions) || !Array.isArray(iv.quizzes)) {
        stop("Missing vocabulary, expression, or quiz array on: " + iv.title);
      }
      vocabCount += iv.vocabularies.length;
      iv.expressions.forEach(function (group) {
        var purposes = group && group.purposes;
        if (!Array.isArray(purposes)) stop("Missing expression purposes on: " + iv.title);
        purposes.forEach(function (purpose) {
          var examples = purpose && purpose.examples;
          if (!Array.isArray(examples)) stop("Missing expression examples on: " + iv.title);
          examples.forEach(function (example) {
            var linkedIds = example && example.linked_vocab_ids;
            if (linkedIds !== null && typeof linkedIds !== "undefined" && !Array.isArray(linkedIds)) {
              stop("linked_vocab_ids must be an array when present on: " + iv.title);
            }
            (Array.isArray(linkedIds) ? linkedIds : []).forEach(function (id) {
              if (!iv.vocabularies.some(function (vocab) { return vocab && vocab.vocab_id === id; })) {
                stop("Unresolved linked vocabulary id " + id + " on: " + iv.title);
              }
            });
          });
        });
      });
      iv.quizzes.forEach(function (module) {
        if (!module || !Array.isArray(module.questions)) stop("Missing quiz questions on: " + iv.title);
        questionCount += module.questions.length;
      });
    });
    if (questionCount !== 180 || vocabCount !== 90) stop("Unexpected content totals: vocabulary=" + vocabCount + ", questions=" + questionCount);

    stage = "connecting to local MongoDB";
    var connection = new Mongo(uri);
    var db = connection.getDB(databaseName);
    var expectedRows = allRows.map(function (row) {
      return { name: row.name, source: row.source, expected: materialize(row.source) };
    });
    stage = "checking existing IDs and namespace conflicts";
    var existingRows = [];
    expectedRows.forEach(function (row) {
      var existing = db.getCollection(row.name).findOne({ _id: row.expected._id });
      existingRows.push({ row: row, existing: existing });
      if (existing && !sameDocument(existing, row.expected)) {
        stop("ID collision with different data in " + row.name + " for " + row.source._id + ". No records were updated or deleted.");
      }
    });
    collections.topics.forEach(function (topic) {
      var byTitle = db.topics.findOne({ title_en: topic.title_en });
      if (byTitle && String(byTitle._id) !== topic._id && idAsHex(byTitle._id) !== topic._id.toLowerCase()) {
        stop("A different topic already has this title: " + topic.title_en + ". No records were updated or deleted.");
      }
      var byLevel = db.topics.findOne({ tags: expectedPackId, cefr_level: topic.cefr_level });
      if (byLevel && idAsHex(byLevel._id) !== topic._id.toLowerCase()) {
        stop("This pack namespace/level already belongs to another topic: " + topic.cefr_level + ". No records were updated or deleted.");
      }
    });
    var presentCount = existingRows.filter(function (entry) { return !!entry.existing; }).length;
    var missingAudioCount = 0;
    collections.topic_item_versions.forEach(function (iv) {
      iv.vocabularies.forEach(function (v) {
        if (!v.snapshot || !v.snapshot.audio_en_url || !v.snapshot.audio_us_url) missingAudioCount++;
      });
    });
    printSummary({
      mode: apply ? "APPLY (local preview only)" : "DRY RUN (read-only)",
      database: databaseName,
      packId: expectedPackId,
      counts: { topics: 6, topic_versions: 6, topic_items: 30, topic_item_versions: 30, vocabulary: vocabCount, quizQuestions: questionCount },
      existingMatchingDocuments: presentCount,
      missingUkUsAudio: missingAudioCount,
      note: "Local preview only. Audio is missing and the content is not approved for production publication."
    });
    if (!apply) {
      print("Preflight complete. No data was written. The wrapper may now ask for IMPORT confirmation.");
      return;
    }

    stage = "inserting missing records";
    ["topic_items", "topic_item_versions", "topic_versions", "topics"].forEach(function (name) {
      var missing = expectedRows.filter(function (row) {
        if (row.name !== name) return false;
        return !existingRows.some(function (entry) { return entry.row.name === name && entry.row.source._id === row.source._id && entry.existing; });
      });
      if (missing.length > 0) {
        db.getCollection(name).insertMany(missing.map(function (row) { return row.expected; }));
        print("Inserted " + missing.length + " documents into " + name + ".");
      } else {
        print("No insert needed for " + name + ".");
      }
    });

    stage = "verifying stored documents";
    expectedRows.forEach(function (row) {
      var saved = db.getCollection(row.name).findOne({ _id: row.expected._id });
      if (!saved || !sameDocument(saved, row.expected)) stop("Post-insert verification failed for " + row.name + " / " + row.source._id);
    });
    var taggedTopics = db.topics.find({ tags: expectedPackId }).toArray();
    if (!Array.isArray(taggedTopics) || taggedTopics.length !== 6) stop("Expected six topics with this pack tag, found " + (taggedTopics ? taggedTopics.length : 0));
    print("IMPORT SUCCESS: all 72 documents are present and verified. No existing documents were updated or deleted.");
    print("Next, test GET /api/v1/topics?level=A1 (then A2, B1, B2, C1 and C2).");
  } catch (error) {
    print("IMPORT FAILED during stage: " + stage);
    print(error && error.stack ? error.stack : String(error));
    quit(1);
  }
})();