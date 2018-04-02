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
  private static CustomCommitLogReadHandler commitLogReadHander;

  /**
   * Gives access to the UI configuration of the stage provided by the {@link CassandraCDCDSource} class.
   */
  public abstract String getConfig();

  @Override
  protected List<ConfigIssue> init() {
    // Validate configuration values and open any required resources.
    List<ConfigIssue> issues = super.init();

    LOG.info("Todd");
    LOG.info("Initialized with config: {}", getConfig());

    if (getConfig().equals("invalidValue")) {
      issues.add(
          getContext().createConfigIssue(
              Groups.SAMPLE.name(), "config", Errors.SAMPLE_00, "Here's what's wrong..."
          )
      );
    }

//    this.dir = Paths.get((String) YamlUtils.select(configuration, "cassandra.cdc_raw_directory"));
    // TODO get this SDC config
    this.dir = Paths.get("/Users/toddmcgrath/dev/cdc_raw/");

    try {

      watcher = FileSystems.getDefault().newWatchService();
      key = dir.register(watcher, ENTRY_CREATE);

    } catch (IOException e) {
      LOG.error(e.getMessage());
    }

    commitLogReader = new CommitLogReader();
    commitLogReadHander = new CustomCommitLogReadHandler();

    // TODO - figure what's going on here
    DatabaseDescriptor.toolInitialization();
//    Schema.instance.loadFromDisk(false);

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
//      WatchKey aKey = watcher.poll(); // TODO - pass in config
      if (!key.equals(aKey)) {
        LOG.error("WatchKey not recognized.");
//        continue;
      }
//
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
        processCommitLogSegment(absolutePath);
//      Files.delete(absolutePath);

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
    commitLogReader.readCommitLogSegment(commitLogReadHander, path.toFile(), false);
    LOG.warn("Commitlog segment processed.");
  }
}
