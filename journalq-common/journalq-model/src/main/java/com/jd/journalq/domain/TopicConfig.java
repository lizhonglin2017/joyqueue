package com.jd.journalq.domain;

import com.google.common.collect.Maps;

import java.io.Serializable;
import java.util.*;

/**
 * @author lixiaobin6
 */
public class TopicConfig extends Topic implements Serializable {

    /**
     * Topic所有partitionGroup信息
     * <broker,id-PartitionGroup>
     * -1表示还没有leader的broker的partitionGroup;
     */
    private Map<Short, PartitionGroup> partitionGroupMap = new HashMap<>();

    private Map<Integer,PartitionGroup> partitionGroups;

    /**
     * 主题配置转换
     *
     * @param topic 主题
     * @return 主题配置
     */
    public static TopicConfig toTopicConfig(Topic topic) {
        if (topic == null) {
            return null;
        }
        TopicConfig config = new TopicConfig();
        //设置主题
        config.setName(topic.getName());
        config.setPartitions(topic.getPartitions());
        config.setType(topic.getType());
        config.setPriorityPartitions(topic.getPriorityPartitions());
        return config;
    }

    public Set<Short> getPriorityPartitions() {
        return priorityPartitions;
    }

    public void setPriorityPartitions(Set<Short> priorityPartitions) {
        this.priorityPartitions = priorityPartitions;
    }



    public short getPartitions() {
        return partitions;
    }

    public void setPartitions(short partitions) {
        this.partitions = partitions;
    }


    public Map<Integer,PartitionGroup> getPartitionGroups() {
        return partitionGroups;
    }

    public List<PartitionGroup> fetchPartitionGroupByBrokerId(int brokerId) {
        List<PartitionGroup> list = new ArrayList<>();
        for(PartitionGroup group : partitionGroups.values()) {
            if (group.getLeader().equals(brokerId)) list.add(group);
        }
        return list;
    }

    public List<PartitionGroup> fetchTopicPartitionGroupsByBrokerId(int brokerId) {
        List<PartitionGroup> list = new ArrayList<>();
        for(PartitionGroup group : partitionGroups.values()) {
            if (group.getReplicas().contains(brokerId)) list.add(group);
        }
        return list;
    }

    public void setPartitionGroups(Map<Integer,PartitionGroup> partitionGroups) {
        this.partitionGroups = partitionGroups;
        this.partitionGroupMap = buildPartitionGroupMap(partitionGroups);
    }

    private Map<Short, PartitionGroup> buildPartitionGroupMap(Map<Integer,PartitionGroup> partitionGroups) {
        Map<Short, PartitionGroup> result = Maps.newHashMap();
        for (PartitionGroup partitionGroup : partitionGroups.values()) {
            for (Short partition : partitionGroup.getPartitions()) {
                result.put(partition, partitionGroup);
            }
        }
        return result;
    }

    public PartitionGroup fetchPartitionGroupByPartition(short partition) {
        return partitionGroupMap.get(partition);
    }

    public PartitionGroup fetchPartitionGroupByGroup(int group) {
        return partitionGroups.get(group);
    }

    public Broker fetchBrokerByPartition(short partition) {
        PartitionGroup group = fetchPartitionGroupByPartition(partition);
        if (null != group) return group.getBrokers().get(group.getLeader());
        return null;
    }

    public List<Partition> fetchPartitionMetadata() {
        List<Partition> metadataList = new ArrayList<>();
            for (PartitionGroup group : partitionGroups.values()) {
                for (Short partition : group.getPartitions()) {
                    Set<Broker> irs = new HashSet<>(null == group.getIsrs() ? 0 : group.getIsrs().size());
                    Set<Broker> replicas = new HashSet<>(null == group.getReplicas() ? 0 : group.getReplicas().size());
                    if (null != group.getIsrs()) for (Integer brokerId : group.getIsrs()) {
                        irs.add(group.getBrokers().get(brokerId));
                    }
                    if (null != group.getReplicas()) for (Integer brokerId : group.getReplicas()) {
                        replicas.add(group.getBrokers().get(brokerId));
                    }
                    metadataList.add(new Partition(partition, group.getBrokers().get(group.getLeader()), irs, replicas));
                }
            }
        return metadataList;
    }

    public Map<Integer, Broker> fetchAllBroker() {
        Map<Integer, Broker> brokers = new HashMap<>();
            for (PartitionGroup group : partitionGroups.values()) {
                brokers.putAll(group.getBrokers());
            }
        return brokers;
    }

    /**
     * 返回相关的broker(获取topic中所有partition的replicas)
     * @return
     */
    public Set<Integer> fetchAllBrokerIds() {
        Set<Integer> brokers = new HashSet<>();
        for (PartitionGroup group : partitionGroups.values()) {
            brokers.addAll(group.getReplicas());
        }
        return brokers;
    }
    public Set<Short> fetchAllPartitions() {
        Set<Short> partitions = new HashSet<>();
        for (PartitionGroup group : partitionGroups.values()) {
            partitions.addAll(group.getPartitions());
        }
        return partitions;
    }
    //TODO 性能差，需要排查下并发问题
    public List<Short> fetchPartitionByBroker(int brokerId) {
        List<Short> partitions = new ArrayList<>();
            for (PartitionGroup group : partitionGroups.values()) {
                if (group.getLeader().equals(brokerId)) partitions.addAll(group.getPartitions());
            }
        return partitions;
    }

    public boolean checkSequential() {
        //TODO 默认都设置为非顺序消息
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof TopicConfig)) {
            return false;
        }
        if (o == this) {
            return true;
        }

        return super.equals(((TopicConfig) o).getName());
    }

    @Override
    public String toString() {
        return "TopicConfig{" +
                "topic='" + name.getFullName() + '\'' +
                ", partitions=" + partitions +
                ", type=" + type +
                ", priorityPartitions=" + (null == priorityPartitions ? "[]" : Arrays.toString(priorityPartitions.toArray())) +
                ", partitionGroups=" + partitionGroups +
                ", partitionGroupMap=" + partitionGroupMap +
                '}';
    }
}