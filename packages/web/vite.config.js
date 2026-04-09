import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

const isVitest = process.env.VITEST === "true";
// 测试环境里不自动注入 Element Plus 的 CSS，避免 jsdom 因样式副作用变慢或报错。
const elementPlusResolver = ElementPlusResolver({
  importStyle: isVitest ? false : "css"
});

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const devApiProxyTarget = env.VITE_DEV_API_PROXY_TARGET || "http://localhost:8080";

  return {
    plugins: [
      vue(),
      // 自动按需引入 Element Plus 组件与 API，减少页面里重复 import。
      AutoImport({
        resolvers: [elementPlusResolver],
      }),
      Components({
        resolvers: [elementPlusResolver],
      }),
    ],
    server: {
      host: "0.0.0.0",
      port: 5173,
      headers: {
        // ONLYOFFICE iframe 在本地调试时会跨端口加载隐藏插件资源，需要放开 CORS。
        "Access-Control-Allow-Origin": "*"
      },
      proxy: {
        "/api": {
          target: devApiProxyTarget,
          changeOrigin: true
        }
      }
    },
    test: {
      environment: "jsdom",
      globals: true,
      setupFiles: "./src/test/setup.js",
      server: {
        deps: {
          inline: ["element-plus", "@element-plus/icons-vue"]
        }
      }
    }
  };
});

