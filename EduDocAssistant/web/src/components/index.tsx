import { Button, Modal, Pagination, Table } from 'antd'
import type { ButtonProps, ModalProps, PaginationProps, TableProps } from 'antd'
import type { Rule } from 'antd/es/form'

/** 表单必填校验规则。 */
export function requiredRule(message: string): Rule {
  return { required: true, message }
}

/** 统一主按钮：默认 primary，可覆盖为 default/text/link 等。 */
export function AppButton({ type = 'primary', ...rest }: ButtonProps) {
  return <Button type={type} {...rest} />
}

/** 统一弹窗：默认关闭即销毁、遮罩点击不关闭。 */
export function AppModal({ destroyOnClose = true, maskClosable = false, ...rest }: ModalProps) {
  return <Modal destroyOnClose={destroyOnClose} maskClosable={maskClosable} {...rest} />
}

/** 统一分页：默认显示条数切换与总数。 */
export function AppPagination({ showSizeChanger = true, showTotal = defaultTotal, ...rest }: PaginationProps) {
  return <Pagination showSizeChanger={showSizeChanger} showTotal={showTotal} {...rest} />
}

/** 统一表格：分页默认显示条数切换与总数，可传 pagination 覆盖。 */
export function AppTable<T extends object>({ pagination, ...rest }: TableProps<T>) {
  const merged = pagination === false
    ? false
    : { showSizeChanger: true, showTotal: defaultTotal, ...pagination }
  return <Table<T> {...rest} pagination={merged} />
}

function defaultTotal(total: number): string {
  return `共 ${total} 条`
}
