// Applies a Vietnamese content revision to the DEV seed and inserts a new immutable snapshot.
(function () {
  const fs = require('fs');
  const path = require('path');
  const root = process.env.PLACEMENT_PACKAGE_ROOT || '/workspace';
  const apply = process.env.MONGO_IMPORT_APPLY === '1';
  const seed = path.join(root, 'tools/placement/data/seed');
  const base = EJSON.parse(fs.readFileSync(path.join(seed, 'placement_questions.mongo.json'), 'utf8'));
  const explanations = JSON.parse(fs.readFileSync(path.join(seed, 'placement_explanations.vi.json'), 'utf8'));
  const sample = path.join(root, 'tools/mongo-json-importer/samples/placement-v3-test-sets-v2.importer.json');
  const [v2] = EJSON.parse(fs.readFileSync(sample, 'utf8')).collections.placement_test_sets;
  if (base.length !== 320 || Object.keys(explanations).length !== base.length || base.some(question => {
    const text = explanations[question._id];
    return typeof text !== 'string' || text.trim().length < 40 || text === question.explanation;
  })) throw Error('The revised explanations must cover all 320 canonical questions');
  if (v2._id !== 'PLT-V3-DEV-001-V2' || v2.version !== 2 || v2.status !== 'DRAFT' ||
      v2.items.length !== 20 || v2.items.some(item => !explanations[item.id])) {
    throw Error('Unexpected V2 seed snapshot');
  }

  const v3 = {
    ...v2,
    _id: 'PLT-V3-DEV-001-V3',
    name: 'Placement DEV V3 — Giải thích đáp án',
    version: 3,
    items: v2.items.map(item => ({ ...item, explanation: explanations[item.id] })),
  };
  const name = process.env.MONGO_IMPORT_DATABASE;
  if (!name || /^(admin|config|local)$|(^|[_-])prod(uction)?($|[_-])/i.test(name)) {
    throw Error('A development database is required');
  }
  if (!process.env.MONGO_IMPORT_URI) throw Error('Mongo connection is required');
  const connection = new Mongo(process.env.MONGO_IMPORT_URI);
  if (!connection.getDB('admin').runCommand({hello: 1}).setName) throw Error('MongoDB replica set required');
  const keys = ['code', 'skill', 'level', 'prompt', 'passage', 'transcript', 'options', 'correct_option_id'];

  function comparable(value) {
    if (Array.isArray(value)) return value.map(comparable);
    if (value !== null && typeof value === 'object') {
      return Object.fromEntries(Object.keys(value).sort().map(key => [key, comparable(value[key])]));
    }
    return value;
  }
  function same(a, b) {
    return JSON.stringify(comparable(a ?? null)) === JSON.stringify(comparable(b ?? null));
  }
  function prepare(database) {
    const sets = database.getCollection('placement_test_sets');
    const questions = database.getCollection('placement_questions');
    const legacy = sets.findOne({_id: v2._id});
    if (!legacy || !Object.keys(v2).every(key => same(legacy[key], v2[key]))) {
      throw Error('V2 does not match the packaged seed. Run the baseline setup before adding V3.');
    }
    const collision = sets.findOne({code: v3.code, version: v3.version, _id: {$ne: v3._id}});
    const existing = sets.findOne({_id: v3._id});
    if (collision || (existing && !Object.keys(v3).every(key => same(existing[key], v3[key])))) {
      throw Error('Version 3 already exists with other content. Keep it and use the Admin workflow for the next version.');
    }
    const fieldProjection = Object.fromEntries([...keys, 'explanation'].map(field => [field, 1]));
    const stored = new Map(questions.find({_id: {$in: base.map(question => question._id)}}, fieldProjection)
      .toArray().map(question => [question._id, question]));
    const operations = [];
    let alreadyRevised = 0;
    let preserved = 0;
    let missing = 0;
    for (const question of base) {
      const current = stored.get(question._id);
      if (!current) {
        missing++;
        continue;
      }
      const explanation = explanations[question._id];
      if (current.explanation === explanation) alreadyRevised++;
      else if (current.explanation !== question.explanation || !keys.every(key => same(current[key], question[key]))) preserved++;
      else operations.push({updateOne: {
        filter: {_id: question._id, explanation: question.explanation},
        update: {$set: {explanation, updated_at: new Date()}, $inc: {revision: NumberLong('1')}},
      }});
    }
    return {questions, sets, operations, alreadyRevised, preserved, missing, insertSnapshot: !existing};
  }

  function report(plan, applied) {
    printjson({database: name, applied, explanations: plan.operations.length, alreadyRevised: plan.alreadyRevised,
      preserved: plan.preserved, missing: plan.missing, insertSnapshot: plan.insertSnapshot, attemptsTouched: false});
  }
  if (!apply) {
    report(prepare(connection.getDB(name)), false);
    return;
  }
  const session = connection.startSession();
  try {
    let committed;
    session.withTransaction(() => {
      const plan = prepare(session.getDatabase(name));
      if (plan.operations.length) {
        const result = plan.questions.bulkWrite(plan.operations);
        if (result.matchedCount !== plan.operations.length) throw Error('A question changed while applying the revision');
      }
      if (plan.insertSnapshot) plan.sets.insertOne(v3);
      committed = plan;
    });
    report(committed, true);
  } finally {
    session.endSession();
  }
})();
