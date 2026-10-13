# EricJC Music

毕设做的音乐播放器。网页端、桌面端、手机浏览器都能用，歌曲和用户数据都存在自己跑起来的后端里。

## 已经有什么功能

- 播放控制：播放/暂停、上一首/下一首、随机播放、进度条拖动、音量，播完自动下一首
- 搜索歌曲、歌手、专辑
- 注册登录（JWT）
- 收藏歌曲、播放历史
- 歌单：新建、改名、删除，往歌单里加歌、移出
- 歌词展示，跟着播放滚动
- 管理后台：歌曲增删改、批量导入（一次选几十首歌，自动读标题/歌手/封面）、用户管理、文件上传
- Electron 桌面端（界面和网页端一样）

## 技术栈

- 前端：Vue 3 + Vite + TypeScript
- 桌面：Electron
- 后端：Spring Boot 3 + MySQL 8

## 目录

```text
frontend/   网页和 Electron 客户端
backend/    Spring Boot 后端
  uploads/  歌曲、封面、歌词文件，后端通过 /uploads/** 提供访问
docs/       需求文档之类的草稿
```

## 怎么跑起来

日常开发需要两个终端，先起后端，再起网页端。

### 第一次跑之前

1. MySQL 里建好库和用户：

```sql
CREATE DATABASE music_player CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'musicplayer'@'localhost' IDENTIFIED BY '你的密码';
GRANT ALL PRIVILEGES ON music_player.* TO 'musicplayer'@'localhost';
```

表不用手动建，后端启动时会自动建。

2. 复制一份本机配置：

```bash
cp backend/config/local.example.yml backend/config/local.yml
```

打开 `local.yml`，填上数据库密码，再生成一段 Base64 随机串填到 `app.jwt.secret`：

```bash
openssl rand -base64 32
```

这个文件里有密码，不会进 git，每台电脑自己配一份。

3. 前端装依赖（只需要一次）：

```bash
cd frontend && npm install
```

### 日常启动

终端 1，后端（端口 18080）：

```bash
cd backend
mvn spring-boot:run
```

终端 2，网页（端口 5173）：

```bash
cd frontend
npm run dev
```

浏览器打开 http://localhost:5173。

后端没起来的时候，页面会显示几条内置的示例歌，所以"能看到歌"不代表连上了后端——登录、收藏这些功能才是真正走后端的。

## 手机怎么访问

手机和电脑连同一个 Wi-Fi：

```bash
cd frontend
npm run dev -- --host
```

然后用手机浏览器打开 `http://电脑的局域网IP:5173`。查电脑 IP 用：

```bash
ipconfig getifaddr en0
```

Vite 会把 `/api` 请求转发给本机后端，所以后端端口不用额外暴露。

## 打包

- 网页：`cd frontend && npm run build`
- 桌面：`cd frontend && npm run electron:dev`（开发调试），`npm run electron:build`（出安装包）
- 后端：`cd backend && mvn -q -DskipTests package`，产物在 `target/ericjc-music-backend-0.1.0.jar`，`java -jar` 就能跑

## Windows 上跑

代码是跨平台的，Windows 装好 JDK 17、Maven、MySQL 8、Node.js 就能跑，命令和上面一样，只有两处区别：

- 复制配置用 PowerShell：`Copy-Item backend/config/local.example.yml backend/config/local.yml`
- 后端启动命令就是 `mvn spring-boot:run`，不用写端口（端口已经写在 application.yml 里了）

`backend/uploads` 里的歌和封面不在 git 里，换电脑要自己拷过去，或者用后台上传/批量导入重新传。

## git

仓库在 https://github.com/Er1cJC/ericjc-music-player。

做完一批功能就提交推送：

```bash
git status
git diff
git add .
git commit -m "说明这次改了什么"
git push
```

换电脑继续开发时先 `git pull --ff-only`。`node_modules`、`dist`、`target`、`backend/config/local.yml`、`uploads` 里的媒体文件都不要提交，`.gitignore` 已经写好了。

## 以后想做的

- 部署到服务器，公网也能访问
- 手机 App（Capacitor 或 PWA）
- 歌单内排序、歌单搜索之类的小功能
