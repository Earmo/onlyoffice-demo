import { createRouter, createWebHistory } from "vue-router";
import DocumentEditorPage from "../pages/DocumentEditorPage.vue";
import DocumentLibraryPage from "../pages/DocumentLibraryPage.vue";
import DocumentPreviewPage from "../pages/DocumentPreviewPage.vue";

// 路由层明确把“工作台首页”和“独立编辑页”拆成两个入口：
// - "/" 负责文档列表、搜索筛选和新建/上传/导入入口；
// - "/preview/:documentId" 负责只读预览；
// - "/editor/:documentId" 负责可编辑工作台。
// 这样列表页可以明确区分“查看文件”和“编辑文档”两种产品意图。
const routes = [
  {
    path: "/",
    name: "library",
    component: DocumentLibraryPage
  },
  {
    path: "/preview/:documentId",
    name: "preview",
    component: DocumentPreviewPage,
    props: true
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
