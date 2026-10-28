const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const repoRoot = process.cwd();
const scriptPath = path.join(repoRoot, 'tools', 'topic-sample-data', 'import-all-cefr-samples.mongosh.js');
const packPath = path.join(repoRoot, 'tools', 'topic-sample-data', 'all-cefr-topic-samples-v2.json');
assert.ok(fs.existsSync(scriptPath), 'Importer script is missing.');
assert.ok(fs.existsSync(packPath), 'CEFR JSON pack is missing.');

class MockObjectId {
  constructor(hex) { this.hex = String(hex).toLowerCase(); }
  toHexString() { return this.hex; }
  toString() { return this.hex; }
}

const collectionNames = ['topics', 'topic_versions', 'topic_items', 'topic_item_versions'];
const store = Object.fromEntries(collectionNames.map(name => [name, new Map()]));

function getHex(value) {
  return value && typeof value.toHexString === 'function' ? value.toHexString().toLowerCase() : String(value).toLowerCase();
}
function matches(document, query) {
  if (!document || !query) return false;
  return Object.entries(query).every(([key, expected]) => {
    const actual = document[key];
    if (key === '_id') return getHex(actual) === getHex(expected);
    if (Array.isArray(actual)) return actual.some(value => value === expected);
    return actual === expected;
  });
}
class MockCollection {
  constructor(name) { this.name = name; }
  findOne(query) { return [...store[this.name].values()].find(document => matches(document, query)) || null; }
  insertMany(documents) {
    for (const document of documents) {
      const id = getHex(document._id);
      if (store[this.name].has(id)) throw new Error('duplicate key in mock store');
      store[this.name].set(id, document);
    }
  }
  find(query) {
    return { toArray: () => [...store[this.name].values()].filter(document => matches(document, query)) };
  }
}
class MockDatabase {
  getCollection(name) { return new MockCollection(name); }
  get topics() { return new MockCollection('topics'); }
}
class MockMongo {
  constructor(uri) { assert.ok(uri, 'Mongo URI must be supplied.'); }
  getDB(name) {
    assert.equal(name, 'engolearn_db');
    return new MockDatabase();
  }
}

function runImporter(apply) {
  let output = '';
  const env = {
    TOPIC_SAMPLE_MONGO_URI: 'mongodb://root:secret@mongodb:27017/?authSource=admin',
    TOPIC_SAMPLE_DATABASE: 'engolearn_db'
  };
  if (apply) env.TOPIC_SAMPLE_APPLY = '1';
  const context = {
    require,
    process: { env, cwd: () => repoRoot },
    Mongo: MockMongo,
    ObjectId: MockObjectId,
    print: (...args) => { output += args.join(' ') + '\n'; },
    printjson: value => { output += JSON.stringify(value, null, 2) + '\n'; },
    quit: code => { if (code !== 0) throw new Error('Importer exited with code ' + code); },
    Date, Map, Set, Object, Array, String, Number, Boolean, Error, JSON, RegExp, Math
  };
  vm.runInNewContext(fs.readFileSync(scriptPath, 'utf8'), context, {
    filename: scriptPath,
    timeout: 10000
  });
  return output;
}

const dryRunOutput = runImporter(false);
assert.match(dryRunOutput, /DRY RUN \(read-only\)/);
assert.match(dryRunOutput, /Preflight complete\. No data was written/);
assert.equal(Object.values(store).reduce((total, collection) => total + collection.size, 0), 0,
  'Dry-run must not write documents.');

const applyOutput = runImporter(true);
assert.match(applyOutput, /IMPORT SUCCESS: all 72 documents are present and verified\./);
const countsAfterApply = Object.fromEntries(Object.entries(store).map(([name, collection]) => [name, collection.size]));
assert.deepEqual(countsAfterApply, {
  topics: 6, topic_versions: 6, topic_items: 30, topic_item_versions: 30
});

const rerunOutput = runImporter(true);
assert.match(rerunOutput, /existingMatchingDocuments": 72/);
assert.match(rerunOutput, /IMPORT SUCCESS: all 72 documents are present and verified\./);
assert.deepEqual(
  Object.fromEntries(Object.entries(store).map(([name, collection]) => [name, collection.size])),
  countsAfterApply,
  'Rerun must be idempotent and must not duplicate documents.'
);

console.log('PASS: mock dry-run writes nothing; import verifies 72 documents; rerun is idempotent.');
console.log('Counts:', JSON.stringify(countsAfterApply));
console.log('Note: this test uses an in-memory Mongo mock, not a real MongoDB server.');
