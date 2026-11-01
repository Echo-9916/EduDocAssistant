import { useState } from 'react'
import { Form, Input, Space, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal, AppPagination, AppTable, requiredRule } from '../components'

type Item = { id: number; name: string; status: string }

const dataSource: Item[] = Array.from({ length: 25 }, (_, i) => ({
  id: i + 1,
  name: `文档 ${i + 1}`,
  status: i % 2 === 0 ? '已发布' : '草稿',
}))

const columns: ColumnsType<Item> = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '名称', dataIndex: 'name' },
  { title: '状态', dataIndex: 'status' },
]

/** 基础组件演示页：验证统一组件封装（按钮/表单校验/表格分页/弹窗/分页）可正常使用。 */
export default function Demo() {
  const [open, setOpen] = useState(false)
  const [page, setPage] = useState(1)
  const [form] = Form.useForm()

  return (
    <div style={{ padding: 24 }}>
      <h2>基础组件演示</h2>
      <Space wrap>
        <AppButton onClick={() => setOpen(true)}>打开弹窗</AppButton>
        <AppButton type="default" onClick={() => message.success('操作成功')}>
          消息提示
        </AppButton>
      </Space>

      <Form
        form={form}
        layout="inline"
        style={{ marginTop: 16 }}
        onFinish={() => message.success('校验通过')}
      >
        <Form.Item name="name" label="名称" rules={[requiredRule('请输入名称')]}>
          <Input placeholder="请输入" />
        </Form.Item>
        <Form.Item>
          <AppButton htmlType="submit">提交</AppButton>
        </Form.Item>
      </Form>

      <AppTable<Item>
        rowKey="id"
        columns={columns}
        dataSource={dataSource}
        style={{ marginTop: 16 }}
        pagination={{ current: page, pageSize: 10, onChange: setPage }}
      />

      <AppPagination
        current={page}
        total={dataSource.length}
        pageSize={10}
        onChange={setPage}
        style={{ marginTop: 16 }}
      />

      <AppModal open={open} title="确认" onOk={() => setOpen(false)} onCancel={() => setOpen(false)}>
        这是一个弹窗。
      </AppModal>
    </div>
  )
}
