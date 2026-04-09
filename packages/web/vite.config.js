import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

const isVitest = process.env.VITEST === "true";
const elementPlusResolver = ElementPlusResolver({
  importStyle: isVitest ? false : "css"
});

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const devApiProxyTarget = env.VITE_DEV_API_PROXY_TARGET || "http://localhost:8080";

  return {
    plugins: [
      vue(),
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

