import { ago, delay, update } from '../store'
import type { RoleCodeValue } from '../../api/types'

/** 消息类型按角色不同：对齐原型屏说明（学生 26、教师 26、运维 25）。 */
export type MessageType =
  | 'COURSE'      // 课程组更新 / 课程治理
  | 'SUBMISSION'  // 学生提交 / 作业提醒
  | 'FEEDBACK'    // 教师反馈
  | 'FORMAT'      // 格式异常
  | 'PERMISSION'  // 权限变更
  | 'ALERT'       // 系统告警
  | 'APPROVAL'    // 审批通知
  | 'SYSTEM'      // 系统通知

export type Message = {
  id: string
  role: RoleCodeValue
  type: MessageType
  title: string
  content: string
  from: string
  createTime: string
  read: boolean
  link?: string
}

export const messageTypeLabel: Record<MessageType, string> = {
  COURSE: '课程组更新',
  SUBMISSION: '提交提醒',
  FEEDBACK: '教师反馈',
  FORMAT: '格式异常',
  PERMISSION: '权限变更',
  ALERT: '系统告警',
  APPROVAL: '审批通知',
  SYSTEM: '系统通知',
}

const SEED: Message[] = [
  { id: 'MSG-01', role: 'STUDENT', type: 'FEEDBACK', title: '《Web开发课程设计报告》批改反馈', content: '张同学：第三章格式已通过，参考文献需按 GB/T 7714 补齐卷期号，请在本周五前重新提交。', from: '李老师', createTime: ago(0, 3), read: false },
  { id: 'MSG-02', role: 'STUDENT', type: 'SUBMISSION', title: '作业截止提醒', content: '《数据库原理》作业 3「索引设计实验」将于 2026-11-12 23:59 截止，当前尚未提交。', from: '课程助手', createTime: ago(0, 6), read: false, link: 'courses' },
  { id: 'MSG-03', role: 'STUDENT', type: 'FORMAT', title: '格式校验未通过', content: '《操作系统课程报告_v2.docx》存在 3 项阻塞问题：页边距、一级标题字号、代码块字体。', from: '格式检查服务', createTime: ago(1, 2), read: false },
  { id: 'MSG-04', role: 'STUDENT', type: 'COURSE', title: '课程模板更新', content: '《Web开发技术》课程模板更新至 v3，新增「附录代码清单」章节要求。', from: '李老师', createTime: ago(2), read: true },
  { id: 'MSG-05', role: 'STUDENT', type: 'SYSTEM', title: '存储配额提醒', content: '个人文档已使用 1.7 GB / 2 GB，可清理回收站释放空间。', from: '系统', createTime: ago(4), read: true },
  { id: 'MSG-06', role: 'TEACHER', type: 'SUBMISSION', title: '新提交待批改', content: '《Web开发技术》作业 2 新增 12 份提交，其中 3 份格式校验未通过。', from: '作业服务', createTime: ago(0, 2), read: false, link: 'courses' },
  { id: 'MSG-07', role: 'TEACHER', type: 'FORMAT', title: '批量格式异常', content: '课程组 3 的 5 份报告页眉缺失课程号，已生成异常清单。', from: '格式检查服务', createTime: ago(0, 8), read: false },
  { id: 'MSG-08', role: 'TEACHER', type: 'COURSE', title: '课程组成员变更', content: '《数据库原理》新增学生 2 名（转专业插入），课程组 5。', from: '教务同步', createTime: ago(1, 5), read: true },
  { id: 'MSG-09', role: 'TEACHER', type: 'SYSTEM', title: '模板发布结果', content: '《编译原理》课程模板 v2 已发布，覆盖 3 个班级共 128 名学生。', from: '模板服务', createTime: ago(3), read: true },
  { id: 'MSG-10', role: 'OPS', type: 'ALERT', title: '解析队列积压', content: '文档解析任务积压 47 条，平均等待 6 分 12 秒，已超过 5 分钟阈值。', from: '监控', createTime: ago(0, 1), read: false, link: 'storage' },
  { id: 'MSG-11', role: 'OPS', type: 'PERMISSION', title: '角色变更申请待审批', content: '教师工号 T2021043 申请获得「课程模板管理员」角色，影响 4 门课程。', from: '审批流', createTime: ago(0, 4), read: false, link: 'users' },
  { id: 'MSG-12', role: 'OPS', type: 'APPROVAL', title: '存储清理审批', content: '回收站超期对象清理任务（预计释放 182 GB）待确认。', from: '存储治理', createTime: ago(1), read: false, link: 'storage' },
  { id: 'MSG-13', role: 'OPS', type: 'COURSE', title: '课程归档提醒', content: '上学期 6 门课程已结课超过 30 天，建议归档为只读。', from: '课程治理', createTime: ago(2), read: true, link: 'courses' },
]

export function messagesOf(role: RoleCodeValue): Message[] {
  return SEED.filter((m) => m.role === role)
}

export function listMessages(role: RoleCodeValue) {
  return delay(messagesOf(role).map((m) => ({ ...m })))
}

export function markRead(ids: string[], read = true) {
  update(SEED, (m) => ids.includes(m.id), { read })
  return delay(true, 120)
}
