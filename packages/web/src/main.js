import { createApp } from "vue";
import App from "./App.vue";
import "./style.css";
import router from "./router";

// ONLYOFFICE 官方 Vue 组件会把编辑器实例挂到 window.DocEditor.instances。
// 我们的宿主页并不会自己 new 一个编辑器，而是通过这个全局对象拿到已挂载实例，
// 再从控制台按钮里调用 insertImage、重新加载等运行态能力。
window.DocEditor = window.DocEditor || { instances: {} };

// 前端入口只做三件事：
// 1. 加载全局样式；
// 2. 挂载 Vue 根组件；
// 3. 接入路由，让“工作台首页 / 独立编辑页”拥有稳定的 URL 语义。
createApp(App).use(router).mount("#app");
