(function () {
  const env = process.env;
  const name = env.MONGO_IMPORT_DATABASE;
  const apply = env.GRAMMAR_V2_APPLY === '1';
  function check(value, message) { if (!value) throw new Error(message); }
  function canonical(value) {
    if (Array.isArray(value)) return value.map(canonical);
    if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(k => [k, canonical(value[k])]));
    return value;
  }
  function hash(document) {
    return require('crypto').createHash('sha256').update(JSON.stringify(canonical(JSON.parse(EJSON.stringify(document, {relaxed: false}))))).digest('hex');
  }
  function child(topic, lesson, now) {
    const result = Object.assign({}, lesson, {_id: lesson.lesson_id, topic_id: topic._id, schema_version: 2,
      created_at: now, updated_at: now, _class: 'fit.iuh.engolearn.grammar.model.GrammarLessonDocument'});
    delete result.lesson_id;
    return result;
  }
  function content(document) {
    const copy = Object.assign({}, document);
    for (const k of ['created_at', 'updated_at', '_class']) delete copy[k];
    return copy;
  }
  try {
    check(name && !/^(admin|config|local)$/i.test(name) && !/(^|[_-])prod(uction)?($|[_-])/i.test(name), 'A non-production database is required');
    check(env.MONGO_IMPORT_URI, 'MongoDB URI is required');
    const connection = new Mongo(env.MONGO_IMPORT_URI);
    const database = connection.getDB(name);
    check(database.hello().setName, 'Migration requires a replica set');
    const ids = JSON.parse(env.GRAMMAR_V2_TOPIC_IDS_JSON || '[]');
    check(Array.isArray(ids) && ids.every(id => typeof id === 'string'), 'Topic IDs must be strings');
    const topics = database.grammar_topics.find(ids.length ? {_id: {$in: ids}} : {}).toArray();
    if (ids.length) check(topics.length === new Set(ids).size, 'Some requested topics do not exist');
    const counter = database.grammar_counters.findOne({_id: 'topic-order-and-code'});
    let maxCode = 0;
    database.grammar_topics.find({}, {code: 1}).forEach(topic => {
      const match = /^ENG-GRM-T(\d+)$/.exec(topic.code || '');
      check(match, 'Unsupported topic code: ' + topic.code);
      maxCode = Math.max(maxCode, Number(match[1]));
    });
    if (maxCode) check(counter && Number(counter.topic_sequence) >= maxCode, 'Counter is missing or below an allocated topic code; reconcile it before migration');
    const plans = [];
    const already = [];
    const allIds = new Set();
    const allCodes = new Set();
    for (const topic of topics) {
      check(typeof topic._id === 'string', 'ObjectId topics require a separately reviewed mapping');
      if (topic.schema_version === 2) {
        check(!Object.hasOwn(topic, 'lessons'), 'V2 topic still has embedded lessons');
        const children = database.grammar_lessons.find({topic_id:topic._id}).toArray();
        const activeOrders = children.filter(l=>l.status!=='ARCHIVED').map(l=>l.order).sort((a,b)=>a-b);
        check(activeOrders.length <= 10 && activeOrders.every((order,index)=>order===index+1), 'V2 active lesson order/limit is invalid');
        if (topic.status === 'PUBLISHED') check(children.some(l=>l.status==='PUBLISHED'), 'Published V2 topic has no published lessons');
        for (const lesson of children) {
          check(typeof lesson._id === 'string' && lesson.schema_version === 2 && !allIds.has(lesson._id), 'V2 lesson identity/schema is invalid');
          allIds.add(lesson._id);
          const code = new RegExp('^' + topic.code + '-L(\\d+)$').exec(lesson.code || '');
          check(code && Number(code[1]) <= Number(topic.lesson_sequence) && !allCodes.has(lesson.code), 'V2 lesson code is invalid or duplicated');
          allCodes.add(lesson.code);
        }
        already.push({id: topic._id, status: 'ALREADY_V2', integrity: 'CURRENT_STRUCTURE_ONLY', migrationSourceHash: topic.migration_source_hash || null});
        continue;
      }
      check(topic.schema_version == null || topic.schema_version === 1, 'Unsupported source schema');
      check(Array.isArray(topic.lessons), 'Legacy topic must contain an embedded lesson array');
      check(topic.version != null && Number.isInteger(Number(topic.version)), 'Topic version is missing');
      const active = topic.lessons.filter(lesson => lesson.status !== 'ARCHIVED').map(lesson => lesson.order).sort((a,b) => a-b);
      check(active.length <= 10 && active.every((order,index) => order === index+1), 'Active lesson order/limit is invalid');
      for (const lesson of topic.lessons) {
        check(typeof lesson.lesson_id === 'string' && !allIds.has(lesson.lesson_id), 'Missing/duplicate lesson ID');
        allIds.add(lesson.lesson_id);
        const match = new RegExp('^' + topic.code + '-L(\\d+)$').exec(lesson.code || '');
        check(match && Number(match[1]) <= Number(topic.lesson_sequence) && !allCodes.has(lesson.code), 'Lesson code/sequence is invalid or duplicated');
        allCodes.add(lesson.code);
        const existing = database.grammar_lessons.findOne({_id: lesson.lesson_id});
        if (existing) check(hash(content(existing)) === hash(content(child(topic,lesson,new Date()))), 'Conflicting standalone lesson: ' + lesson.lesson_id);
        const sameCode = database.grammar_lessons.findOne({code: lesson.code, _id: {$ne: lesson.lesson_id}});
        check(!sameCode, 'Standalone lesson code is already in use');
      }
      // Standalone rows with no matching embedded source must never be treated as a clean migration.
      check(database.grammar_lessons.countDocuments({topic_id:topic._id, _id:{$nin:topic.lessons.map(l=>l.lesson_id)}}) === 0,
        'Unexpected standalone lessons already reference this legacy topic');
      plans.push({id: topic._id, sourceVersion: Number(topic.version), sourceHash: hash(topic), lessons: topic.lessons.length});
    }
    if (apply) {
      const expected = JSON.parse(env.GRAMMAR_V2_EXPECTED_PLAN_JSON || 'null');
      check(Array.isArray(expected) && hash(expected) === hash(plans), 'Apply plan differs from the reviewed preflight');
      for (const plan of plans) {
        const session = connection.startSession();
        try {
          session.withTransaction(() => {
            const tx = session.getDatabase(name);
            const topic = tx.grammar_topics.findOne({_id:plan.id});
            check(topic && hash(topic) === plan.sourceHash, 'Topic changed since preflight: ' + plan.id);
            const now = new Date();
            for (const lesson of topic.lessons) {
              const target = child(topic,lesson,now);
              const existing = tx.grammar_lessons.findOne({_id:target._id});
              if (existing) check(hash(content(existing)) === hash(content(target)), 'Child changed since preflight');
              else tx.grammar_lessons.insertOne(target);
            }
            const updated = tx.grammar_topics.updateOne({_id:plan.id,version:topic.version},
              {$set:{schema_version:2,migration_source_hash:plan.sourceHash,migrated_at:now},$unset:{lessons:''},$inc:{version:1}});
            check(updated.modifiedCount === 1, 'Topic version changed during migration');
          });
        } finally { session.endSession(); }
      }
    }
    print('GRAMMAR_V2_REPORT ' + JSON.stringify({mode:apply?'APPLY':'PREFLIGHT',database:name,plan:plans,alreadyV2:already,
      migrated:apply?plans.length:0,writesPerformed:apply&&plans.length>0}));
  } catch (error) {
    print('GRAMMAR_V2_ERROR ' + error.message);
    quit(1);
  }
})();
