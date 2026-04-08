# Phase 10: element-plus-element-plus - Research

## Research Objective
Identify all UI elements, CSS files, and Vue components that need to be refactored to use Element Plus, and define the specific implementation paths based on the `10-CONTEXT.md` decisions.

## Codebase Findings

### Target Files for Refactoring
Research into `packages/web` reveals the following key UI components and pages that contain custom CSS and markup which need to be replaced with Element Plus components (`el-table`, `el-button`, `el-card`, `el-container`, `el-message`, etc.):

1. **Pages (Layout & Structure):**
   - `DocumentLibraryPage.vue` - The main library page. Currently uses a custom header and layout.
   - `DocumentEditorPage.vue` - Replaces layout with `<el-container>`, `<el-header>`, `<el-main>`.
   - `DocumentPreviewPage.vue` - Similar structural refactor.

2. **Components (UI & Interaction):**
   - `library/DocumentCreateActions.vue` - Contains buttons for creating, uploading, importing. Replace with `el-button` and `@element-plus/icons-vue`. Replace dialogs/modals with `el-dialog`.
   - `library/DocumentList.vue` - The core document list. Needs to be completely refactored to use `<el-table>` (Decision D-03).
   - `editor/EditorShell.vue` - Needs an `el-drawer` or `el-aside` for the sidebar layout, `<el-page-header>` for back actions, and `<el-button>` for actions.

3. **Global Styling & Setup:**
   - `style.css` - Much of the custom flexbox/layout, button hovering, and table styling can be deleted, as Element Plus will provide these out-of-the-box.
   - `package.json` & `vite.config.js` - Need to add `element-plus`, `@element-plus/icons-vue`, `unplugin-vue-components`, and `unplugin-auto-import` (Decision D-01).

### Key Execution Nuances
- **Table Data Mapping:** For `DocumentList.vue`, the `el-table` needs columns for Title, Author (tenantUser), Last Saved Time, Status, Storage Available flag, and Actions.
- **Routing unchanged:** The router configs (`router/index.js`) and API calls (`lib/api.js`) must NOT be modified. Only the presentation layer changes.
- **Iconography:** All `<svg>` or custom icons to be replaced with `@element-plus/icons-vue` imported via `unplugin-auto-import` or manually locally.

## Validation Architecture

1. **Build Validation:** `npm run build` inside `packages/web` should succeed without type or compilation errors after Element Plus integration.
2. **Visual Validation:** 
   - `DocumentList.vue` must contain `<el-table>` rather than custom `div` lists.
   - `DocumentCreateActions.vue` must use `<el-button>` components.
3. **Bundle Validation:** `package.json` must include `element-plus` and the `unplugin` packages.

## Conclusion and Recommendations
The planner can proceed to create 2 waves:
1. **Wave 1:** Dependency installation, Vite `unplugin` configurations, and `style.css` cleanup.
2. **Wave 2:** Component refactoring (Pages, Library Components, Editor components) using `el-table`, `el-button`, `el-dialog`, etc.
