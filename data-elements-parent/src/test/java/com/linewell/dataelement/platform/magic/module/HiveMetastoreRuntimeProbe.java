package com.linewell.dataelement.platform.magic.module;

import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.ThriftHiveMetastore;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;

/** Runs with production libraries only, also through the executable Boot JAR's class loader. */
public final class HiveMetastoreRuntimeProbe {
    public static void main(String[] args) throws Exception {
        verify(Path.of(args[0]), Integer.parseInt(args[1]));
    }

    static void verify(Path directory, int count) throws Exception {
        // Resolving the real client catches missing classes in method signatures before any RPC.
        HiveMetaStoreClient.class.getDeclaredMethods();
        Class.forName("org.apache.hadoop.hive.common.ValidWriteIdList");
        Files.createDirectories(directory);
        AtomicInteger requests = new AtomicInteger();
        List<String> buckets = new ArrayList<>();
        for (int i = 0; i < count; i++) buckets.add("p_" + i);
        var handler = (ThriftHiveMetastore.Iface) Proxy.newProxyInstance(
                HiveMetastoreRuntimeProbe.class.getClassLoader(), new Class<?>[]{ThriftHiveMetastore.Iface.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("set_ugi")) return List.of();
                    if (method.getName().equals("shutdown")) return null;
                    if (!method.getName().equals("get_partitions_by_names"))
                        throw new UnsupportedOperationException("Unexpected Metastore request: " + method.getName());
                    if (!String.valueOf(arguments[0]).endsWith("warehouse") || !"events".equals(arguments[1]))
                        throw new IllegalStateException("Unexpected target identity");
                    List<String> expected = buckets.stream().map(bucket -> "ods_partition=" + bucket).toList();
                    if (!expected.equals(arguments[2])) throw new IllegalStateException("Incomplete batch request");
                    requests.incrementAndGet();
                    List<Partition> partitions = new ArrayList<>();
                    for (String bucket : buckets) {
                        Partition partition = new Partition();
                        partition.setDbName("warehouse");
                        partition.setTableName("events");
                        partition.setValues(List.of(bucket));
                        StorageDescriptor storage = new StorageDescriptor();
                        storage.setLocation("hdfs://fixture/custom/" + bucket);
                        partition.setSd(storage);
                        partitions.add(partition);
                    }
                    return partitions;
                });
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            TServerSocket transport = new TServerSocket(socket);
            TServer server = new TSimpleServer(new TServer.Args(transport)
                    .protocolFactory(new TBinaryProtocol.Factory())
                    .processor(new ThriftHiveMetastore.Processor<>(handler)));
            Thread thread = new Thread(server::serve, "validation-hive-metastore");
            thread.setDaemon(true);
            thread.start();
            try {
                Files.writeString(directory.resolve("hive-site.xml"), """
                        <configuration>
                          <property><name>hive.metastore.uris</name><value>thrift://127.0.0.1:%d</value></property>
                          <property><name>hive.metastore.sasl.enabled</name><value>false</value></property>
                          <property><name>hive.metastore.execute.setugi</name><value>false</value></property>
                        </configuration>
                        """.formatted(socket.getLocalPort()));
                HiveModule hive = new HiveModule();
                var configDirectory = HiveModule.class.getDeclaredField("hiveConfDir");
                configDirectory.setAccessible(true);
                configDirectory.set(hive, directory.toString());
                var locations = hive.partitionLocations("warehouse", "events", buckets);
                if (locations.size() != count || requests.get() != 1)
                    throw new IllegalStateException("Expected one complete Metastore batch for " + count + " partitions");
                for (String bucket : buckets) if (!("hdfs://fixture/custom/" + bucket).equals(locations.get(bucket)))
                    throw new IllegalStateException("Custom partition Location was lost");
                System.out.println("PASS real HiveModule/HiveMetaStoreClient RPC: " + count + " partitions, one batch");
            } finally {
                server.stop();
                transport.close();
                thread.join(2000);
            }
        }
    }
}
