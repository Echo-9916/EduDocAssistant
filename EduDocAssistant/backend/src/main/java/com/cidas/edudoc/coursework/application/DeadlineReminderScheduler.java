package com.cidas.edudoc.coursework.application;

import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRepository;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.AssignmentStats;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.MemberAccount;
import com.cidas.edudoc.message.application.NotificationEvents;
import com.cidas.edudoc.message.infrastructure.NotificationWorkerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 临近截止扫描（06 切片 2-5 生产者④，设计 §4.1 第 4 行）：每小时扫 PUBLISHED 且
 * due_time 落在未来 {@value #WINDOW_HOURS} 小时内的作业，给未交学生发 SUBMISSION 提醒。
 *
 * <p>每天最多一封：去重键带扫描日期（dedupDate=yyyyMMdd），配合 uk_notification_1，
 * 同一天内每小时重扫、甚至多实例重复扫都只会写入第一封（重放即原结果，§4.3）。
 * 未提交名单由 SQL 的 NOT EXISTS 现算：扫描间隔内刚交的学生天然出局。
 *
 * <p>开关复用 edudoc.notification.enabled：worker 关掉时扫描也停，避免事件无人消费积压。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineReminderScheduler {

    /** 提醒提前量：截止前 48 小时（设计 §六.2 已拍板口径，改窗口只需换这个常量）。 */
    static final int WINDOW_HOURS = 48;
    // ponytail: outbox 载荷有 4KB 硬上限（OutboxEventMapper 的 JSON_SERIALIZE VARCHAR2(4000)），
    // 50 人/事件约 2KB 稳在限内；大班拆多事件由去重键兜底，真不够再把 mapper 换 RETURNING CLOB
    static final int RECIPIENTS_PER_EVENT = 50;
    private static final DateTimeFormatter DUE_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CourseworkRepository courseworkRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final NotificationWorkerProperties properties;

    /** 每小时第 10 分钟执行（错开整点的其它任务）；全扫描一个事务，作业量级为个位数～几十。 */
    @Scheduled(cron = "0 10 * * * *")
    @Transactional
    public void scanDueSoon() {
        if (!properties.enabled()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        String dedupDate = DateTimeFormatter.BASIC_ISO_DATE.format(now);
        for (AssignmentStats assignment : courseworkRepository
                .listDueSoonAssignments(now, now.plusHours(WINDOW_HOURS))) {
            List<String> students = courseworkRepository.listUnsubmitted(assignment.getId()).stream()
                    .map(MemberAccount::getUserId)
                    .toList();
            if (students.isEmpty()) {
                continue;
            }
            String body = "《" + assignment.getTitle() + "》将于 " + DUE_TEXT.format(assignment.getDueTime())
                    + " 截止，你尚未提交，请尽快完成提交。";
            for (int from = 0; from < students.size(); from += RECIPIENTS_PER_EVENT) {
                List<String> chunk = students.subList(from,
                        Math.min(from + RECIPIENTS_PER_EVENT, students.size()));
                outboxEventRepository.publish("ASSIGNMENT", assignment.getId(), NotificationEvents.EVENT_TYPE,
                        NotificationEvents.payload(NotificationEvents.TYPE_SUBMISSION, assignment.getId(),
                                dedupDate, "ASSIGNMENT", assignment.getId(), null, chunk,
                                "作业截止提醒", body));
            }
        }
    }
}
