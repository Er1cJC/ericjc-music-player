# Codex 接手文档（EricJC Music）

> 本文档由前一位维护者（DSH）在 Windows 迁移完成后编写。接手前先读一遍，可以省去大量踩坑时间。

## 一、项目概况

- 毕业设计音乐播放器。前端 Vue 3 + Vite + TypeScript（`frontend/`），后端 Spring Boot 3 + MySQL 8（`backend/`），JWT 登录认证。
- 仓库：https://github.com/Er1cJC/ericjc-music-player （remote 为 SSH）
- 前端是单文件组件：`frontend/src/App.vue`（约 1500 行），样式集中在 `frontend/src/styles.css`；后端入口包 `com.ericjc.musicplayer`。

## 二、本机环境（Windows，LEGION-R7000）

| 工具 | 位置 / 版本 | 备注 |
|---|---|---|
| Java | `E:\Program\Jvav\jdk-22`（22.0.2） | 启动前手动设 `$env:JAVA_HOME` |
| Maven | IntelliJ 自带：`E:\Program\IntelliJ IDEA 2024.3.4\plugins\maven\lib\maven3\bin\mvn.cmd` | **系统没有独立 Maven，`mvn` 不在 PATH** |
| Node / npm | v22.17.0 / 10.9.2 | |
| Git | 2.51.0 | SSH 已配置：`~/.ssh/id_rsa` 已加入 GitHub 账号，remote 走 `ssh.github.com:443` |
| MySQL | 服务名 `MySQL80`（开机自启） | 客户端在 `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`，**不在 PATH** |

## 三、启动方式

后端（端口 18080）：

```powershell
cd backend
$env:JAVA_HOME = 'E:\Program\Jvav\jdk-22'
& 'E:\Program\IntelliJ IDEA 2024.3.4\plugins\maven\lib\maven3\bin\mvn.cmd' spring-boot:run
```

前端（端口 5173）：

```powershell
cd frontend
npm run dev
```

- Vite 代理：`/api`、`/uploads` → `http://localhost:18080`
- Windows 防火墙弹窗选"允许"；后端日志出现 `Tomcat started on port 18080` 即成功

## 四、数据库与配置

- 库 `music_player`（utf8mb4_unicode_ci），应用用户 `musicplayer`。
- **密码与 JWT secret 都在 `backend/config/local.yml`**（该文件被 gitignore，不上库；JWT secret 是 Base64 随机串）。
- `application.yml` 中 `spring.sql.init.mode: always`，`data.sql` 用**条件插入**（id 不存在才插）——管理员删除的内置歌**重启不会复活**，不要改回 `INSERT IGNORE` 的写法。
- 表由 JPA 自动建（`ddl-auto: update`）。

## 五、当前数据状态（2026-10-13）

- 用户：`test`（USER）、`EricJC`（ADMIN）。
- 歌曲 19 首：内置 Echoes、Goldener Schnitt、Highscore、星降る海 4 首（**义勇军进行曲已被用户删除**，如需恢复请用户从 Mac 拷文件重新上传）+ 用户批量导入的 15 首周杰伦歌曲。
- 媒体文件都在 `backend/uploads/`（audio/covers/lyrics，gitignore 忽略，不提交）。
- **遗留缺口**：`backend/uploads/lyrics/Highscore.lrc` 缺失（用户删歌时被误删，数据库引用已恢复，等用户从 Mac 补拷）。
- 数据库曾从 `music_player_backup.sql` 导入（用户的 Mac 备份）。

## 六、近期重要修复（别把坑改回去）

1. **删除歌曲**：后端先清收藏/播放历史/歌单引用（三个 Repository 的 `deleteBySongId`，带事务），再删媒体文件（**URL 先 UTF-8 解码**再找磁盘文件——数据库里存的是 `%20`/`%E4...` 编码 URL），最后删记录；前端删除成功后清理正在播放状态、最近播放、收藏、歌单引用。
2. **`/error` 已 permitAll**：之前任何 500 错误经 `/error` 转发会被伪装成 401"未登录"，现已放行，真实错误原样返回。
3. **编辑歌曲**：封面/音频/歌词改为选文件上传（不是 URL 输入），时长自动识别（music-metadata 解析，失败用 `<audio>` 兜底）。
4. **歌词界面**：每行固定 44px 行高，当前句中心始终对齐容器中线；开头第一句在中心线下方待命；容器 `::before`(50%) / `::after`(50%-22px) 占位实现首尾句都能居中——改行高时需同步 JS 里动态读取的逻辑（JS 已动态读 `offsetHeight`，伪元素写死了 22px 半行）。
5. **播放页布局**：横屏（`@media (orientation: landscape)`）唱片左歌词右，竖屏上下；唱片尺寸 `clamp()` 自适应并有 0.5s 过渡。
6. **歌词页只有顶部按钮能收起**（overlay 上的 `@click.self` 已移除）。

## 七、协作约定（用户要求，继续遵守）

- 每次修改完代码：验证通过后**自动 commit（提交说明用中文）并 push**，不需要停下来等用户确认。
- 动手前先 `git pull --ff-only`。
- 测试产生的数据（账号、歌曲、上传文件）清理干净再收工。
- 注意 `frontend/package-lock.json` 有平台差异：Windows 上 npm install 会移除 macOS 的 optional 条目，Mac 上会加回来——提交时留意避免无意义的往返改动。
- Windows 注意事项：PowerShell 5.1 语法坑（字符串解析、外部命令 stderr 会被当错误）、curl 传 `-d` JSON 时引号会被 Windows 剥掉（测试时用 Invoke-RestMethod 或把 JSON 写入临时文件）。

## 八、当前运行状态

- 前端 dev server 与后端此前由 DSH 的后台任务托管；**DSH 会话结束后这些进程会终止**，Codex 接手后需自行按第三节命令启动。

## 九、快速验证命令

```powershell
curl http://localhost:18080/api/health     # {"status":"ok"}
curl http://localhost:18080/api/songs      # 19 首歌曲 JSON
curl http://localhost:5173/                # HTTP 200
```
