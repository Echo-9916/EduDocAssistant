import { useEffect, useState } from 'react'

type SmokeResult = {
  backend?: string
  db?: { status?: string; roleCount?: number }
  ai?: { service?: string; python?: string; status?: string }
}

export default function App() {
  const [result, setResult] = useState<SmokeResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/smoke')
      .then((r) => r.json())
      .then((r) => setResult(r.data ?? r))
      .catch((e) => setError(String(e)))
  }, [])

  return (
    <main>
      <h1>CIDAS 冒烟测试</h1>
      {error && <p className="red">请求失败：{error}</p>}
      {!error && !result && <p>请求中…</p>}
      {result && (
        <table>
          <tbody>
            <Row name="后端 backend" ok={result.backend === 'ok'} detail={result.backend} />
            <Row
              name="数据库 Oracle"
              ok={result.db?.status === 'ok'}
              detail={`${result.db?.status}，角色数 ${result.db?.roleCount}`}
            />
            <Row
              name="AI 服务 ai-service"
              ok={result.ai?.status === 'ok'}
              detail={`${result.ai?.service} / python ${result.ai?.python}`}
            />
          </tbody>
        </table>
      )}
    </main>
  )
}

function Row({ name, ok, detail }: { name: string; ok: boolean; detail?: string }) {
  return (
    <tr>
      <td>{name}</td>
      <td className={ok ? 'green' : 'red'}>{ok ? '✓' : '✗'}</td>
      <td>{detail}</td>
    </tr>
  )
}
