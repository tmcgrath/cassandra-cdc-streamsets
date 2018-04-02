# cassandra-cdc-streamsets
Cassandra CDC with StreamSets

## Quick Start

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

verify /tmp/cdc_raw has *.log file

SDC pipeline ... provide json to import

Notes:
I needed to update sdc-security file to allow reads
I need to figure out how to not include all the jars coming along with Cassandra


// User stage libraries code base:
grant codebase "file://${sdc.dist.dir}/user-libs/-" {
  permission java.util.PropertyPermission "*", "read";
  permission java.lang.RuntimePermission "accessDeclaredMembers";
  permission java.lang.reflect.ReflectPermission "suppressAccessChecks";
  permission java.io.FilePermission "${sdc.dist.dir}/user-libs/streamsets-datacollector-dev-lib/lib/*", "read";
  permission java.io.FilePermission "/Users/toddmcgrath/dev/cdc_raw", "read";
  permission java.io.FilePermission "/Users/toddmcgrath/dev/cdc_raw/*", "read";
};




Or is this a quickstart?  Maybe Dockerized would be easier


## More advanced

* Docker compose -> xeal with replication factor -> Need multi-node Cassandra cluster, SDC running on each and dedupe pipeline

* Gatling for data load simulation




