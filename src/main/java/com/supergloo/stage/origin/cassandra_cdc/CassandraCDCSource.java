/*
 * Copyright 2017 StreamSets Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.supergloo.stage.origin.cassandra_cdc;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.base.BaseSource;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.db.commitlog.CommitLogReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

/**
 * TBD
 */
public abstract class CassandraCDCSource extends BaseSource {

  private final static Logger LOG = LoggerFactory.getLogger(CassandraCDCSource.class);

  private static WatchService watcher;
  private static Path dir;
  private static WatchKey key;
  private static CommitLogReader commitLogReader;
  private static StreamSetsCommitLogReadHandler commitLogReadHander;

  public abstract String getConfig();
  public abstract String getStorageDir();

  @Override
  protected List<ConfigIssue> init() {
    // Validate configuration values and open any required resources.
    List<ConfigIssue> issues = super.init();

    LOG.debug("Initialized Cassandra CDC with config: {}", getConfig());

    if (getConfig().equals("invalidValue")) { // TODO
      issues.add(
          getContext().createConfigIssue(
              Groups.CASSANDRA.name(), "config", Errors.SAMPLE_00, "Here's what's wrong..."
          )
      );
    }

    if (getStorageDir().equals("invalidValue")) { // TODO
      issues.add(
              getContext().createConfigIssue(
                      Groups.CASSANDRA.name(), "config", Errors.SAMPLE_00, "Here's what's wrong..."
              )
      );
    }

    // I bet if I set `storagedir` then I wouldn't have to make all the cassandra.yaml mods like to point specifically
    // to hints, saved_caches etc.
    // https://github.com/PaytmLabs/cassandra/blob/master/src/java/org/apache/cassandra/config/DatabaseDescriptor.java#L516

    // TODO verify config and storage AND... the config contains required cdc config settings such as raw dir
    // TODO - figure what's going on here

    /*
    java -jar -Dcassandra.config=file://<path_to_cassandra-cdc>/config/cassandra-1-cdc-tmp.yaml
    -Dcassandra.storagedir=file:///tmp/cdc/cassandra-1/
    <path_to_cassandra-cdc>/target/cassandra-cdc-0.0.1-SNAPSHOT.jar <path_to_cassandra-cdc>/config/reader-1.yml
     */
    // TM -one
    // Shit ton of mods to cassandra.yaml beyond just CDC
//    System.setProperty("cassandra.config", "file:///Users/toddmcgrath/dev/apache-cassandra-3.11.2/conf/cassandra.yaml");
//    System.setProperty("cassandra.storage", "file:///Users/toddmcgrath/dev/apache-cassandra-3.11.2/data/");

    System.setProperty("cassandra.config", String.format("file://%s", getConfig()));
    System.setProperty("cassandra.storage", String.format("file://%s", getStorageDir()));


//    this.dir = Paths.get((String) YamlUtils.select(configuration, "cassandra.cdc_raw_directory"));
    // TODO read following from cassandra.yaml
    // since it's required above
    this.dir = Paths.get("/tmp/cdc_raw/"); // TODO - throw error if cdc_raw and others not set


    // TODO - if no issues then do the following
    try {

      watcher = FileSystems.getDefault().newWatchService();
      key = dir.register(watcher, ENTRY_CREATE);

    } catch (IOException e) {
      LOG.error(e.getMessage());
    }

    commitLogReader = new CommitLogReader();
    commitLogReadHander = new StreamSetsCommitLogReadHandler();

    DatabaseDescriptor.toolInitialization();
    Schema.instance.loadFromDisk(false);

    // If issues is not empty, the UI will inform the user of each configuration issue in the list.
    return issues;
  }

  /** {@inheritDoc} */
  @Override
  public void destroy() {
    super.destroy();
  }

  /** {@inheritDoc} */
  @Override
  public String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {

    // Offsets can vary depending on the data source. Here we use an integer as an example only.
    long nextSourceOffset = 0;
    if (lastSourceOffset != null) {
      nextSourceOffset = Long.parseLong(lastSourceOffset);
    }

    int numRecords = 0;

    try {

      WatchKey aKey = watcher.take();
      if (!key.equals(aKey)) {
        LOG.error("WatchKey not recognized.");
      }

    } catch (InterruptedException e) {
      LOG.error(e.getMessage());
    }

    for (WatchEvent<?> event : key.pollEvents()) {
      WatchEvent.Kind<?> kind = event.kind();
      if (kind != ENTRY_CREATE) {
        continue;
      }

      // Context for directory entry event is the file name of entry
      WatchEvent<Path> ev = (WatchEvent<Path>) event;
      Path relativePath = ev.context();
      Path absolutePath = dir.resolve(relativePath);

      try {
        commitLogReadHander.setBatchMaker(batchMaker);
        commitLogReadHander.setContext(getContext());
        processCommitLogSegment(absolutePath);
        Files.delete(absolutePath);

      } catch (IOException e) {
        LOG.error(e.getMessage());
      }

      // print out event
      LOG.error("TODD");
      LOG.error("{}: {}", event.kind().name(), absolutePath);
    }
    key.reset();

    // Create records and add to batch. Records must have a string id. This can include the source offset
    // or other metadata to help uniquely identify the record itself.
    while (numRecords < maxBatchSize) {


      Record record = getContext().createRecord("some-id::" + nextSourceOffset);
      Map<String, Field> map = new HashMap<>();
      map.put("fieldName", Field.create("Some Value"));
      record.set(Field.create(map));
      batchMaker.addRecord(record);
      ++nextSourceOffset;
      ++numRecords;
    }

    return String.valueOf(nextSourceOffset);
  }

  private void processCommitLogSegment(Path path) throws IOException {
    LOG.warn("Processing commitlog segment...");

    // TODO possible call `readCommitLogSegment method with signature that includes mutationLimit and offset info
    commitLogReader.readCommitLogSegment(commitLogReadHander, path.toFile(), false);
    LOG.warn("Commitlog segment processed.");
  }
}
