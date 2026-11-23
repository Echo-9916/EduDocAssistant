#!/usr/bin/env bash
# 一次性建库：等待 Oracle → 建表空间/用户 → 首次执行 01~04 脚本。
# 重复执行时检测 EDUDOC_USER 已存在则跳过，保证 docker compose up 幂等。
set -euo pipefail

: "${ORACLE_PWD:?ORACLE_PWD is required}"
DB_USER="${EDUDOC_DB_USERNAME:-user_cidas}"
DB_PASS="${EDUDOC_DB_PASSWORD:-cidas123}"
DB_USER_UPPER="$(echo "$DB_USER" | tr '[:lower:]' '[:upper:]')"

SYSTEM_CONN="system/${ORACLE_PWD}@//oracle:1521/FREEPDB1"
USER_CONN="${DB_USER}/${DB_PASS}@//oracle:1521/FREEPDB1"

echo "[db-init] 等待 Oracle 与 FREEPDB1 就绪（首次启动约 5-10 分钟）..."
ready=0
for _ in $(seq 1 180); do
  # 用专用标记判断：监听已起但 PDB 未注册时，sqlplus 可能仍返回 0
  OUT="$(echo "select 'READY' from dual;" | sqlplus -s "$SYSTEM_CONN" 2>&1 || true)"
  if echo "$OUT" | grep -q READY; then
    ready=1
    break
  fi
  sleep 5
done
if [ "$ready" != "1" ]; then
  echo "[db-init] Oracle 在 15 分钟内未就绪，退出"
  exit 1
fi
echo "[db-init] Oracle 已就绪"

echo "[db-init] 创建表空间与用户（已存在则跳过）..."
sqlplus -s "$SYSTEM_CONN" <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET DEFINE OFF
SET ECHO OFF
DECLARE
  v NUMBER;
BEGIN
  SELECT COUNT(*) INTO v FROM dba_tablespaces WHERE tablespace_name = 'TBS_CIDAS';
  IF v = 0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLESPACE TBS_CIDAS DATAFILE ''/opt/oracle/oradata/FREE/FREEPDB1/tbs_cidas.dbf'' SIZE 512M AUTOEXTEND ON NEXT 100M MAXSIZE 8G';
  END IF;
END;
/
DECLARE
  v NUMBER;
BEGIN
  SELECT COUNT(*) INTO v FROM dba_users WHERE username = '${DB_USER_UPPER}';
  IF v = 0 THEN
    EXECUTE IMMEDIATE 'CREATE USER ${DB_USER} IDENTIFIED BY "${DB_PASS}" DEFAULT TABLESPACE TBS_CIDAS';
  END IF;
END;
/
ALTER USER ${DB_USER} IDENTIFIED BY "${DB_PASS}";
GRANT CONNECT, RESOURCE TO ${DB_USER};
ALTER USER ${DB_USER} QUOTA UNLIMITED ON TBS_CIDAS;
SQL

echo "[db-init] 检查业务表..."
TABLE_COUNT=$(
  echo "SET PAGESIZE 0 FEEDBACK OFF HEADING OFF
SELECT COUNT(*) FROM dba_tables WHERE owner = '${DB_USER_UPPER}' AND table_name = 'EDUDOC_USER';" \
    | sqlplus -s "$SYSTEM_CONN" | tr -d '[:space:]'
)
if [ "$TABLE_COUNT" != "0" ]; then
  echo "[db-init] 已检测到 EDUDOC_USER，跳过建表"
  exit 0
fi

echo "[db-init] 首次初始化：执行 01_schema ..."
sqlplus -s "$USER_CONN" <<'SQL'
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET DEFINE OFF
SET ECHO OFF
@/schema/01_schema.sql
SQL

echo "[db-init] 执行 02_indexes（容器默认向量内存为 0 时向量索引会跳过，不影响功能）..."
sqlplus -s "$USER_CONN" > /tmp/indexes.log 2>&1 <<'SQL'
WHENEVER SQLERROR CONTINUE
SET DEFINE OFF
SET ECHO OFF
@/schema/02_indexes.sql
SQL
if grep -q "ORA-51962" /tmp/indexes.log; then
  echo "[db-init] 提示：向量索引未创建（向量内存不足），普通检索与智能处理仍可用"
fi

echo "[db-init] 执行 03_triggers / 04_seed ..."
sqlplus -s "$USER_CONN" <<'SQL'
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET DEFINE OFF
SET ECHO OFF
@/schema/03_triggers.sql
@/schema/04_seed.sql
SQL

echo "[db-init] 数据库初始化完成"