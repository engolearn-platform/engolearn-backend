// One-time, resumable rename. Run against the intended database while application writers are stopped.
// Prints counts and marker counts only; never prints account documents or credentials.
const legacyName = "accounts";
const targetName = "users";
const oldType = "fit.iuh.engolearn.user.model.Account";
const newType = "fit.iuh.engolearn.user.model.User";
const names = db.getCollectionNames();
const hasLegacy = names.includes(legacyName);
const hasTarget = names.includes(targetName);
if (hasLegacy && hasTarget) {
  throw new Error("Both accounts and users exist; reconcile manually before migration");
}
if (!hasLegacy && !hasTarget) {
  print(JSON.stringify({status: "no_collection", accounts: 0, users: 0}));
  quit(0);
}
if (hasLegacy) {
  const legacy = db.getCollection(legacyName);
  if (legacy.countDocuments({_class: {$exists: true, $nin: [oldType, newType]}}) !== 0) {
    throw new Error("Unexpected accounts type marker; inspect before migration");
  }
  if (!legacy.getIndexes().some(index => index.unique === true && index.key.email === 1)) {
    throw new Error("accounts unique email index is missing; inspect before migration");
  }
  db.getCollection(legacyName).renameCollection(targetName, false);
}
const users = db.getCollection(targetName);
const before = users.countDocuments({});
const legacyMarkers = users.countDocuments({_class: oldType});
const changed = users.updateMany({_class: oldType}, {$set: {_class: newType}});
const remaining = users.countDocuments({_class: oldType});
const wrongType = users.countDocuments({_class: {$exists: true, $nin: [newType]}});
const uniqueEmail = users.getIndexes().some(index => index.unique === true && index.key.email === 1);
if (remaining !== 0 || wrongType !== 0 || before !== users.countDocuments({}) || !uniqueEmail) {
  throw new Error("Post-migration verification failed; inspect users before starting the application");
}
print(JSON.stringify({status: hasLegacy ? "renamed" : "already_migrated",
  users: before, oldTypeMarkers: legacyMarkers, rewritten: changed.modifiedCount,
  remainingOldTypeMarkers: remaining, uniqueEmailIndex: uniqueEmail}));
