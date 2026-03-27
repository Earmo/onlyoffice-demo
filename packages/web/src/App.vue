<script setup>
import { RouterView } from "vue-router";
</script>

<template>
  <!--
    顶层 App 故意保持极薄，只承担路由出口职责。
    这样首页工作台和独立编辑页的页面壳层都交给各自页面管理，
    避免把业务布局、编辑器运行态和列表页状态揉进根组件里。
  -->
  <RouterView />
</template>
