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
