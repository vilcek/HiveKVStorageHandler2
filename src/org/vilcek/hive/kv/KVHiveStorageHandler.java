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

import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.HiveMetaHook;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.metadata.HiveStorageHandler;
import org.apache.hadoop.hive.ql.plan.TableDesc;
import org.apache.hadoop.hive.ql.security.authorization.DefaultHiveAuthorizationProvider;
import org.apache.hadoop.hive.ql.security.authorization.HiveAuthorizationProvider;
import org.apache.hadoop.hive.serde2.SerDe;

/**
 *
 * @author Alexandre Vilcek (alexandre.vilcek@oracle.com)
 */
public class KVHiveStorageHandler implements HiveStorageHandler {
    
    private Configuration conf = null;

    @Override
    public Class<? extends org.apache.hadoop.mapred.InputFormat> getInputFormatClass() {
        return KVHiveInputFormat.class;
    }

    @Override
    public Class<? extends org.apache.hadoop.mapred.OutputFormat> getOutputFormatClass() {
        return KVHiveOutputFormat.class;
    }

    @Override
    public Class<? extends SerDe> getSerDeClass() {
        return KVHiveSerDe.class;
    }

    @Override
    public HiveMetaHook getMetaHook() {
        //not supported yet
        return null;
    }

    @Override
    public void configureTableJobProperties(TableDesc td, Map<String, String> map) {
        Properties p = td.getProperties();
        Enumeration<Object> keys = p.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = p.getProperty(key);
            map.put(key, value);
        }
        
    }

    @Override
    public void setConf(Configuration c) {
        this.conf = c;
    }

    @Override
    public Configuration getConf() {
        return conf;
    }

    @Override
    public HiveAuthorizationProvider getAuthorizationProvider() throws HiveException {
        //throw new UnsupportedOperationException("Not supported yet.");
        return new DefaultHiveAuthorizationProvider();
    }

    @Override
    public void configureInputJobProperties(TableDesc td, Map<String, String> map) {
        Properties p = td.getProperties();
        Enumeration<Object> keys = p.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = p.getProperty(key);
            map.put(key, value);
        }
    }

    @Override
    public void configureOutputJobProperties(TableDesc td, Map<String, String> map) {
        Properties p = td.getProperties();
        Enumeration<Object> keys = p.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = p.getProperty(key);
            map.put(key, value);
        }
    }
}
