import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";

import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

const isVitest = process.env.VITEST === "true";
const elementPlusResolver = ElementPlusResolver({
  importStyle: isVitest ? false : "css"
});

export default defineConfig({
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
    port: 5173
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
});

