<#
演示库复位脚本（仅演示库执行）。

用法：
  # 1) 演示前记录基线：种子演示文档 + 种子提交文档，另可追加要保留的文档 ID
  .\scripts\demo-reset.ps1 -Snapshot
  .\scripts\demo-reset.ps1 -Snapshot -KeepIds 0123456789ABCDEF0123456789ABCDEF

  # 2) 演示后复位（可连续执行，第二次各步行数为 0）
  .\scripts\demo-reset.ps1

约定：
  - 通过 oracle-dev 容器执行，NLS_LANG=AMERICAN_AMERICA.AL32UTF8，保证中文标题匹配。
  - 基线存 edudoc_demo_baseline_doc；不在基线的 student/teacher 文档会被软删除。
  - 数据库连接默认 user_cidas/cidas123@//localhost:1521/FREEPDB1。
#>
param(
    [switch]$Snapshot,
    [string[]]$KeepIds = @(),
    [string]$Container = 'oracle-dev',
    [string]$DbUser = 'user_cidas',
    [string]$DbPassword = 'cidas123',
    [string]$DbConnect = '//localhost:1521/FREEPDB1'
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path

function Invoke-DemoSql {
    param([string]$Path)

    $remote = "/tmp/" + [IO.Path]::GetFileName($Path)
    & docker cp $Path "${Container}:$remote" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "docker cp 失败：$Path" }
    try {
        & docker exec -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 $Container sqlplus -s "$DbUser/$DbPassword@$DbConnect" "@$remote"
        if ($LASTEXITCODE -ne 0) { throw "sqlplus 执行失败：$Path" }
    }
    finally {
        # docker cp 写入的文件属 root，容器默认 oracle 用户删不掉
        & docker exec -u 0 $Container rm -f $remote | Out-Null
    }
}

function Invoke-Snapshot {
    $keeps = foreach ($raw in $KeepIds) {
        $id = $raw.Trim().ToUpperInvariant()
        if ($id -notmatch '^[0-9A-F]{32}$') {
            throw "KeepIds 不是 32 位十六进制：$raw"
        }
        @"
    INSERT INTO edudoc_demo_baseline_doc (document_id)
    SELECT HEXTORAW('$id') FROM dual
     WHERE NOT EXISTS (SELECT 1 FROM edudoc_demo_baseline_doc b WHERE b.document_id = HEXTORAW('$id'));
    DBMS_OUTPUT.PUT_LINE('STEP KEEP $id rows=' || SQL%ROWCOUNT);
"@
    }
    $keepSql = ($keeps -join "`n")

    $sql = @"
SET SERVEROUTPUT ON SIZE UNLIMITED
SET FEEDBACK OFF
SET DEFINE OFF
WHENEVER SQLERROR EXIT FAILURE

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM user_tables WHERE table_name = 'EDUDOC_DEMO_BASELINE_DOC';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE edudoc_demo_baseline_doc (
            document_id RAW(16) NOT NULL,
            snapshot_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
            CONSTRAINT pk_demo_baseline_doc PRIMARY KEY (document_id))';
    END IF;
END;
/

BEGIN
    INSERT INTO edudoc_demo_baseline_doc (document_id)
    SELECT d.id FROM edudoc_document d
     WHERE (d.id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2')
            OR d.title = '课程设计报告（演示：版本对比）')
       AND NOT EXISTS (SELECT 1 FROM edudoc_demo_baseline_doc b WHERE b.document_id = d.id);
    DBMS_OUTPUT.PUT_LINE('STEP 0.1 基线-演示文档 rows=' || SQL%ROWCOUNT);

    INSERT INTO edudoc_demo_baseline_doc (document_id)
    SELECT d.id FROM edudoc_document d JOIN edudoc_user u ON u.id = d.owner_id
     WHERE u.username IN ('student', 'teacher', '2023001', '2023002', '2023003', '2023004')
       AND d.title LIKE 'Web开发课程设计报告\_%' ESCAPE '\'
       AND NOT EXISTS (SELECT 1 FROM edudoc_demo_baseline_doc b WHERE b.document_id = d.id);
    DBMS_OUTPUT.PUT_LINE('STEP 0.2 基线-种子提交文档 rows=' || SQL%ROWCOUNT);

$keepSql
    COMMIT;
END;
/

PROMPT --- 基线清单 ---

SELECT RAWTOHEX(b.document_id) AS document_id, u.username, d.title
  FROM edudoc_demo_baseline_doc b
  JOIN edudoc_document d ON d.id = b.document_id
  JOIN edudoc_user u ON u.id = d.owner_id
 ORDER BY u.username, d.title;

PROMPT --- student/teacher 文档预览：KEEP=保留，DELETE=复位时软删除 ---

SELECT CASE WHEN EXISTS (SELECT 1 FROM edudoc_demo_baseline_doc b WHERE b.document_id = d.id)
            THEN 'KEEP' ELSE 'DELETE' END AS action,
       RAWTOHEX(d.id) AS document_id, u.username, d.title, d.status
  FROM edudoc_document d JOIN edudoc_user u ON u.id = d.owner_id
 WHERE u.username IN ('student', 'teacher')
 ORDER BY action DESC, u.username, d.title;
"@

    $tmp = Join-Path $env:TEMP ("edudoc-demo-snapshot-{0}.sql" -f [Guid]::NewGuid().ToString('N'))
    [IO.File]::WriteAllText($tmp, $sql, (New-Object System.Text.UTF8Encoding($false)))
    try {
        Invoke-DemoSql -Path $tmp
    }
    finally {
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
}

if ($Snapshot) {
    Invoke-Snapshot
}
else {
    Invoke-DemoSql -Path (Join-Path $here 'demo-reset.sql')
}