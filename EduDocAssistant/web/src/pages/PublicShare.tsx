// /s/:token 免登录只读页：展示当前版本标题与结构化正文（标题/正文/列表/表格/代码），不提供下载/文件对象。
import { createElement } from 'react'
import { useEffect, useState } from 'react'
import { Card, Result, Space, Spin, Tag, Typography } from 'antd'
import { useParams } from 'react-router-dom'
import { getErrorMessage } from '../api/http'
import { getPublicShare } from '../api/shares'
import type { PublicShare as PublicShareData } from '../api/shares'
import type { PreviewBlock, StructureNode } from '../api/ai'
import { buildBlockUnits, markerHeadings } from './view/structure'
import './view/structure.css'

export default function PublicShare() {
  const { token = '' } = useParams<{ token: string }>()
  const [data, setData] = useState<PublicShareData | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let alive = true
    getPublicShare(token)
      .then((d) => alive && setData(d))
      .catch((e) => alive && setError(getErrorMessage(e)))
    return () => {
      alive = false
    }
  }, [token])

  return (
    <div style={{ minHeight: '100vh', background: '#f6f8fa', padding: '40px 16px' }}>
      <Card style={{ maxWidth: 900, margin: '0 auto' }}>
        {error ? (
          <Result status="warning" title="共享链接不可用" subTitle={error} />
        ) : !data ? (
          <Spin style={{ display: 'block', padding: 48, textAlign: 'center' }} />
        ) : (
          <>
            <Space wrap align="baseline" style={{ marginBottom: 8 }}>
              <Typography.Title level={4} style={{ margin: 0 }}>{data.title}</Typography.Title>
              <Tag color={data.permission === 'COMMENT' ? 'blue' : 'default'}>
                {data.permission === 'COMMENT' ? '可批注' : '只读'}
              </Tag>
              {data.versionNo != null && <Tag>v{data.versionNo}</Tag>}
            </Space>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
              通过共享链接查看当前版本，仅展示文本内容。
            </Typography.Paragraph>
            <div className="doc-structure">
              {renderDocument(data.blocks ?? [], data.structure ?? [], data.text)}
            </div>
          </>
        )}
      </Card>
    </div>
  )
}

/** 结构化只读渲染：与预览页同一套 block/structure 坐标，不做高亮与批注交互。 */
function renderDocument(blocks: PreviewBlock[], structure: StructureNode[], fallback: string) {
  if (blocks.length === 0) {
    return <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', lineHeight: 1.9, marginBottom: 0 }}>
      {fallback || '该版本暂无可读文本。'}
    </Typography.Paragraph>
  }
  return blocks.map((block) => {
    const markers = markerHeadings(block, structure)
    const units = buildBlockUnits(block, structure)
    if (units.length === 0 && markers.length === 0) {
      return <p key={block.chunkId} style={{ whiteSpace: 'pre-wrap' }}>{block.text}</p>
    }
    return (
      <div key={block.chunkId}>
        {markers.map((m) => createElement(
          `h${Math.min(4, Math.max(1, m.level))}`,
          { key: `marker-${m.nodeNo}` },
          m.text,
        ))}
        {units.map((unit, i) => {
          if (unit.type === 'heading') {
            return createElement(
              `h${Math.min(4, Math.max(1, unit.level))}`,
              { key: `unit-${i}` },
              unit.text,
            )
          }
          if (unit.type === 'paragraph') {
            return <p key={`unit-${i}`}>{unit.text}</p>
          }
          if (unit.type === 'code') {
            return (
              <pre key={`unit-${i}`} style={{
                padding: '8px 10px', borderRadius: 4, background: '#f6f8fa',
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
              }}>
                <code>{unit.text}</code>
              </pre>
            )
          }
          if (unit.type === 'list') {
            return (
              <ol key={`unit-${i}`} start={unit.items[0]?.ordinal ?? 1}>
                {unit.items.map((item) => <li key={item.nodeNo}>{item.text}</li>)}
              </ol>
            )
          }
          return (
            <table key={`unit-${i}`}>
              <tbody>
                {unit.rows.map((row, ri) => (
                  <tr key={`row-${ri}`}>
                    {row.cells.map((cell) => <td key={cell.nodeNo}>{cell.text}</td>)}
                  </tr>
                ))}
              </tbody>
            </table>
          )
        })}
      </div>
    )
  })
}