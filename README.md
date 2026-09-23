# EricJC Music

毕业设计项目：基于 Vue 3、Electron、Spring Boot 的 EricJC Music 播放器系统。

## 技术栈

- 前端：Vue 3 + Vite + TypeScript
- 桌面端：Electron
- 后端：Spring Boot 3
- 数据库：MySQL 8，开发早期可先使用接口模拟数据

## 项目结构

```text
ericjc-music-player/
  frontend/     Vue + Electron 客户端
  backend/      Spring Boot 服务端
    uploads/    音频、封面、歌词等静态资源
  docs/         需求、设计和答辩材料草稿
```

## 静态资源目录

开发阶段的歌曲、封面和歌词放在后端的 `uploads` 目录：

```text
backend/uploads/
  audio/   mp3 等音频文件
  covers/  jpg、png 等专辑封面
  lyrics/  lrc 歌词文件
```

后端会把它们映射到 `/uploads/**`，例如：

```text
http://localhost:18080/uploads/audio/after-rain.mp3
http://localhost:18080/uploads/covers/after-rain.jpg
http://localhost:18080/uploads/lyrics/after-rain.lrc
```

## 第一阶段目标

1. 完成播放器主界面。
2. 完成基础播放控制交互。
3. 完成后端歌曲列表接口。
4. 前后端联调。
5. 再加入用户、歌单、收藏、播放历史和后台管理。

## 本地运行

日常开发需要同时启动两个服务：

- 后端 Spring Boot：提供歌曲接口、封面、音频等静态资源，端口 `18080`
- 网页端 Vite：提供浏览器页面，并代理 `/api` 和 `/uploads` 请求，端口 `5173`

建议先启动后端，再启动网页端。

首次在一台电脑上运行时，先完成下方的数据库和本机配置。MySQL 服务也需要处于运行状态。

### 1. 启动后端

打开一个终端：

```bash
cd /Users/ericjc/Documents/ericjc-music-player/backend
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
```

看到类似下面的日志，说明后端启动成功：

```bash
Tomcat started on port 18080
```

后端接口地址：

```text
http://localhost:18080/api/songs
```

### 2. 启动网页端

再打开一个新的终端：

```bash
cd /Users/ericjc/Documents/ericjc-music-player/frontend
npm run dev
```

看到类似下面的日志，说明网页端启动成功：

```bash
Local: http://localhost:5173/
```

浏览器打开：

```text
http://localhost:5173/
```

也可以打开：

```text
http://127.0.0.1:5173/
```

## 关闭项目

分别在后端终端和网页端终端里按：

```bash
Ctrl + C
```

两个服务都停止后，网页端地址 `http://localhost:5173/` 就无法访问，这是正常的。

## 常用检查

检查后端是否启动：

```bash
curl http://localhost:18080/api/health
curl http://localhost:18080/api/songs
```

检查网页端是否启动：

```bash
curl http://localhost:5173/
```

## 依赖安装

第一次运行或 `node_modules` 丢失时，需要安装前端依赖：

```bash
cd /Users/ericjc/Documents/ericjc-music-player/frontend
npm install
```

平时只是启动项目，不需要每次执行 `npm install`。

## Windows 上运行

从 GitHub 克隆项目后，先安装这些环境：

- Git
- Node.js LTS
- JDK 17 或更高版本
- Maven
- MySQL 8

克隆项目：

```bash
git clone https://github.com/Er1cJC/ericjc-music-player.git
cd ericjc-music-player
```

安装前端依赖：

```bash
cd frontend
npm install
```

准备数据库：

```sql
CREATE DATABASE music_player CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'musicplayer'@'localhost' IDENTIFIED BY 'REPLACE_WITH_YOUR_DATABASE_PASSWORD';
GRANT ALL PRIVILEGES ON music_player.* TO 'musicplayer'@'localhost';
FLUSH PRIVILEGES;
```

把 SQL 中的 `REPLACE_WITH_YOUR_DATABASE_PASSWORD` 替换为你自己设置的密码。已有数据库和用户的电脑无需重复创建。

在项目根目录复制本机配置模板。macOS：

```bash
cp backend/config/local.example.yml backend/config/local.yml
openssl rand -base64 32
```

Windows PowerShell：

```powershell
Copy-Item backend/config/local.example.yml backend/config/local.yml
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
$rng.Dispose()
```

编辑 `backend/config/local.yml`：填入数据库密码，并把生成的 Base64 字符串填到 `app.jwt.secret`。该文件已被 Git 忽略，每台电脑各自配置。也可以通过 `DB_PASSWORD` 和 `JWT_SECRET` 环境变量配置，此时无需本机配置文件。

从 `backend` 目录启动，Spring Boot 会自动读取 `config/local.yml`：

```bash
cd backend
mvn spring-boot:run
```

再打开一个终端，从项目根目录启动网页端：

```bash
cd frontend
npm run dev
```

浏览器打开 Vite 显示的地址，通常是 `http://localhost:5173/`。macOS 的绝对路径命令不能直接在 Windows 使用，Windows 请使用上述相对路径命令。

注意：`backend/uploads` 下的歌曲、封面、歌词属于运行时资源，不会上传到 GitHub。换电脑后需要自己把音频、封面、歌词文件复制到对应目录，或者后续使用后台上传功能重新上传。

## 构建检查

构建检查不是日常启动项目必须步骤。只有在改完代码、想确认能否正常打包时运行。

网页端构建：

```bash
cd /Users/ericjc/Documents/ericjc-music-player/frontend
npm run build
```

后端构建：

```bash
cd /Users/ericjc/Documents/ericjc-music-player/backend
mvn -q -DskipTests package
```

## 桌面端运行

```bash
cd /Users/ericjc/Documents/ericjc-music-player/frontend
npm run electron:dev
```

桌面端目前复用网页端界面。日常开发优先使用网页端即可。

## GitHub 维护方式

仓库地址：https://github.com/Er1cJC/ericjc-music-player

项目已初始化 Git 并设置 `origin`，日常开发无需再执行 `git init` 或 `git remote add`。

每次开始开发前，在项目根目录执行：

```bash
git status
git pull --ff-only
```

完成一批改动后，检查并提交：

```bash
git status
git diff
git add .
git commit -m "说明这次改了什么"
git push
```

例如提交说明可以写 `完善搜索和歌单功能`。提交保存在本机，`git push` 才会上传到 GitHub。网页开发时保存文件就会热更新，不需要每次保存都提交。

从 macOS 切换到 Windows，或反过来：先在原电脑提交并推送，再在另一台电脑的项目根目录执行：

```bash
git pull --ff-only
```

如果 `git status` 显示还有本地改动，先提交，或用 `git stash push -m "临时保存"` 暂存，再拉取，最后用 `git stash pop` 恢复。遇到冲突或 `--ff-only` 失败时，先处理两边的改动；不要使用强制推送覆盖另一台电脑的提交。

拉取后如果 `frontend/package-lock.json` 更新，在 `frontend` 目录执行 `npm ci`；其他情况下不必重新安装依赖。

GitHub 同步代码，不会同步 MySQL 中的用户、收藏等数据，也不会同步被忽略的媒体文件和本机配置。换电脑时这些需要单独准备。

首次推送可能需要 GitHub 登录。HTTPS 认证使用 GitHub 的授权登录或个人访问令牌，不能使用 GitHub 账号密码。不要把令牌写进代码或远程地址。

不要提交这些内容：

- `frontend/node_modules`
- `frontend/dist`
- `backend/target`
- `.DS_Store`
- 大型音频文件、封面图片、上传文件
- `backend/config/local.yml`、`.env` 等密码和密钥配置

这些都已经写进 `.gitignore`。

## 下一步开发顺序

1. 设计 MySQL 表：用户、歌曲、歌手、专辑、歌单、收藏、播放历史。
2. 实现登录注册。
3. 将歌曲、收藏、歌单从前端临时状态迁移到后端接口和数据库。
4. 实现后台歌曲上传。
5. 加入歌词解析与滚动歌词展示。
