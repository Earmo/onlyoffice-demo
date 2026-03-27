import { createRouter, createWebHistory } from "vue-router";
import DocumentEditorPage from "../pages/DocumentEditorPage.vue";
import DocumentLibraryPage from "../pages/DocumentLibraryPage.vue";

// 路由层明确把“工作台首页”和“独立编辑页”拆成两个入口：
// - "/" 负责文档列表、搜索筛选和新建/上传/导入入口；
// - "/editor/:documentId" 负责单文档编辑运行态。
// 这样既符合产品心智，也方便后续直接分享编辑页链接。
const routes = [
  {
    path: "/",
    name: "library",
    component: DocumentLibraryPage
  },
  {
    path: "/editor/:documentId",
    name: "editor",
    component: DocumentEditorPage,
    props: true
  }
];

export default createRouter({
  // 当前项目按浏览器历史模式部署，Nginx 会把前端路由回退到 index.html。
  history: createWebHistory(),
  routes
});
