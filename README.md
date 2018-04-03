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

verify /tmp/cdc_raw has CDC *.log file

SDC pipeline ... provide json to import

Notes:
1) I need to figure out how to not include all the jars coming along with Cassandra


2) I needed to update sdc-security file to allow all kinds of stuff

// User stage libraries code base:
grant codebase "file://${sdc.dist.dir}/user-libs/-" {
  permission java.util.PropertyPermission "*", "read";
  permission java.lang.RuntimePermission "accessDeclaredMembers";
  permission java.lang.reflect.ReflectPermission "suppressAccessChecks";
  permission java.io.FilePermission "${sdc.dist.dir}/user-libs/streamsets-datacollector-dev-lib/lib/*", "read";
  permission java.io.FilePermission "/tmp/cdc_raw", "read";
  permission java.io.FilePermission "/tmp/cdc_raw/*", "read";
  permission java.io.FilePermission "/Users/toddmcgrath/dev/apache-cassandra-3.11.2/conf/", "read";
  permission java.io.FilePermission "/Users/toddmcgrath/dev/apache-cassandra-3.11.2/conf/*", "read";
  permission java.lang.RuntimePermission "getClassLoader"; // for net.jpountz.lz4.LZ4Factory.fastestInstance(LZ4Factory.java:135) part of CommitLogReader
  permission java.util.PropertyPermission "cassandra.config", "read,write";
  permission java.util.PropertyPermission "cassandra.storage", "read,write";
  permission java.lang.RuntimePermission "accessClassInPackage.sun.misc";
  permission java.lang.RuntimePermission "getFileStoreAttributes";
  permission java.io.FilePermission "/Users/toddmcgrath/dev/apache-cassandra-3.11.2/data/commitlog", "read";
  permission javax.management.MBeanServerPermission "createMBeanServer";
  permission java.io.FilePermission "/Users/toddmcgrath/dev/apache-cassandra-3.11.2/data", "read";
  // fuck it - TODO revisit
  permission java.security.AllPermission;
};

3) I needed to update Cassandra YAML -- //TODO revisit though after setting storage dir

# the configured compaction strategy.
# If not set, the default directory is $CASSANDRA_HOME/data/data.
# data_file_directories:
#     - /var/lib/cassandra/data
data_file_directories:
    - /Users/toddmcgrath/dev/apache-cassandra-3.11.2/data

# commit log.  when running on magnetic HDD, this should be a
# separate spindle than the data directories.
# If not set, the default directory is $CASSANDRA_HOME/data/commitlog.
# commitlog_directory: /var/lib/cassandra/commitlog
commitlog_directory: /Users/toddmcgrath/dev/apache-cassandra-3.11.2/data/commitlog



Or is this a quickstart?  Maybe Dockerized would be easier


## More advanced

* Docker compose -> xeal with replication factor -> Need multi-node Cassandra cluster, SDC running on each and dedupe pipeline

* Gatling for data load simulation




