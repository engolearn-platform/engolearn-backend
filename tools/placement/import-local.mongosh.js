// Domain preflight around the unchanged generic importer. Never resets attempts.
(function () {
  const fs=require('fs');
  load('/workspace/tools/placement/validate-dev-package.mongosh.js');
  const files=JSON.parse(process.env.MONGO_IMPORT_FILES_JSON || '[]');
  const name=process.env.MONGO_IMPORT_DATABASE;
  if(!name || /^(admin|config|local)$|(^|[_-])prod(uction)?($|[_-])/i.test(name)) throw Error('Invalid DEV database');
  if(files.length!==2) throw Error('Exactly the questions and V2 snapshot inputs are required');
  const connection=new Mongo(process.env.MONGO_IMPORT_URI);
  if(!connection.getDB('admin').runCommand({hello:1}).setName) throw Error('Real registration requires a MongoDB replica set');
  const database=connection.getDB(name);
  function sorted(value) {
    if(Array.isArray(value)) return value.map(sorted);
    if(value && typeof value==='object') return Object.fromEntries(Object.keys(value).sort().map(k=>[k,sorted(value[k])]));
    return value;
  }
  const expected=[];
  for(const file of files) {
    const payload=EJSON.parse(fs.readFileSync(file,'utf8'));
    for(const [collection,documents] of Object.entries(payload.collections || {})) {
      if(!['placement_questions','placement_test_sets'].includes(collection)) throw Error('Unexpected collection');
      for(const document of documents) expected.push({collection,document});
    }
  }
  if(expected.length!==321 || expected.filter(e=>e.collection==='placement_questions').length!==320 ||
     expected.filter(e=>e.collection==='placement_test_sets' && e.document._id==='PLT-V3-DEV-001-V2' && e.document.version===2).length!==1) throw Error('Unexpected Placement input identities/counts');
  function compare(requireAll) {
    let existing=0,missing=0;
    for(const {collection,document} of expected) {
      const stored=database.getCollection(collection).findOne({_id:document._id});
      if(!stored) { missing++; continue; }
      existing++;
      const projected=Object.fromEntries(Object.keys(document).map(key=>[key,stored[key]]));
      if(JSON.stringify(sorted(EJSON.serialize(projected)))!==JSON.stringify(sorted(EJSON.serialize(document)))) {
        throw Error('Existing content differs in '+collection+' / '+document._id+'; nothing will be replaced');
      }
    }
    if(requireAll && missing) throw Error('Post-import verification found missing documents');
    printjson({database:name,existing,missing,contentMismatches:0,attemptsTouched:false});
  }
  compare(false);
  process.env.MONGO_IMPORT_CONFLICT_POLICY='skip';
  load('/workspace/tools/mongo-json-importer/importer.mongosh.js');
  if(process.env.MONGO_IMPORT_APPLY==='1') compare(true);
})();
