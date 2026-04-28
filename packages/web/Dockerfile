FROM node:22-alpine AS build
WORKDIR /app

# 构建阶段只先复制依赖清单，利用 Docker layer cache 加速二次构建。
COPY package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:1.25-alpine

ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata

# 运行阶段只保留静态产物和 nginx 配置，减小镜像体积。
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
