import { useEffect, useMemo, useState } from 'react'
import { Alert, Checkbox, Descriptions, Drawer, Form, Input, Select, Space, Tabs, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal, AppTable, requiredRule } from '../../components'
import DemoPage from '../DemoPage'
import type { AccountStatusValue, PermissionCodeValue } from '../../api/types'
import {
  ALL_PERMISSIONS, SCOPE_OPTIONS, accountStatusColor, accountStatusLabel, addRole, addUser, changeUserRole, conflictsOf,
  listRoles, listUsers, permissionLabel, setRolePermission,
} from '../../mock/domain/governance'
import type { GovRole, GovUser, UserVisit } from '../../mock/domain/governance'

const roleTagColor = (code: string) =>
  code === 'STUDENT' ? 'green' : code === 'TEACHER' ? 'blue' : code === 'OPS' ? 'purple' : 'gold'

/** 用户与权限：用户目录 + 角色权限矩阵（对应原型：运维 07/08/09/10/11/12）。 */
export default function OpsUsers() {
  const [users, setUsers] = useState<GovUser[]>([])
  const [roles, setRoles] = useState<GovRole[]>([])
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState('users')
  // 07 屏筛选
  const [roleFilter, setRoleFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState<'ALL' | AccountStatusValue>('ALL')
  const [orgFilter, setOrgFilter] = useState('ALL')
  const [keyword, setKeyword] = useState('')
  // 08/09/11/12 屏弹层
  const [detail, setDetail] = useState<GovUser | null>(null)
  const [roleTarget, setRoleTarget] = useState<GovUser | null>(null)
  const [addOpen, setAddOpen] = useState(false)
  const [createRoleOpen, setCreateRoleOpen] = useState(false)
  const [addForm] = Form.useForm()
  const [changeRoleForm] = Form.useForm()
  const [createRoleForm] = Form.useForm()
  const targetRoleCode = Form.useWatch('target', changeRoleForm) as string | undefined
  const pickedPerms = (Form.useWatch('permissions', createRoleForm) as PermissionCodeValue[] | undefined) ?? []

  useEffect(() => {
    Promise.all([listUsers(), listRoles()]).then(([u, r]) => {
      setUsers(u)
      setRoles(r)
      setLoading(false)
    })
  }, [])

  const roleName = (code: string) => roles.find((r) => r.code === code)?.name ?? code
  const permsOf = (code: string | undefined) => roles.find((r) => r.code === code)?.permissions ?? []

  const data = useMemo(
    () =>
      users
        .filter((u) => roleFilter === 'ALL' || u.roleCode === roleFilter)
        .filter((u) => statusFilter === 'ALL' || u.status === statusFilter)
        .filter((u) => orgFilter === 'ALL' || u.org === orgFilter)
        .filter((u) => !keyword || u.name.includes(keyword) || u.accountNo.includes(keyword)),
    [users, roleFilter, statusFilter, orgFilter, keyword],
  )
  const orgs = useMemo(() => [...new Set(users.map((u) => u.org))], [users])
  /** 10 屏：矩阵中命中互斥对的角色。 */
  const matrixConflicts = roles.flatMap((r) => conflictsOf(r.permissions).map((c) => ({ role: r, ...c })))

  /* ---- 12 屏：变更角色 ---- */
  const added = permsOf(targetRoleCode).filter((p) => !permsOf(roleTarget?.roleCode).includes(p))
  const removed = permsOf(roleTarget?.roleCode).filter((p) => !permsOf(targetRoleCode).includes(p))

  const onChangeRole = async () => {
    if (!roleTarget) return
    const values = await changeRoleForm.validateFields()
    await changeUserRole(roleTarget.id, values.target, values.effective, values.reason)
    setUsers((prev) => prev.map((u) => (u.id === roleTarget.id ? { ...u, roleCode: values.target } : u)))
    setRoleTarget(null)
    changeRoleForm.resetFields()
    message.success('用户角色已变更，变更立即写入审计日志')
  }

  /* ---- 09 屏：添加用户 ---- */
  const onAddUser = async () => {
    const values = await addForm.validateFields()
    setUsers(await addUser({ accountNo: values.accountNo, name: values.name, roleCode: values.role, org: values.org, scope: values.scope ?? [] }))
    setAddOpen(false)
    addForm.resetFields()
    message.success('用户已创建并发送激活邀请，首次登录前不获得任何课程或文档访问权限')
  }

  /* ---- 10 屏：矩阵勾选 ---- */
  const togglePerm = async (roleCode: string, perm: PermissionCodeValue, checked: boolean) => {
    await setRolePermission(roleCode, perm, checked)
    setRoles(await listRoles())
  }

  /* ---- 11 屏：新增角色 ---- */
  const createRoleConflicts = conflictsOf(pickedPerms)
  const onCreateRole = async () => {
    const values = await createRoleForm.validateFields()
    await addRole({ name: values.name, purpose: values.purpose, scope: values.scope, permissions: (values.permissions ?? []) as PermissionCodeValue[] })
    setRoles(await listRoles())
    setCreateRoleOpen(false)
    createRoleForm.resetFields()
    message.success(createRoleConflicts.length ? `自定义角色已创建，职责冲突检查命中 ${createRoleConflicts.length} 项，已列入权限冲突复核` : '自定义角色已创建，职责冲突检查通过')
  }

  const userColumns: ColumnsType<GovUser> = [
    { title: '工号 / 学号', dataIndex: 'accountNo', width: 110 },
    { title: '姓名', dataIndex: 'name', width: 90 },
    {
      title: '角色', dataIndex: 'roleCode', width: 130,
      render: (v: string) => <Tag color={roleTagColor(v)}>{roleName(v)}</Tag>,
    },
    { title: '组织', dataIndex: 'org', width: 170 },
    {
      title: '账号状态', dataIndex: 'status', width: 90,
      render: (v: AccountStatusValue) => <Tag color={accountStatusColor[v]}>{accountStatusLabel[v]}</Tag>,
    },
    { title: '课程范围', width: 90, render: (_, u) => `${u.courseScope.length} 门` },
    { title: '最近登录', dataIndex: 'lastLogin', width: 150 },
    {
      title: '操作', width: 150,
      render: (_, row) => (
        <Space size={0}>
          <AppButton type="link" size="small" onClick={() => setDetail(row)}>详情</AppButton>
          <AppButton type="link" size="small" onClick={() => setRoleTarget(row)}>变更角色</AppButton>
        </Space>
      ),
    },
  ]

  const visitColumns: ColumnsType<UserVisit> = [
    { title: '时间', dataIndex: 'time', width: 140 },
    { title: '事件', dataIndex: 'event' },
    { title: '来源', dataIndex: 'source', width: 160 },
    {
      title: '结果', dataIndex: 'result', width: 80,
      render: (v: UserVisit['result']) => <Tag color={v === 'SUCCESS' ? 'green' : 'red'}>{v === 'SUCCESS' ? '成功' : '已拒绝'}</Tag>,
    },
  ]

  type MatrixRow = { perm: PermissionCodeValue }
  const matrixColumns: ColumnsType<MatrixRow> = [
    {
      title: '权限项', dataIndex: 'perm', width: 220,
      render: (v: PermissionCodeValue) => (
        <Space direction="vertical" size={0}>
          <span>{permissionLabel[v]}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{v}</Typography.Text>
        </Space>
      ),
    },
    ...roles.map((role) => ({
      title: (
        <Space size={4}>
          {role.name}
          <Tag color={role.builtin ? 'blue' : 'gold'} style={{ marginInlineEnd: 0 }}>{role.builtin ? '内置' : '自定义'}</Tag>
        </Space>
      ),
      width: 130,
      render: (_: unknown, row: MatrixRow) => (
        <Checkbox
          checked={role.permissions.includes(row.perm)}
          onChange={(e) => togglePerm(role.code, row.perm, e.target.checked)}
        />
      ),
    })),
  ]

  const usersPane = (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          style={{ width: 160 }}
          value={roleFilter}
          onChange={setRoleFilter}
          options={[{ value: 'ALL', label: '全部角色' }, ...roles.map((r) => ({ value: r.code, label: r.name }))]}
        />
        <Select
          style={{ width: 130 }}
          value={statusFilter}
          onChange={setStatusFilter}
          options={[
            { value: 'ALL', label: '全部状态' },
            ...(Object.keys(accountStatusLabel) as AccountStatusValue[]).map((s) => ({ value: s, label: accountStatusLabel[s] })),
          ]}
        />
        <Select
          style={{ width: 190 }}
          value={orgFilter}
          onChange={setOrgFilter}
          options={[{ value: 'ALL', label: '全部组织' }, ...orgs.map((o) => ({ value: o, label: o }))]}
        />
        <Input.Search
          style={{ width: 220 }}
          placeholder="搜索姓名 / 工号 / 学号"
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </Space>
      <AppTable<GovUser> rowKey="id" size="middle" loading={loading} columns={userColumns} dataSource={data} />
    </>
  )

  const rolesPane = (
    <>
      <Space style={{ marginBottom: 12 }} wrap>
        <Typography.Text type="secondary">
          勾选即调整该角色的权限边界（就地生效）。调整账号权限请在用户目录使用「变更角色」，变更立即写入审计日志。
        </Typography.Text>
        <AppButton
          type="default"
          size="small"
          onClick={() => {
            setTab('users')
            message.info('请在用户目录中选择目标账号，点击「变更角色」')
          }}
        >
          变更用户角色
        </AppButton>
      </Space>
      {matrixConflicts.map(({ role, a, b, why }) => (
        <Alert
          key={`${role.code}-${a}-${b}`}
          type="warning"
          showIcon
          style={{ marginBottom: 8 }}
          message={`职责冲突：「${role.name}」同时授予「${permissionLabel[a]}」与「${permissionLabel[b]}」，${why}。`}
        />
      ))}
      <AppTable<MatrixRow>
        rowKey="perm"
        size="small"
        loading={loading}
        pagination={false}
        columns={matrixColumns}
        dataSource={ALL_PERMISSIONS.map((perm) => ({ perm }))}
      />
    </>
  )

  return (
    <DemoPage
      title="用户与权限"
      screens="对应原型：运维 07/08/09/10/11/12"
      desc="管理教师、学生、课程组和运维账号，区分身份、角色、课程范围与职责冲突。"
    >
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          { key: 'users', label: '用户目录', children: usersPane },
          { key: 'roles', label: '角色权限矩阵', children: rolesPane },
        ]}
        tabBarExtraContent={
          tab === 'users' ? (
            <AppButton onClick={() => setAddOpen(true)}>添加用户</AppButton>
          ) : (
            <AppButton onClick={() => setCreateRoleOpen(true)}>新增角色</AppButton>
          )
        }
      />

      {/* 08 屏：用户权限详情抽屉 */}
      <Drawer open={!!detail} onClose={() => setDetail(null)} width={620} title={detail ? `用户权限详情 · ${detail.name}` : ''}>
        {detail && (
          <>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="工号 / 学号">{detail.accountNo}</Descriptions.Item>
              <Descriptions.Item label="角色"><Tag color={roleTagColor(detail.roleCode)}>{roleName(detail.roleCode)}</Tag></Descriptions.Item>
              <Descriptions.Item label="所属组织">{detail.org}</Descriptions.Item>
              <Descriptions.Item label="账号状态"><Tag color={accountStatusColor[detail.status]}>{accountStatusLabel[detail.status]}</Tag></Descriptions.Item>
              <Descriptions.Item label="授权来源">{detail.identitySource}</Descriptions.Item>
              <Descriptions.Item label="注册时间">{detail.registeredAt}</Descriptions.Item>
              <Descriptions.Item label="最近登录" span={2}>{detail.lastLogin}</Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5} style={{ marginTop: 16 }}>课程范围</Typography.Title>
            {detail.courseScope.length ? (
              <Space wrap>{detail.courseScope.map((c) => <Tag key={c} color="blue">{c}</Tag>)}</Space>
            ) : (
              <Typography.Text type="secondary">暂未分配课程</Typography.Text>
            )}
            <Typography.Title level={5} style={{ marginTop: 16 }}>最近访问记录</Typography.Title>
            <AppTable<UserVisit> rowKey={(r) => `${r.time}-${r.event}`} size="small" pagination={false} columns={visitColumns} dataSource={detail.visits} />
          </>
        )}
      </Drawer>

      {/* 12 屏：变更用户角色 */}
      <AppModal
        open={!!roleTarget}
        title="变更用户角色"
        okText="确认变更"
        width={560}
        onOk={onChangeRole}
        onCancel={() => setRoleTarget(null)}
      >
        {roleTarget && (
          <Form form={changeRoleForm} layout="vertical">
            <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
              账号：{roleTarget.name} · {roleTarget.accountNo} · {roleTarget.org}
            </Typography.Paragraph>
            <Form.Item label="当前角色">
              <Input value={`${roleName(roleTarget.roleCode)}（${roleTarget.roleCode}）`} readOnly />
            </Form.Item>
            <Form.Item name="target" label="目标角色" rules={[requiredRule('请选择目标角色')]}>
              <Select
                placeholder="请选择目标角色"
                options={roles.filter((r) => r.code !== roleTarget.roleCode).map((r) => ({ value: r.code, label: `${r.name} · ${r.purpose}` }))}
              />
            </Form.Item>
            <Form.Item name="effective" label="生效时间" rules={[requiredRule('请选择生效时间')]} initialValue="立即生效">
              <Select options={['立即生效', '下次登录时生效'].map((v) => ({ value: v, label: v }))} />
            </Form.Item>
            <Form.Item name="reason" label="变更原因" rules={[requiredRule('请填写变更原因')]}>
              <Input.TextArea rows={3} placeholder="如：参与本学期课程质量复核，需增加院系教学审核权限。" />
            </Form.Item>
            {targetRoleCode && (
              <Typography.Paragraph style={{ marginBottom: 8 }}>
                影响说明：权限范围增加 {added.length} 项{added.length > 0 && `（${added.map((p) => permissionLabel[p]).join('、')}）`}，
                移除 {removed.length} 项{removed.length > 0 && `（${removed.map((p) => permissionLabel[p]).join('、')}）`}；课程范围保持不变。
              </Typography.Paragraph>
            )}
            {conflictsOf(permsOf(targetRoleCode)).length > 0 && (
              <Alert type="warning" showIcon style={{ marginBottom: 8 }} message="目标角色存在职责冲突授权，变更后将列入权限冲突复核。" />
            )}
            <Alert type="info" showIcon message="变更立即写入审计日志，记录操作人、对象、原因和结果。" />
          </Form>
        )}
      </AppModal>

      {/* 09 屏：添加用户 */}
      <AppModal open={addOpen} title="添加用户" okText="创建并发送邀请" width={560} onOk={onAddUser} onCancel={() => setAddOpen(false)}>
        <Typography.Paragraph type="secondary">账号将通过校内身份服务完成绑定；新账号首次登录前不会获得任何课程或文档访问权限。</Typography.Paragraph>
        <Form form={addForm} layout="vertical">
          <Space.Compact block>
            <Form.Item name="accountNo" label="工号 / 学号" rules={[requiredRule('请输入工号或学号')]} style={{ flex: 1, marginRight: 8 }}>
              <Input placeholder="如：2024032" />
            </Form.Item>
            <Form.Item name="name" label="姓名" rules={[requiredRule('请输入姓名')]} style={{ flex: 1 }}>
              <Input placeholder="如：周同学" />
            </Form.Item>
          </Space.Compact>
          <Form.Item name="accountType" label="账号类型" rules={[requiredRule('请选择账号类型')]}>
            <Select
              placeholder="请选择账号类型"
              options={[
                { value: 'TEACHER', label: '教师账号' },
                { value: 'STUDENT', label: '学生账号' },
                { value: 'OPS', label: '运维账号' },
              ]}
            />
          </Form.Item>
          <Form.Item name="org" label="所属组织" rules={[requiredRule('请选择所属组织')]}>
            <Select placeholder="请选择组织" options={['计算机学院', '软件学院', '人工智能学院', '教务处'].map((v) => ({ value: v, label: v }))} />
          </Form.Item>
          <Form.Item name="role" label="初始角色" rules={[requiredRule('请选择初始角色')]}>
            <Select placeholder="请选择角色" options={roles.map((r) => ({ value: r.code, label: r.name }))} />
          </Form.Item>
          <Form.Item name="scope" label="初始访问范围" rules={[requiredRule('请选择初始访问范围')]}>
            <Select mode="multiple" placeholder="选择课程或暂不分配" options={SCOPE_OPTIONS.map((v) => ({ value: v, label: v }))} />
          </Form.Item>
        </Form>
      </AppModal>

      {/* 11 屏：新增角色 */}
      <AppModal open={createRoleOpen} title="新增自定义角色" okText="创建角色" width={640} onOk={onCreateRole} onCancel={() => setCreateRoleOpen(false)}>
        <Typography.Paragraph type="secondary">自定义角色仅用于授权范围受限的教学或运维协作，提交前执行职责冲突检查。</Typography.Paragraph>
        <Form form={createRoleForm} layout="vertical">
          <Space.Compact block>
            <Form.Item name="name" label="角色名称" rules={[requiredRule('请输入角色名称')]} style={{ flex: 1, marginRight: 8 }}>
              <Input placeholder="如：院系教学审核员" />
            </Form.Item>
            <Form.Item name="purpose" label="角色用途" rules={[requiredRule('请选择角色用途')]} style={{ flex: 1 }}>
              <Select placeholder="请选择用途" options={['教学治理', '运维协作'].map((v) => ({ value: v, label: v }))} />
            </Form.Item>
          </Space.Compact>
          <Form.Item name="scope" label="授权范围说明" rules={[requiredRule('请描述授权范围')]}>
            <Input.TextArea rows={2} placeholder="如：查看课程运行数据、复核异常提交，不可变更用户角色。" />
          </Form.Item>
          <Form.Item name="permissions" label="权限项" rules={[requiredRule('请至少选择一个权限项')]}>
            <Checkbox.Group style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr' }}>
              {ALL_PERMISSIONS.map((p) => (
                <Checkbox key={p} value={p}>{permissionLabel[p]}</Checkbox>
              ))}
            </Checkbox.Group>
          </Form.Item>
        </Form>
        {createRoleConflicts.length ? (
          <Alert
            type="warning"
            showIcon
            message={`职责冲突检查：命中 ${createRoleConflicts.length} 项互斥授权（${createRoleConflicts.map((c) => `${permissionLabel[c.a]} + ${permissionLabel[c.b]}`).join('；')}），创建后将列入权限冲突复核。`}
          />
        ) : (
          <Alert type="info" showIcon message="职责冲突检查：当前组合未触发高风险职责冲突。" />
        )}
      </AppModal>
    </DemoPage>
  )
}
