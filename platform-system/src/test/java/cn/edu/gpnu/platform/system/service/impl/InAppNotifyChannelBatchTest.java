package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.entity.Notification;
import cn.edu.gpnu.platform.system.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Phase 44（PG-M3 整改）：站内信批量写的语句形态与字段等价性单测（离线，无数据库）。
 *
 * <p>审计 PG-M3 指出 44f 把 44a 的批量插入回退为逐行 insert，性能交付被撤销而文档仍称已闭环。本次改为
 * {@code NotificationMapper.insertBatch}（同事务/同连接的 multi-values INSERT）。这里断言：
 * ①批量路径<b>不再</b>逐行 {@code insert}；②语句条数为 ⌈N/500⌉；③每行字段与 {@link InAppNotifyChannel#send}
 * 完全一致（44f 明确承诺的不变量）。同连接/同事务与真实锁等待由 {@code Phase44NotificationBatchIT} 用真实
 * MySQL 证明——单测无法证明连接归属。
 */
@ExtendWith(MockitoExtension.class)
class InAppNotifyChannelBatchTest {

    private static final int BATCH_SIZE = 500;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private InAppNotifyChannel channel;

    @Test
    void sendBatchIssuesOneMultiValuesStatementAndNoPerRowInsert() {
        channel.sendBatch(List.of(11L, 12L, 13L), "REVIEW_TODO", "待初审", "内容", "process_material", "9001");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationMapper).insertBatch(captor.capture());
        verify(notificationMapper, never()).insert(any(Notification.class));

        assertThat(captor.getValue()).hasSize(3);
        assertThat(captor.getValue()).extracting(Notification::getUserId).containsExactly(11L, 12L, 13L);
    }

    @Test
    void sendBatchChunksLargeFanOutIntoBoundedStatements() {
        // 大扇出（PG-M3 的失败场景）：1001 个收件人 → 3 条语句（500/500/1），而不是 1001 次 insert。
        List<Integer> chunkSizes = new ArrayList<>();
        doAnswer(invocation -> {
            List<?> rows = invocation.getArgument(0);
            chunkSizes.add(rows.size()); // 必须在调用时取尺寸：调用方会复用并清空该 List
            return rows.size();
        }).when(notificationMapper).insertBatch(anyList());

        List<Long> recipients = LongStream.rangeClosed(1, 1001).boxed().toList();
        channel.sendBatch(recipients, "VIDEO_ASSIGN", "视频评审提醒", "内容", "video_review", "7");

        assertThat(chunkSizes).containsExactly(BATCH_SIZE, BATCH_SIZE, 1);
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    void batchRowFieldsAreIdenticalToPerRowSend() {
        channel.send(21L, "REVIEW_TODO", "待复审", "正文", "process_material", "9002");
        ArgumentCaptor<Notification> single = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(single.capture());

        channel.sendBatch(List.of(21L), "REVIEW_TODO", "待复审", "正文", "process_material", "9002");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> batch = ArgumentCaptor.forClass(List.class);
        verify(notificationMapper).insertBatch(batch.capture());

        Notification perRow = single.getValue();
        Notification batched = batch.getValue().get(0);
        assertThat(batched.getUserId()).isEqualTo(perRow.getUserId());
        assertThat(batched.getType()).isEqualTo(perRow.getType());
        assertThat(batched.getTitle()).isEqualTo(perRow.getTitle());
        assertThat(batched.getContent()).isEqualTo(perRow.getContent());
        assertThat(batched.getBizType()).isEqualTo(perRow.getBizType());
        assertThat(batched.getBizId()).isEqualTo(perRow.getBizId());
        assertThat(batched.getReadFlag()).isEqualTo(perRow.getReadFlag());
        // 主键与审计字段留空、交由 MyBatis-Plus 参数处理器填充（两路同一条链），故此处必须都为 null。
        assertThat(batched.getId()).isNull();
        assertThat(perRow.getId()).isNull();
        assertThat(batched.getCreatedAt()).isNull();
        assertThat(batched.getCreatedBy()).isNull();
        assertThat(batched.getDeleted()).isNull();
    }

    @Test
    void emptyRecipientsIssueNoStatement() {
        channel.sendBatch(List.of(), "REVIEW_TODO", "待初审", "内容", "process_material", "9003");
        channel.sendBatch(null, "REVIEW_TODO", "待初审", "内容", "process_material", "9003");

        verifyNoInteractions(notificationMapper);
    }
}
