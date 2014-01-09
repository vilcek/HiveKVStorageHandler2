/*
* This file is part of Hive KV Storage Handler
* Copyright 2012 Alexandre Vilcek (alexandre.vilcek@oracle.com)
*
*   Licensed under the Apache License, Version 2.0 (the "License");
*   you may not use this file except in compliance with the License.
*   You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
*   Unless required by applicable law or agreed to in writing, software
*   distributed under the License is distributed on an "AS IS" BASIS,
*   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*   See the License for the specific language governing permissions and
*   limitations under the License.
*/

package org.vilcek.hive.kv;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import oracle.kv.KVStore;
import oracle.kv.KVStoreConfig;
import oracle.kv.KVStoreFactory;
import oracle.kv.KeyValueVersion;
import oracle.kv.Value.Format;
import oracle.kv.avro.AvroCatalog;
import oracle.kv.avro.JsonAvroBinding;
import oracle.kv.avro.JsonRecord;
import oracle.kv.impl.api.KVStoreImpl;

import org.apache.avro.Schema;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.codehaus.jackson.JsonNode;

/**
 *
 * @author Alexandre Vilcek (alexandre.vilcek@oracle.com)
 */
public class KVHiveRecordReader implements RecordReader<LongWritable, MapWritable> {

    private KVStore kvstore;
    private Iterator<KeyValueVersion> iter;
    private KeyValueVersion current;
    private long cnt = 0;
    private KVHiveInputSplit inputSplit;
    private String majorKeyLabels;
    private String[] majorKeyLabelsArray;
    private JsonAvroBinding binding;

    private static final String SERIALIZED_NULL = NullWritable.get().toString();

    public KVHiveRecordReader(InputSplit split, JobConf conf) {
        inputSplit = (KVHiveInputSplit) split;
        majorKeyLabels = conf.get(ConfigProperties.KV_MAJOR_KEYS_MAPPING);
        majorKeyLabelsArray = majorKeyLabels.split(",");
        String kvStoreName = inputSplit.getKVStoreName();
        String[] kvHelperHosts = inputSplit.getKVHelperHosts();
        kvstore = KVStoreFactory.getStore(new KVStoreConfig(kvStoreName, kvHelperHosts));
        KVStoreImpl kvstoreImpl = (KVStoreImpl) kvstore;
        int singlePartId = inputSplit.getKVPart();
        iter = kvstoreImpl.partitionIterator(inputSplit.getDirection(),
            inputSplit.getBatchSize(),
            singlePartId,
            inputSplit.getParentKey(),
            inputSplit.getSubRange(),
            inputSplit.getDepth(),
            inputSplit.getConsistency(),
            inputSplit.getTimeout(),
            inputSplit.getTimeoutUnit());
        AvroCatalog avroCatalog = kvstoreImpl.getAvroCatalog();
        if (avroCatalog != null) {
            Map<String, Schema> currentSchemas = avroCatalog.getCurrentSchemas();
            if (currentSchemas != null && !currentSchemas.isEmpty()) {
                binding = avroCatalog.getJsonMultiBinding(currentSchemas);
            }
        }
    }

    @Override
    public boolean next(LongWritable k, MapWritable v) throws IOException {
        boolean ret = iter.hasNext();
        if (ret) {
            current = iter.next();
            k.set(cnt);
            v.clear();
            List<String> majorKeysList = current.getKey().getMajorPath();
            List<String> minorKeysList = current.getKey().getMinorPath();
            for (int i = 0; i < majorKeyLabelsArray.length; i++) {
                try {
                    String key = majorKeyLabelsArray[i];
                    String value = majorKeysList.get(i);
                    if (!value.equals(SERIALIZED_NULL)) {
                        v.put(new Text(key), new Text(value));
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                }
            }
            byte[] value = current.getValue().getValue();
            if (!value.toString().equals(SERIALIZED_NULL)) {
                if (Format.AVRO == current.getValue().getFormat() && binding != null) {
                    try {
                        JsonRecord object = binding.toObject(current.getValue());
                        JsonNode jsonNode = object.getJsonNode();
                        value = jsonNode.toString().getBytes("UTF8");
                    } catch (Throwable ignored) {
                    }
                }
                if (minorKeysList.isEmpty()) {
                    v.put(new Text("value"), new Text(value));
                } else {
                    for (int j = 0; j < minorKeysList.size(); j++) {
                        String key = minorKeysList.get(j);
                        v.put(new Text(key), new Text(value));
                    }
                }
            }
            cnt++;
            return ret;
        } else {
            return false;
        }
    }

    @Override
    public LongWritable createKey() {
        return new LongWritable();
    }

    @Override
    public MapWritable createValue() {
        return new MapWritable();
    }

    @Override
    public long getPos() throws IOException {
        return cnt;
    }

    @Override
    public void close() throws IOException {
        kvstore.close();
    }

    @Override
    public float getProgress() throws IOException {
        return inputSplit.getLength() > 0 ? cnt / (float) inputSplit.getLength() : 1.0f;
    }

}
