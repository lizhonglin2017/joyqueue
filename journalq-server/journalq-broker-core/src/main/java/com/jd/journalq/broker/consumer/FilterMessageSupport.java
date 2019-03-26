package com.jd.journalq.broker.consumer;

import com.alibaba.fastjson.JSON;
import com.jd.journalq.broker.cluster.ClusterManager;
import com.jd.journalq.broker.consumer.filter.FilterCallback;
import com.jd.journalq.broker.consumer.filter.FilterPipeline;
import com.jd.journalq.broker.consumer.filter.MessageFilter;
import com.jd.journalq.domain.Consumer;
import com.jd.journalq.domain.TopicName;
import com.jd.journalq.event.ConsumerEvent;
import com.jd.journalq.event.EventType;
import com.jd.journalq.event.MetaEvent;
import com.jd.journalq.event.NameServerEvent;
import com.jd.journalq.exception.JMQCode;
import com.jd.journalq.exception.JMQException;
import com.jd.journalq.toolkit.concurrent.EventListener;
import com.jd.journalq.toolkit.security.Hex;
import com.jd.journalq.toolkit.security.Md5;
import com.jd.laf.extension.ExtensionManager;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 消息过滤
 * <p>
 * Created by chengzhiliang on 2018/8/22.
 */
class FilterMessageSupport {

    private final Logger logger = LoggerFactory.getLogger(FilterMessageSupport.class);
    // 集群管理
    private ClusterManager clusterManager;
    // 用户的消息过滤管道缓存
    private ConcurrentMap</* consumerId */String, /* 过滤管道 */FilterPipeline<MessageFilter>> filterRuleCache = new ConcurrentHashMap<>();

    public FilterMessageSupport(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;

        // 添加消费者信息更新事件
        clusterManager.addListener(new updateConsumeListener());
    }


    /**
     * 根据用户设置的过滤规则过滤消息
     *
     * @param consumer       消费者
     * @param byteBuffers    消息缓存集合
     * @param filterCallback 过滤回调函数，用于处理被过滤消息的应答问题
     * @return
     * @throws JMQException
     */
    public List<ByteBuffer> filter(Consumer consumer, List<ByteBuffer> byteBuffers, FilterCallback filterCallback) throws JMQException {
        FilterPipeline<MessageFilter> filterPipeline = filterRuleCache.get(consumer.getId());
        if (filterPipeline == null) {
            filterPipeline = createFilterPipeline(consumer.getConsumerPolicy());
            filterRuleCache.putIfAbsent(consumer.getId(), filterPipeline);
        }
        List<ByteBuffer> result = filterPipeline.execute(byteBuffers, filterCallback);
        return result;
    }

    /**
     * 根据用户配置消费策略构建顾虑管道
     *
     * @param consumerPolicy 用户消费策略
     * @return 消息过滤管道
     * @throws JMQException
     */
    private FilterPipeline<MessageFilter> createFilterPipeline(Consumer.ConsumerPolicy consumerPolicy) throws JMQException {
        Map<String, String> filterRule = consumerPolicy.getFilters();
        String pipelineId = generatePipelineId(filterRule);
        FilterPipeline<MessageFilter> filterPipeline = new FilterPipeline<>(pipelineId);

        if (MapUtils.isNotEmpty(filterRule)) {
            Set<Map.Entry<String, String>> entries = filterRule.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                String type = entry.getKey(); // type
                String content = entry.getValue();// content;
                MessageFilter newMessageFilter = ExtensionManager.getOrLoadExtension(MessageFilter.class, type);
                newMessageFilter.setRule(content);
                filterPipeline.register(newMessageFilter);
            }
        }

        return filterPipeline;
    }

    /**
     * 生成管道标号
     *
     * @param filterRule 过滤规则
     * @return 管道编号
     * @throws JMQException
     */
    private String generatePipelineId(Map<String, String> filterRule) throws JMQException {
        if (MapUtils.isEmpty(filterRule)) {
            return null;
        }
        String jsonString = JSON.toJSONString(filterRule);
        try {
            byte[] encrypt = Md5.INSTANCE.encrypt(jsonString.getBytes("utf-8"), null);
            return Hex.encode(encrypt);
        } catch (Exception e) {
            logger.error("generate filter pipeline error.", e);
            throw new JMQException(e, JMQCode.CN_UNKNOWN_ERROR.getCode());
        }
    }

    /**
     * 更新消费者对于的过滤管道
     *
     * @param topic
     * @param app
     */
    private void updateFilterRuleCache(TopicName topic, String app) {
        try {
            Consumer.ConsumerPolicy consumerPolicy = clusterManager.getConsumerPolicy(topic, app);
            Map<String, String> filters = consumerPolicy.getFilters();
            String pipelineId = generatePipelineId(filters);
            FilterPipeline<MessageFilter> pipeline = filterRuleCache.get(getConsumeId(topic, app));
            if (pipeline != null && StringUtils.equals(pipelineId, pipeline.getId())) {
                // id相同说明过滤管道已经存在，不需要重复创建
                logger.info("FilterPipeline is already exist, topic:[{}], app:[{}], filers:[{}]", topic, app, JSON.toJSON(filters));
                return;
            }

            FilterPipeline<MessageFilter> filterPipeline = createFilterPipeline(consumerPolicy);
            filterRuleCache.put(getConsumeId(topic, app), filterPipeline);
        } catch (Exception ex) {
            logger.error("Update Message filter cache error.", ex);
        }
    }

    /**
     * 生成用户编号
     *
     * @param topic 主题
     * @param app   应用
     * @return 用户编号
     */
    private String getConsumeId(TopicName topic, String app) {
        // copy from Consumer.getId();
        return new StringBuilder(30).append(topic.getFullName()).append(".").append(app).toString();
    }

    /**
     * 监听消费配置变化，更新过滤管道
     */
    class updateConsumeListener implements EventListener {

        @Override
        public void onEvent(Object event) {
            if (((MetaEvent) event).getEventType() == EventType.UPDATE_CONSUMER) {
                NameServerEvent nameServerEvent = (NameServerEvent) event;
                logger.info("listen update consume event to update filter pipeline.");

                ConsumerEvent updateConsumerEvent = (ConsumerEvent) nameServerEvent.getMetaEvent();

                // 更新消息过滤管道
                updateFilterRuleCache(updateConsumerEvent.getTopic(), updateConsumerEvent.getApp());
            }
        }
    }
}