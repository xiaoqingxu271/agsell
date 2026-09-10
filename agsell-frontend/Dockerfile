# 管理端多阶段构建：Node 构建 → Nginx 托管
FROM node:24-alpine AS build
WORKDIR /app
# 使用国内 npm 镜像加速（replace-registry-host 让 lock 中的 URL 也走镜像）+ 断线重试 + 限并发 + BuildKit 缓存
RUN npm config set registry https://registry.npmmirror.com \
    && npm config set replace-registry-host npmjs \
    && npm config set fetch-retries 10 \
    && npm config set fetch-retry-mintimeout 2000 \
    && npm config set fetch-retry-maxtimeout 120000 \
    && npm config set maxsockets 4
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
