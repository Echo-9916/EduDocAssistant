# 登录认证接口 · 手动测试说明

> 覆盖 A-01~A-04 后端接口。Apifox 可直接导入 [apifox/auth-login.postman_collection.json](apifox/auth-login.postman_collection.json)；本文件给出每个接口的 URL、Header、Body 与预期返回，便于脱离 Apifox 手工验证。

## 环境与前置

- **Base URL**：`http://localhost:8080`（后端 `mvn spring-boot:run` 启动后）
- **鉴权头**：`Authorization: Bearer <token>`，`<token>` 来自登录成功返回的 `data.token`
- **请求体格式**：含 Body 的接口统一 `Content-Type: application/json`
- **统一响应 envelope**：

  ```json
  { "code": 200, "message": "success", "data": {}, "traceId": "…", "timestamp": "…" }
  ```

- **错误码**：`40001` 参数错误 · `40101` 未登录/账号受限 · `40102` 令牌已撤销
- **种子账号**（`db_seed/seed_accounts.csv`）：

  | username | password | 状态 |
  |----------|----------|------|
  | student  | Cidas@123 | 正常（STUDENT） |
  | teacher  | Cidas@123 | 正常（TEACHER） |
  | ops      | Cidas@123 | 正常（OPS） |
  | locked   | Cidas@123 | 锁定（登录被拒） |

---

## 1. 登录成功

- **Method / URL**：`POST /api/v1/auth/login`
- **Header**：`Content-Type: application/json`
- **Body**：

  ```json
  { "username": "student", "password": "Cidas@123" }
  ```

- **预期返回**：HTTP 200，`code=200`

  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "token": "eyJhbGciOiJIUzUxMiJ9.…（含 jti 声明）",
      "user": {
        "userId": "…（32 位大写 HEX）",
        "username": "student",
        "roles": ["STUDENT"],
        "permissions": ["…"],
        "courseScope": []
      }
    },
    "traceId": "…",
    "timestamp": "…"
  }
  ```

- **curl**：

  ```bash
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"student","password":"Cidas@123"}'
  ```

---

## 2. 登录失败 · 密码错误

- **Method / URL**：`POST /api/v1/auth/login`
- **Header**：`Content-Type: application/json`
- **Body**：

  ```json
  { "username": "student", "password": "wrong" }
  ```

- **预期返回**：HTTP 401，`code=40101`，`message=用户名或密码错误`，`data=null`

---

## 3. 登录失败 · 账号锁定

- **Method / URL**：`POST /api/v1/auth/login`
- **Header**：`Content-Type: application/json`
- **Body**：

  ```json
  { "username": "locked", "password": "Cidas@123" }
  ```

- **预期返回**：HTTP 401，`code=40101`，`message=账号受限，请联系管理员`，`data=null`

---

## 4. 当前用户 me

- **Method / URL**：`GET /api/v1/auth/me`
- **Header**：`Authorization: Bearer <token>`
- **Body**：无
- **预期返回**：HTTP 200，`code=200`，`data` 为 UserPrincipal：

  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "userId": "…",
      "username": "student",
      "roles": ["STUDENT"],
      "permissions": ["…"],
      "courseScope": []
    }
  }
  ```

- **curl**：

  ```bash
  curl http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer <token>"
  ```

---

## 5. 登出 logout

- **Method / URL**：`POST /api/v1/auth/logout`
- **Header**：`Authorization: Bearer <token>`
- **Body**：无
- **预期返回**：HTTP 204，无响应体（token 被服务端撤销）

---

## 6. 登出后复用旧 token（验证 40102）

- **Method / URL**：`GET /api/v1/auth/me`
- **Header**：`Authorization: Bearer <token>`（沿用第 5 步已登出的 token）
- **Body**：无
- **预期返回**：HTTP 401，`code=40102`，`message=令牌已撤销`，`data=null`

---

## 7. 找回密码（占位）

- **Method / URL**：`POST /api/v1/auth/reset-password`
- **Header**：`Content-Type: application/json`（可选，占位接口不校验 Body）
- **Body**：任意或空
- **预期返回**：HTTP 200，`code=200`，`message=该功能正在开发中`，`data=null`

---

## 8. 权限申请（占位）

- **Method / URL**：`POST /api/v1/auth/access-request`
- **Header**：`Content-Type: application/json`（可选）
- **Body**：任意或空
- **预期返回**：HTTP 200，`code=200`，`message=该功能正在开发中`，`data=null`

---

## 建议测试顺序

1. `1 登录成功` → 记录 `data.token`
2. `4 当前用户 me` → 用上面 token，应 200
3. `5 登出` → 204
4. `6 复用旧 token` → 40102
5. `2 密码错误` / `3 账号锁定` → 各 40101
6. `7 找回密码` / `8 权限申请` → 各 200 占位文案
