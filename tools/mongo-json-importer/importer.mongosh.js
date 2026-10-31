/*
 * Generic MongoDB JSON importer. Domain-agnostic; no application schema is assumed.
 * Each input file must contain a top-level collections object whose values are arrays.
 * MongoDB Extended JSON is parsed through EJSON.parse.
 */
(function () {
  "use strict";
  var fs = require("fs");
  function fail(message) { throw new Error(message); }
  function isObject(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value) && !(value instanceof Date);
  }
  function idKey(value) { return EJSON.stringify(value, null, 0, { relaxed: false }); }
  function printReport(report) { print("\nIMPORT REPORT"); printjson(report); }

  function compileFieldPatterns(raw) {
    if (!raw || raw.trim().toLowerCase() === "none") return [];
    return raw.split(",").map(function (pattern) {
      pattern = pattern.trim();
      if (!pattern) return null;
      if (!/^[A-Za-z0-9_*]+$/.test(pattern)) {
        fail("Invalid ObjectId field pattern '" + pattern + "'. Use letters, numbers, underscores, and * only.");
      }
      return new RegExp("^" + pattern.replace(/\*/g, ".*") + "$", "i");
    }).filter(Boolean);
  }

  function normalizeObjectIdFields(value, fieldName, patterns, stats) {
    var matches = fieldName && patterns.some(function (pattern) { return pattern.test(fieldName); });
    if (matches && typeof value === "string" && /^[0-9a-fA-F]{24}$/.test(value)) {
      stats.converted++;
      return ObjectId(value);
    }
    if (Array.isArray(value)) {
      return value.map(function (item) {
        if (matches && typeof item === "string" && /^[0-9a-fA-F]{24}$/.test(item)) {
          stats.converted++;
          return ObjectId(item);
        }
        return normalizeObjectIdFields(item, null, patterns, stats);
      });
    }
    if (isObject(value)) {
      Object.keys(value).forEach(function (key) {
        value[key] = normalizeObjectIdFields(value[key], key, patterns, stats);
      });
    }
    return value;
  }

  function loadDatasets(files, patterns, stats) {
    var groups = new Map();
    var seen = new Map();
    var total = 0;
    files.forEach(function (filePath) {
      var parsed;
      try { parsed = EJSON.parse(fs.readFileSync(filePath, "utf8")); }
      catch (error) { fail("Cannot parse '" + filePath + "': " + error.message); }
      if (!isObject(parsed) || !isObject(parsed.collections)) {
        fail("'" + filePath + "' must contain a top-level collections object.");
      }
      parsed = normalizeObjectIdFields(parsed, null, patterns, stats);
      Object.keys(parsed.collections).forEach(function (name) {
        if (!name || name.indexOf("\0") >= 0 || name.indexOf("$") >= 0 || /^system\./i.test(name)) {
          fail("Unsupported collection name '" + name + "' in " + filePath + ".");
        }
        var documents = parsed.collections[name];
        if (!Array.isArray(documents)) fail("Collection '" + name + "' in " + filePath + " must be an array.");
        if (!groups.has(name)) groups.set(name, []);
        documents.forEach(function (document, index) {
          if (!isObject(document)) fail(name + "[" + index + "] in " + filePath + " must be an object.");
          if (typeof document._id === "undefined" || document._id === null) {
            fail("Missing _id at " + filePath + " -> " + name + "[" + index + "].");
          }
          var key = name + "::" + idKey(document._id);
          if (seen.has(key)) fail("Duplicate _id in input for collection '" + name + "': " + idKey(document._id));
          seen.set(key, filePath);
          groups.get(name).push({ file: filePath, document: document });
          total++;
        });
      });
    });
    if (total === 0) fail("Input files contain no documents.");
    return { groups: groups, total: total };
  }

  function findExisting(collection, entries, batchSize) {
    var existing = new Map();
    for (var offset = 0; offset < entries.length; offset += batchSize) {
      var chunk = entries.slice(offset, offset + batchSize);
      var ids = chunk.map(function (entry) { return entry.document._id; });
      collection.find({ _id: { $in: ids } }).forEach(function (doc) { existing.set(idKey(doc._id), doc); });
    }
    return existing;
  }

  function main() {
    var uri = process.env.MONGO_IMPORT_URI;
    var databaseName = process.env.MONGO_IMPORT_DATABASE;
    var policy = (process.env.MONGO_IMPORT_CONFLICT_POLICY || "skip").toLowerCase();
    var batchSize = Number(process.env.MONGO_IMPORT_BATCH_SIZE || "500");
    var continueOnError = process.env.MONGO_IMPORT_CONTINUE_ON_ERROR === "true";
    var apply = process.env.MONGO_IMPORT_APPLY === "1";
    var objectIdFieldSetting = process.env.MONGO_IMPORT_OBJECT_ID_FIELDS || "_id,*_id,*_ids";
    var objectIdPatterns = compileFieldPatterns(objectIdFieldSetting);
    var objectIdStats = { converted: 0 };
    if (!uri || !databaseName) fail("MongoDB URI or database is missing.");
    if (["skip", "fail", "replace"].indexOf(policy) < 0) fail("Conflict policy must be skip, fail, or replace.");
    if (!Number.isInteger(batchSize) || batchSize < 1) fail("Batch size must be a positive integer.");
    if (/^(admin|config|local)$/i.test(databaseName) || /(^|[_-])prod(uction)?($|[_-])/i.test(databaseName)) {
      fail("Refusing system or production-looking database: " + databaseName);
    }

    var files = JSON.parse(process.env.MONGO_IMPORT_FILES_JSON || "[]");
    if (!Array.isArray(files) || files.length === 0) fail("No input files were provided.");
    var loaded = loadDatasets(files, objectIdPatterns, objectIdStats);
    var db = new Mongo(uri).getDB(databaseName);

    // Complete read-only preflight for every file and collection before the first write.
    var plans = [];
    var conflictsTotal = 0;
    loaded.groups.forEach(function (entries, name) {
      var existing = findExisting(db.getCollection(name), entries, batchSize);
      var conflicts = entries.filter(function (entry) { return existing.has(idKey(entry.document._id)); }).length;
      conflictsTotal += conflicts;
      plans.push({ name: name, entries: entries, existing: existing, conflicts: conflicts });
    });

    var report = {
      mode: apply ? "IMPORT" : "DRY_RUN",
      database: databaseName,
      files: files,
      conflictPolicy: policy,
      batchSize: batchSize,
      objectIdFields: objectIdFieldSetting,
      convertedObjectIdValues: objectIdStats.converted,
      totals: {
        collections: plans.length,
        documents: loaded.total,
        alreadyExisting: conflictsTotal,
        newDocuments: loaded.total - conflictsTotal,
        wouldSkip: policy === "skip" ? conflictsTotal : 0,
        wouldReplace: policy === "replace" ? conflictsTotal : 0
      },
      collections: plans.map(function (plan) {
        return { name: plan.name, documents: plan.entries.length, alreadyExisting: plan.conflicts };
      }),
      writeResults: [],
      errors: []
    };

    if (policy === "fail" && conflictsTotal > 0) {
      printReport(report);
      fail("Conflict policy is fail and " + conflictsTotal + " document(s) already exist. No data was written.");
    }
    if (!apply) {
      printReport(report);
      print("Preflight complete. No data was written.");
      return;
    }

    plans.forEach(function (plan) {
      var collection = db.getCollection(plan.name);
      for (var offset = 0; offset < plan.entries.length; offset += batchSize) {
        var chunk = plan.entries.slice(offset, offset + batchSize);
        var operations = [];
        chunk.forEach(function (entry) {
          var exists = plan.existing.has(idKey(entry.document._id));
          if (policy === "skip" && exists) return;
          if (policy === "replace") {
            operations.push({ replaceOne: {
              filter: { _id: entry.document._id },
              replacement: entry.document,
              upsert: true
            }});
          } else {
            operations.push({ insertOne: { document: entry.document } });
          }
        });
        if (operations.length === 0) continue;
        try {
          var result = collection.bulkWrite(operations, { ordered: false });
          var succeeded = policy === "replace"
            ? (result.matchedCount || 0) + (result.upsertedCount || 0)
            : (result.insertedCount || 0);
          report.writeResults.push({
            collection: plan.name,
            batch: Math.floor(offset / batchSize) + 1,
            attempted: operations.length,
            succeeded: succeeded,
            failed: operations.length - succeeded
          });
        } catch (error) {
          var writeErrors = Array.isArray(error.writeErrors) ? error.writeErrors : [];
          var failed = writeErrors.length > 0 ? writeErrors.length : operations.length;
          report.writeResults.push({
            collection: plan.name,
            batch: Math.floor(offset / batchSize) + 1,
            attempted: operations.length,
            succeeded: Math.max(0, operations.length - failed),
            failed: failed,
            error: error.message
          });
          report.errors.push({
            collection: plan.name,
            batch: Math.floor(offset / batchSize) + 1,
            message: error.message,
            writeErrors: writeErrors.map(function (item) {
              return { index: item.index, code: item.code, message: item.errmsg || item.message };
            })
          });
          if (!continueOnError) {
            printReport(report);
            fail("Write failed. Earlier successful batches are not rolled back. " + error.message);
          }
        }
      }
    });
    report.status = report.errors.length === 0 ? "completed" : "completed_with_errors";
    printReport(report);
    if (report.errors.length > 0) fail("Import completed with errors; successful writes were not rolled back.");
  }

  try { main(); }
  catch (error) { print("\nERROR: " + error.message); quit(1); }
})();
