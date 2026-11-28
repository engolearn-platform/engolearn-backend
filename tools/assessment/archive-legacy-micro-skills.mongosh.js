const database = process.env.PFC_CATALOG_DATABASE;
const mode = process.env.PFC_CATALOG_MODE || "plan";
if (!database || db.getName() !== database || ["admin", "local", "config"].includes(database)) {
    throw new Error("Select the Assessment test database and set PFC_CATALOG_DATABASE to its exact name.");
}
if (!["plan", "apply"].includes(mode)) {
    throw new Error("PFC_CATALOG_MODE must be plan or apply.");
}

const legacy = {
    _id: /^[RL][0-9]{2}$/,
    section: {$in: ["READING", "LISTENING"]},
    label: {$type: "string"},
    active: {$type: "bool"},
    code: {$exists: false},
    canonical_name: {$exists: false},
};
const source = "micro_skills";
const archive = "pfc_legacy_micro_skills";
const incompatible = {$or: [{code: {$not: {$type: "string"}}}, {canonical_name: {$not: {$type: "string"}}}]};

function inspect(target) {
    const catalog = target.getCollection(source);
    const documents = catalog.find(legacy).sort({_id: 1}).toArray();
    if (catalog.countDocuments(incompatible) !== documents.length) {
        throw new Error("Catalog contains unrecognized entries. Inspect them before migration.");
    }
    return documents;
}

if (mode === "plan") {
    const documents = inspect(db);
    printjson({database, mode, count: documents.length, ids: documents.map(document => document._id)});
} else {
    const session = db.getMongo().startSession();
    try {
        let archived;
        session.withTransaction(() => {
            const target = session.getDatabase(database);
            const documents = inspect(target);
            archived = documents.length;
            if (archived === 0) return;
            target.getCollection(archive).insertMany(documents);
            const removed = target.getCollection(source)
                .deleteMany({...legacy, _id: {$in: documents.map(document => document._id)}});
            if (removed.deletedCount !== archived) {
                throw new Error("Catalog changed during migration. No legacy rows were moved.");
            }
        }, {writeConcern: {w: "majority"}});
        printjson({database, mode, archived, archive});
    } finally {
        session.endSession();
    }
}
