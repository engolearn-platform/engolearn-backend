// Local input validation only. The shared importer is evaluated in memory with main disabled.
(function () {
  const fs = require('fs');
  const path = require('path');
  const root = process.cwd();
  const directory = path.join(root, 'tools/placement/data/dev-v3');
  const samples = path.join(root, 'tools/mongo-json-importer/samples');
  const canonical = JSON.parse(fs.readFileSync(path.join(root, 'tools/placement/data/seed/placement_questions.mongo.json'), 'utf8'));
  const selection = JSON.parse(fs.readFileSync(path.join(directory, 'selection-manifest.json'), 'utf8'));
  const media = JSON.parse(fs.readFileSync(path.join(directory, 'audio-url-manifest.json'), 'utf8'));
  const importer = path.join(root, 'tools/mongo-json-importer/importer.mongosh.js');
  const source = fs.readFileSync(importer, 'utf8');
  const marker = 'try { main(); }';
  if (source.split(marker).length !== 2) throw Error('Importer entry point changed; review harness before use');
  const isolated = source.replace(marker, 'try { capture({loadDatasets: loadDatasets, compileFieldPatterns: compileFieldPatterns}); }');
  let connections = 0;
  function loader(fileSystem) {
    let api;
    new Function('require', 'EJSON', 'ObjectId', 'Mongo', 'print', 'printjson', 'quit', 'capture', isolated)(
      name => name === 'fs' ? fileSystem : require(name), EJSON, ObjectId,
      function () { connections++; throw Error('Mongo connections forbidden in local validation'); },
      print, printjson, code => { throw Error('Importer exited: ' + code); }, captured => { api = captured; });
    return api;
  }
  function check(condition, message) { if (!condition) throw Error(message); }
  const api = loader(fs);
  const files = ['placement-v3-questions.importer.json', 'placement-v3-test-sets-v2.importer.json'].map(n => path.join(samples, n));
  for (const fieldPatterns of ['none', '_id,*_id,*_ids']) {
    const stats = { converted: 0 };
    const data = api.loadDatasets(files, api.compileFieldPatterns(fieldPatterns), stats);
    check(data.total === 321 && data.groups.size === 2, '321 documents / 2 collections required');
    check(stats.converted === 0, 'String IDs must not be normalized to ObjectId');
    const questions = data.groups.get('placement_questions');
    const sets = data.groups.get('placement_test_sets');
    check(questions.length === 320 && sets.length === 1, 'Unexpected collection counts');
    check([...data.groups.values()].flat().every(e => typeof e.document._id === 'string'), 'String root IDs required');
    const bank = new Map(questions.map(e => [e.document._id, e.document]));
    check(JSON.stringify(questions.map(e => e.document)) === JSON.stringify(canonical), 'Canonical fields, values and order must be unchanged');
    const set = sets[0].document;
    check(set._id === 'PLT-V3-DEV-001-V2' && set.version === 2 && set.status === 'DRAFT' && set.items.length === 20, 'DRAFT snapshot contract');
    check(set.items.every(i => typeof i.id === 'string' && bank.has(i.id)), 'Snapshot references must resolve');
    check(set.items.slice(14).every(i => typeof i.audio.url === 'string' && typeof i.audio.duration_seconds === 'number'), 'Listening metadata');
    check(JSON.stringify(set.items.map(i => i.id)) === JSON.stringify(selection.questions.map(q => q.question_id)), 'Selection order');
    const matrix = {};
    const families = new Set();
    for (let index = 0; index < set.items.length; index++) {
      const item = set.items[index];
      const question = bank.get(item.id);
      check(Number.isInteger(item.order_index) && item.order_index === index + 1, 'Mongo order_index type/value');
      check(item.skill === question.skill && item.level === question.level, 'Skill/level snapshot fidelity');
      check(item.skill === (index < 14 ? 'READING' : 'LISTENING'), 'Reading before Listening');
      for (const field of ['prompt', 'passage', 'correct_option_id', 'explanation', 'transcript']) {
        check((item[field] ?? null) === (question[field] ?? null), 'Snapshot field fidelity: ' + field);
      }
      check(JSON.stringify(item.options) === JSON.stringify(question.options), 'Options content/order/IDs unchanged');
      check(item.label === question.provenance.title, 'Snapshot label');
      const key = item.level + '/' + item.skill;
      matrix[key] = (matrix[key] || 0) + 1;
      if (item.level === 'B2') {
        check(!families.has(question.provenance.semantic_family_id), 'Duplicate B2 semantic family');
        families.add(question.provenance.semantic_family_id);
      }
      if (index >= 14) {
        check(item.audio.duration_seconds === question.provenance.audio_duration_seconds, 'Decimal audio duration');
        check(item.audio.url === media.metadata.audio_base_url + (media.metadata.audio_base_url.startsWith('/') ? '/' : '/audio/') + question.provenance.audio_sha256 + '.wav', 'Audio URL/hash reference');
      }
    }
    for (const level of ['A1', 'A2', 'B1', 'B2']) {
      const reading = ['A1', 'A2'].includes(level) ? 4 : 3;
      check(matrix[level + '/READING'] === reading && matrix[level + '/LISTENING'] === 5 - reading, 'Level matrix');
    }
    print('REAL_IMPORTER_INPUT_LOADER=PASS; EJSON=PASS; objectIdFields=' + fieldPatterns + '; converted=0; collections=2; documents=321');
  }
  const crypto = require('crypto');
  check(media.assets.length === 96, '96 resolved media references');
  for (const asset of media.assets) {
    const digest = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
    check(digest(path.join(root, asset.source_path)) === asset.sha256, 'Packaged canonical WAV checksum');
  }
  print('PLACEMENT_DOMAIN_VALIDATOR=PASS; snapshot fidelity/matrix/B2 families/references PASS; packaged canonical WAV hashes=96/96');
  function rejects(operation, label) {
    let rejected = false;
    try { operation(); } catch (_) { rejected = true; }
    check(rejected, 'Negative regression was not rejected: ' + label);
  }
  rejects(() => api.loadDatasets([files[0], files[0]], [], { converted: 0 }), 'duplicate IDs across files');
  rejects(() => api.loadDatasets([path.join(directory, 'selection-manifest.json')], [], { converted: 0 }), 'sidecar is not import input');
  for (const invalid of [
    { collections: { placement_questions: {} } },
    { collections: { placement_questions: [{ _id: null }] } },
    { collections: { placement_questions: [{}] } },
    { collections: { placement_questions: [null] } },
    { collections: { 'system.bad': [{ _id: 'x' }] } },
    { collections: { '$bad': [{ _id: 'x' }] } }
  ]) {
    const memory = loader({ readFileSync: () => JSON.stringify(invalid) });
    rejects(() => memory.loadDatasets(['in-memory-negative-case'], [], { converted: 0 }), 'invalid envelope/document/name');
  }
  check(connections === 0, 'No Mongo connections permitted');
  print('NEGATIVE_INPUT_REGRESSIONS=8 PASS; Mongo connections=0; database writes=0');
  print('IMPORTER DATABASE DryRun: NOT_RUN — target not approved (this is not import.ps1 -DryRun)');
})();
