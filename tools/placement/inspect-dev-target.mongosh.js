(function () {
  const target = 'engolearn_placement_dev_v3';
  const connection = new Mongo('mongodb://127.0.0.1:27017/?appName=PlacementDevReadOnlyInventory&serverSelectionTimeoutMS=2000');
  const admin = connection.getDB('admin');
  const databases = admin.runCommand({ listDatabases: 1, nameOnly: true });
  if (!databases.ok) throw Error('Database metadata query failed');
  const names = databases.databases.map(d => d.name).sort();
  const exists = names.includes(target);
  printjson({ mode: 'READ_ONLY', proposedTarget: target, targetApproved: false,
    host: '127.0.0.1:27017', version: admin.runCommand({ buildInfo: 1 }).version,
    replicaSet: admin.runCommand({ hello: 1 }).setName || null,
    existingDatabases: names, targetExists: exists });
  if (exists) {
    const database = connection.getDB(target);
    const collections = database.getCollectionNames();
    printjson({ targetCollections: collections });
    for (const name of ['placement_questions', 'placement_test_sets', 'placement_attempts']) {
      if (!collections.includes(name)) { print(name + ': ABSENT'); continue; }
      printjson({ collection: name, documents: database.getCollection(name).countDocuments({}),
        indexes: database.getCollection(name).getIndexes() });
    }
    if (collections.some(n => !['placement_questions', 'placement_test_sets', 'placement_attempts'].includes(n))) {
      print('BLOCKED: proposed target contains non-Placement collections; do not repurpose it');
    }
  }
  print('No database/index was created or modified. Proposal is not import approval.');
})();
