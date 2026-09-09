#!/bin/bash
set -e

mongosh -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin <<EOF
use $APP_DB;

db.createUser({
  user: '$APP_USER',
  pwd: '$APP_PASSWORD',
  roles: [
    {
      role: 'readWrite',
      db: '$APP_DB'
    }
  ]
});

db.createCollection('init_marker');
db.init_marker.insertOne({ initializedAt: new Date(), status: "READY" });
EOF