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
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import oracle.kv.*;
import oracle.kv.impl.rep.RepNodeStatus;
import oracle.kv.impl.rep.admin.RepNodeAdminAPI;
import oracle.kv.impl.topo.*;
import oracle.kv.impl.util.TopologyLocator;
import oracle.kv.impl.util.registry.RegistryUtils;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.io.HiveInputFormat;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.mapred.*;

/**
 * 
 * @author Alexandre Vilcek (alexandre.vilcek@oracle.com)
 */
public class KVHiveInputFormat extends HiveInputFormat<LongWritable, MapWritable> {

    private static String kvStoreName;
    private static String[] kvHelperHosts;
    private static Direction direction = Direction.FORWARD;
    private static int batchSize = 0;
    // private static Key parentKey = null;
    // private static KeyRange subRange = null;
    private static Depth depth = Depth.PARENT_AND_DESCENDANTS;
    private static Consistency consistency = null;
    private static long timeout = 0;
    private static TimeUnit timeoutUnit = null;

    @Override
    public RecordReader getRecordReader(InputSplit split, JobConf job, Reporter reporter) throws IOException {
        return new KVHiveRecordReader(split, job);
    }

    @Override
    public InputSplit[] getSplits(JobConf conf, int numSplits) throws IOException {
        String kvHostPort = conf.get(ConfigProperties.KV_HOST_PORT);
        Pattern pattern = Pattern.compile(",");
        kvHelperHosts = pattern.split(kvHostPort);
        kvStoreName = conf.get(ConfigProperties.KV_NAME);

        Topology topology = null;
        try {
            topology = TopologyLocator.get(kvHelperHosts, 0);
        } catch (KVStoreException KVSE) {
            KVSE.printStackTrace();
            return null;
        }
        RegistryUtils regUtils = new RegistryUtils(topology);
        PartitionMap partitionMap = topology.getPartitionMap();
        int nParts = partitionMap.getNPartitions();
        List<InputSplit> ret = new ArrayList<InputSplit>(nParts);
        for (int i = 1; i <= nParts; i++) {
            PartitionId partId = new PartitionId(i);
            RepGroupId repGroupId = topology.getRepGroupId(partId);
            RepGroup repGroup = topology.get(repGroupId);
            Collection<RepNode> repNodes = repGroup.getRepNodes();
            List<String> repNodeNames = new ArrayList<String>();
            List<String> repNodeNamesAndPorts = new ArrayList<String>();
            for (RepNode rn : repNodes) {
                RepNodeStatus rnStatus = null;
                try {
                    RepNodeAdminAPI rna = regUtils.getRepNodeAdmin(rn.getResourceId());
                    rnStatus = rna.ping();
                } catch (RemoteException re) {
                    System.err.println("Ping failed for " + rn.getResourceId() + ": " + re.getMessage());
                    re.printStackTrace();
                } catch (NotBoundException e) {
                    System.err.println("No RMI service for RN: " + rn.getResourceId() + " message: " + e.getMessage());
                }

                if (rnStatus == null) {
                    continue;
                }

                /*
                 * com.sleepycat.je.rep.ReplicatedEnvironment.State state = rnStatus.getReplicationState(); if (!state.isActive() ||
                 * (consistency == Consistency.ABSOLUTE && !state.isMaster())) { continue; }
                 */

                StorageNodeId snid = rn.getStorageNodeId();
                StorageNode sn = topology.get(snid);

                repNodeNames.add(sn.getHostname());
                repNodeNamesAndPorts.add(sn.getHostname() + ":" + sn.getRegistryPort());
            }
            Path[] tablePaths = FileInputFormat.getInputPaths(conf);

            Key parentKey = null;
            String parentKeyValue = conf.get("oracle.kv.parentKey");
            if (parentKeyValue != null && parentKeyValue.length() > 0) {
                parentKey = Key.fromString(parentKeyValue);
            }
            KeyRange subRange = null;
            String subRangeValue = conf.get("oracle.kv.subRange");
            if (subRangeValue != null && subRangeValue.length() > 0) {
                subRange = KeyRange.fromString(subRangeValue);
            }

            ret.add(new KVHiveInputSplit(tablePaths[0]).setKVHelperHosts(repNodeNamesAndPorts.toArray(new String[0]))
                .setKVStoreName(kvStoreName)
                .setKVPart(i)
                .setLocations(repNodeNames.toArray(new String[0]))
                .setDirection(direction)
                .setBatchSize(batchSize)
                .setParentKey(parentKey)
                .setSubRange(subRange)
                .setDepth(depth)
                .setConsistency(consistency)
                .setTimeout(timeout)
                .setTimeoutUnit(timeoutUnit));

        }

        return ret.toArray(new InputSplit[ret.size()]);
    }
}
