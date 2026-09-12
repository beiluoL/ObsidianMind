/**
 * 路由表（hash 模式）。
 * 注意：/note/:id 的 id 是 Vault 相对路径（可含多级 /），必须用自定义
 * parse 函数取「/note/ 之后整段」作为 id，不能依赖默认的单段 param。
 */
import { createRouter, createWebHashHistory } from 'vue-router';

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/ai', name: 'ai', component: () => import('@/views/AiFocusView.vue') },
    { path: '/graph', name: 'graph', component: () => import('@/views/GraphView.vue') },
    { path: '/search', name: 'search', component: () => import('@/views/SearchView.vue') },
    { path: '/note/:id(.*)', name: 'note', component: () => import('@/views/NoteView.vue') },
    { path: '/settings', name: 'settings', component: () => import('@/views/SettingsView.vue') },
  ],
});

export default router;
