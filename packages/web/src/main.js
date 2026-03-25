import { createApp } from "vue";
import App from "./App.vue";
import "./style.css";
import router from "./router";

// ONLYOFFICE Vue 组件内部会把编辑器实例挂到 window.DocEditor.instances。
// 这里提前声明全局对象，便于宿主页按钮直接调用 docEditor.insertImage(...)。
window.DocEditor = window.DocEditor || { instances: {} };

// 前端入口现在同时负责挂载路由，让首页工作台和独立编辑页拥有清晰 URL 语义。
createApp(App).use(router).mount("#app");
