/**
 * message 业务域（06 切片 2：站内信箱）。
 *
 * <p>承载信箱列表与未读数、批量已读与全部已读、通知偏好（八类，无偏好记录 = 接收），
 * 以及给投递侧（2-4 NotificationWorker / 2-5 生产者）的写入端口 {@code InboxService#deliver}
 * （uk_notification_1 去重 + 偏好过滤，本切片只给接口不接事件）。
 *
 * <p>域内实际只有 api / application / infrastructure 三层：规则薄（类型枚举校验、
 * 已读派生、偏好缺省口径），全部落在应用层，不建 domain 层。调用顺序固定为
 * Controller → ApplicationService → Repository，SQL 只落在
 * {@code resources/mapper/NotificationMapper.xml}。
 */
package com.cidas.edudoc.message;
