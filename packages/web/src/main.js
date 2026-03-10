import { createApp } from "vue";
import App from "./App.vue";
import "./style.css";

// ONLYOFFICE Vue 组件内部会把编辑器实例挂到 window.DocEditor.instances。
// 这里提前声明全局对象，便于宿主页按钮直接调用 docEditor.insertImage(...)。
window.DocEditor = window.DocEditor || { instances: {} };

// 前端入口保持最小化，只负责挂载根组件和全局样式。
createApp(App).mount("#app");
