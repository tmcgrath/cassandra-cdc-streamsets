# cassandra-cdc-streamsets
Cassandra CDC with StreamSets

Rough outline for as-simple-as-possible CDC (no rep factor consideration)

Download Cassandra
vi conf/cassandra.yaml
cdc_enabled: true
cdc_raw_directory: /var/lib/cassandra/cdc_raw <- set to wherever; e.g. /tmp/cdc_raw

TODO - lower the memtable, so `nodetool flush` will work instead of drain... see below

mkdir /tmp/cdc_raw

Start Cassandra bin/cassandra

cd setup/ dir
cqlsh < simple-schema.cql
cqlsh < load-users.cql 
nodetool drain

verify /tmp/cdc_raw



