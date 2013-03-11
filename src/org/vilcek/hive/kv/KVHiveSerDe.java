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

import java.util.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.serde2.SerDe;
import org.apache.hadoop.hive.serde2.SerDeException;
import org.apache.hadoop.hive.serde2.SerDeStats;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;

/**
 *
 * @author Alexandre Vilcek (alexandre.vilcek@oracle.com)
 */
public class KVHiveSerDe implements SerDe {
    
    private String kvMajorKeysMapping, kvMinorKeysMapping;
    private String[] kvMajorKeysMappingArray, kvMinorKeysMappingArray;
    private List<String> majorMinorKeys, row;
    private int fieldCount;
    private StructObjectInspector objectInspector;

    @Override
    public void initialize(Configuration c, Properties prprts) throws SerDeException {
        kvMajorKeysMapping = prprts.getProperty(ConfigProperties.KV_MAJOR_KEYS_MAPPING);
        kvMajorKeysMappingArray = kvMajorKeysMapping.split(","); 
        kvMinorKeysMapping = prprts.getProperty(ConfigProperties.KV_MINOR_KEYS_MAPPING);
        if (kvMinorKeysMapping != null) {
            kvMinorKeysMappingArray = kvMinorKeysMapping.split(",");
        } else {
            kvMinorKeysMappingArray = new String[1];
            kvMinorKeysMappingArray[0] = "value";
        }
        fieldCount = kvMajorKeysMappingArray.length + kvMinorKeysMappingArray.length;
        majorMinorKeys = new ArrayList<String>(fieldCount);
        majorMinorKeys.addAll(Arrays.asList(kvMajorKeysMappingArray));
        majorMinorKeys.addAll(Arrays.asList(kvMinorKeysMappingArray));
        List<ObjectInspector> fieldOIs = new ArrayList<ObjectInspector>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            fieldOIs.add(PrimitiveObjectInspectorFactory.javaStringObjectInspector);
        }
        objectInspector = ObjectInspectorFactory.getStandardStructObjectInspector(majorMinorKeys, fieldOIs);
        row = new ArrayList<String>(fieldCount);
    }

    @Override
    public Object deserialize(Writable wrtbl) throws SerDeException {
        MapWritable input = (MapWritable) wrtbl;
        Text t = new Text();
        row.clear();
        for (int i = 0; i < fieldCount; i++) {
            t.set(majorMinorKeys.get(i));
            Writable value = input.get(t);
            if (value != null && !NullWritable.get().equals(value)) {
                row.add(value.toString());
            } else {
                row.add(null);
            }
        }
        return row;
    }

    @Override
    public ObjectInspector getObjectInspector() throws SerDeException {
        return objectInspector;
    }

    @Override
    public Class<? extends Writable> getSerializedClass() {
        return MapWritable.class;
    }

    @Override
    public SerDeStats getSerDeStats() {
        //throw new UnsupportedOperationException("Not supported yet.");
        return new SerDeStats();
    }

    @Override
    public Writable serialize(Object o, ObjectInspector oi) throws SerDeException {
        //throw new UnsupportedOperationException("Not supported yet.");
        return null;
    }
    
}
