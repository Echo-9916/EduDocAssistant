// 学生工作台：四个统计卡 + 待办列表 + 快捷入口，数据来自一次聚合接口。
import { useEffect, useState } from 'react'
import { Alert, Card, Col, List, Row, Statistic, Tag, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { AppButton } from '../../components'
import { rolePath } from '../../routes'
import { getErrorMessage } from '../../api/http'
import { studentWorkbenchSummary } from '../../api/studentWorkbench'
import type { StudentWorkbenchSummary } from '../../api/studentWorkbench'

const SHORTCUTS = [
	{
		key: 'upload',
		label: '上传文档',
		desc: 'Word/PDF 课件、作业、报告',
		to: 'documents'
	},
	{
		key: 'from-template',
		label: '按模板新建',
		desc: '选课程模板生成大纲初稿',
		to: 'format'
	},
	{
		key: 'assignments',
		label: '查看作业',
		desc: '课程作业与提交状态',
		to: 'courses'
	},
	{ key: 'messages', label: '教学消息', desc: '提醒与反馈通知', to: 'messages' }
]

export default function StudentWorkbench() {
	const navigate = useNavigate()
	const [summary, setSummary] = useState<StudentWorkbenchSummary | null>(null)
	const [loading, setLoading] = useState(true)
	const [error, setError] = useState('')

	useEffect(() => {
		let cancelled = false
		studentWorkbenchSummary()
			.then(r => !cancelled && setSummary(r))
			.catch(e => !cancelled && setError(getErrorMessage(e)))
			.finally(() => !cancelled && setLoading(false))
		return () => {
			cancelled = true
		}
	}, [])

	const toCourses = () => navigate(`${rolePath.STUDENT}/courses`)

	return (
		<div>
			{error && (
				<Alert
					type='error'
					showIcon
					message={error}
					style={{ marginBottom: 16 }}
				/>
			)}
			<Row gutter={16} style={{ marginBottom: 24 }}>
				<Col span={6}>
					<Card>
						<Statistic
							title='我的文档'
							value={summary?.documentTotal ?? '-'}
							suffix='份'
						/>
					</Card>
				</Col>
				<Col span={6}>
					<Card>
						<Statistic
							title='待提交作业'
							value={summary?.pendingAssignmentTotal ?? '-'}
							suffix='项'
						/>
					</Card>
				</Col>
				<Col span={6}>
					<Card>
						<Statistic
							title='未读消息'
							value={summary?.unreadMessageTotal ?? '-'}
							suffix='条'
						/>
					</Card>
				</Col>
				<Col span={6}>
					<Card>
						<Statistic
							title='协作中'
							value={summary?.activeCollaborationTotal ?? '-'}
							suffix='份'
						/>
					</Card>
				</Col>
			</Row>

			<Card
				title='待办'
				extra={
					<AppButton type='link' size='small' onClick={toCourses}>
						查看全部
					</AppButton>
				}
				style={{ marginBottom: 24 }}>
				<List
					loading={loading}
					dataSource={summary?.todos ?? []}
					locale={{ emptyText: error ? '待办加载失败' : '暂无待办' }}
					renderItem={item => (
						<List.Item
							style={{ cursor: 'pointer' }}
							onClick={toCourses}
							actions={[
								<Tag
									key='remain'
									color={item.status === 'OVERDUE' ? 'error' : 'default'}>
									{item.remainText}
								</Tag>
							]}>
							<List.Item.Meta
								title={`${item.title} · ${item.courseName}`}
								description={`截止 ${item.deadline.replace('T', ' ').slice(0, 16)}`}
							/>
						</List.Item>
					)}
				/>
			</Card>

			<Card title='快捷入口'>
				<Row gutter={[16, 16]}>
					{SHORTCUTS.map(s => (
						<Col span={6} key={s.key}>
							<Card size='small' title={s.label}>
								<Typography.Paragraph
									type='secondary'
									style={{ fontSize: 12, minHeight: 36 }}>
									{s.desc}
								</Typography.Paragraph>
								<AppButton
									size='small'
									onClick={() => navigate(`${rolePath.STUDENT}/${s.to}`)}>
									前往
								</AppButton>
							</Card>
						</Col>
					))}
				</Row>
			</Card>
		</div>
	)
}
