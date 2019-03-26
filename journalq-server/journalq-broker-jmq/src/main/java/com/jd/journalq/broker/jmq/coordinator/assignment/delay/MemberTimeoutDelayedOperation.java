package com.jd.journalq.broker.jmq.coordinator.assignment.delay;

import com.jd.journalq.broker.jmq.coordinator.domain.JMQCoordinatorGroup;
import com.jd.journalq.broker.jmq.coordinator.domain.JMQCoordinatorGroupMember;
import com.jd.journalq.toolkit.delay.AbstractDelayedOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MemberTimeoutDelayedOperation
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/12/5
 */
public class MemberTimeoutDelayedOperation extends AbstractDelayedOperation {

    protected static final Logger logger = LoggerFactory.getLogger(MemberTimeoutDelayedOperation.class);

    private JMQCoordinatorGroup group;
    private JMQCoordinatorGroupMember member;

    public MemberTimeoutDelayedOperation(JMQCoordinatorGroup group, JMQCoordinatorGroupMember member, long delayMs) {
        super(delayMs);
        this.group = group;
        this.member = member;
    }

    @Override
    protected boolean tryComplete() {
        synchronized (group) {
            if (member.isExpired()) {
                return forceComplete();
            } else {
                return false;
            }
        }
    }

    @Override
    protected void onExpiration() {
        synchronized (group) {
            if (!group.getMembers().containsKey(member.getId()) || !member.isExpired()) {
                return;
            }

            logger.info("jmq consumer {} is expired, release assigned partition, connection: {}, latestHeartbeat: {}", member.getId(), member.getConnectionHost(), member.getLatestHeartbeat());

            if (member.getTimeoutCallback() != null) {
                member.getTimeoutCallback().onCompletion(group, member);
            }
            group.addExpiredMember(member);
            group.getMembers().remove(member.getId(), member);
        }
    }
}