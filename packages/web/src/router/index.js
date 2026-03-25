import { createRouter, createWebHistory } from "vue-router";
import DocumentEditorPage from "../pages/DocumentEditorPage.vue";
import DocumentLibraryPage from "../pages/DocumentLibraryPage.vue";

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
  history: createWebHistory(),
  routes
});
