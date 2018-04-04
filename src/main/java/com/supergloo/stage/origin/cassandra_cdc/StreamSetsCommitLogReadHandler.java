package com.supergloo.stage.origin.cassandra_cdc;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.Stage;
import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.db.Clustering;
import org.apache.cassandra.db.ClusteringBound;
import org.apache.cassandra.db.ClusteringPrefix;
import org.apache.cassandra.db.Mutation;
import org.apache.cassandra.db.commitlog.CommitLogDescriptor;
import org.apache.cassandra.db.commitlog.CommitLogReadHandler;
import org.apache.cassandra.db.partitions.Partition;
import org.apache.cassandra.db.partitions.PartitionUpdate;
import org.apache.cassandra.db.rows.Cell;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.db.rows.Unfiltered;
import org.apache.cassandra.db.rows.UnfilteredRowIterator;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class StreamSetsCommitLogReadHandler  implements CommitLogReadHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomCommitLogReadHandler.class);

//    private final String keyspace;
//    private final String table;
//    private final String topic;

    private BatchMaker batchMaker;
    private Stage.Context context;

    public StreamSetsCommitLogReadHandler() {}

    public StreamSetsCommitLogReadHandler(Map<String, Object> configuration) {
//        keyspace = (String) YamlUtils.select(configuration, "cassandra.keyspace");
//        table = (String) YamlUtils.select(configuration, "cassandra.table");
//        topic = (String) YamlUtils.select(configuration, "kafka.topic");
//        producer = new KafkaProducer<>((Map) YamlUtils.select(configuration, "kafka.configuration"));
    }

    @Override
    public void handleMutation(Mutation mutation, int size, int entryLocation, CommitLogDescriptor descriptor) {
        LOGGER.debug("Handle mutation started...");
        for (PartitionUpdate partitionUpdate : mutation.getPartitionUpdates()) {
            process(partitionUpdate);
        }
        LOGGER.debug("Handle mutation finished...");
    }

    @Override
    public void handleUnrecoverableError(CommitLogReadException exception) throws IOException {
        LOGGER.debug("Handle unrecoverable error called.");
        throw new RuntimeException(exception);
    }

    @Override
    public boolean shouldSkipSegmentOnError(CommitLogReadException exception) throws IOException {
        LOGGER.debug("Should skip segment on error.");
        exception.printStackTrace();
        return true;
    }


    @SuppressWarnings("unchecked")
    private void process(Partition partition) {
        LOGGER.info("Process method started...");
//        if (!partition.metadata().ksName.equals(keyspace)) {
//            LOGGER.debug("Keyspace should be '{}' but is '{}'.", keyspace, partition.metadata().ksName);
//            return;
//        }
//        if (!partition.metadata().cfName.equals(table)) {
//            LOGGER.debug("Table should be '{} but is '{}'.", table, partition.metadata().cfName);
//            return;
//        }

        String key = getKey(partition);
//        JSONObject obj = new JSONObject();
//        obj.put("key", key);

        Record record = getContext().createRecord("some-id::" + key); // TODO this probably won't work
        Map<String, Field> map = new HashMap<>();

        if (partitionIsDeleted(partition)) {
            map.put("partitionDeleted", Field.create(true));
        } else {
            UnfilteredRowIterator it = partition.unfilteredIterator();
//            List<JSONObject> rows = new ArrayList<>();
            while (it.hasNext()) {
                Unfiltered un = it.next();
                if (un.isRow()) {
//                    JSONObject jsonRow = new JSONObject();
                    Clustering clustering = (Clustering) un.clustering();
                    String clusteringKey = clustering.toCQLString(partition.metadata());
//                    jsonRow.put("clusteringKey", clusteringKey);
                    map.put("clusteringKey", Field.create(clusteringKey));


                    Row row = partition.getRow(clustering);

                    if (rowIsDeleted(row)) {
                        map.put("rowDeleted", Field.create(true));
                    } else {
                        Iterator<Cell> cells = row.cells().iterator();
                        Iterator<ColumnDefinition> columns = row.columns().iterator();
//                        List<JSONObject> cellObjects = new ArrayList<>();

                        while (cells.hasNext() && columns.hasNext()) {
//                            JSONObject jsonCell = new JSONObject();
                            ColumnDefinition columnDef = columns.next();
                            Cell cell = cells.next();
                            jsonCell.put("name", columnDef.name.toString());
                            if (cell.isTombstone()) {
                                jsonCell.put("deleted", true);
                            } else {
                                String data = columnDef.type.getString(cell.value());
                                jsonCell.put("value", data);
                            }
                            cellObjects.add(jsonCell);
                        }
                        jsonRow.put("cells", cellObjects);
                    }
                    rows.add(jsonRow);

                } else if (un.isRangeTombstoneMarker()) {
                    map.put("rowRangeDeleted", Field.create(true));

                    ClusteringBound bound = (ClusteringBound) un.clustering();
                    List<JSONObject> bounds = new ArrayList<>();
                    for (int i = 0; i < bound.size(); i++) {
                        String clusteringBound = partition.metadata().comparator.subtype(i).getString(bound.get(i));
                        JSONObject boundObject = new JSONObject();
                        boundObject.put("clusteringKey", clusteringBound);
                        if (i == bound.size() - 1) {
                            if (bound.kind().isStart()) {
                                boundObject.put("inclusive",
                                        bound.kind() == ClusteringPrefix.Kind.INCL_START_BOUND ? true : false);
                            }
                            if (bound.kind().isEnd()) {
                                boundObject.put("inclusive",
                                        bound.kind() == ClusteringPrefix.Kind.INCL_END_BOUND ? true : false);
                            }
                        }
                        bounds.add(boundObject);
                    }
                    obj.put((bound.kind().isStart() ? "start" : "end"), bounds);
                }
            }
            obj.put("rows", rows);
        }
        LOGGER.info("Creating json value...");
        String value = obj.toJSONString();
        LOGGER.info("Created json value '{}'", value);

        record.set(Field.create(map));
        batchMaker.addRecord(record);
//            ++nextSourceOffset;
//            ++numRecords;

    }

    private boolean partitionIsDeleted(Partition partition) {
        return partition.partitionLevelDeletion().markedForDeleteAt() > Long.MIN_VALUE;
    }

    private boolean rowIsDeleted(Row row) {
        return row.deletion().time().markedForDeleteAt() > Long.MIN_VALUE;
    }

    private String getKey(Partition partition) {
        return partition.metadata().getKeyValidator().getString(partition.partitionKey().getKey());
    }

    protected void setBatchMaker(BatchMaker batchMaker) {
        this.batchMaker = batchMaker;
    }

    protected void setContext(Stage.Context context) {
        this.context = context;
    }

    private Stage.Context getContext() {
        return this.context;
    }
}