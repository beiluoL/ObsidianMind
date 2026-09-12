/**
 * 应用入口：装配 Pinia、Router、主题并挂载 App 外壳。
 *
 * CSS 加载顺序：tokens（骨架变量）→ theme-dark（默认色）→ theme-light（覆盖）→ base。
 * theme.init() 与 index.html 内联脚本结论一致，避免首屏主题跳变（FOUC）。
 */
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { useThemeStore } from './stores/theme';
import './assets/styles/tokens.css';
import './assets/styles/theme-dark.css';
import './assets/styles/theme-light.css';
import './assets/styles/base.css';

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);

useThemeStore(pinia).init();

app.mount('#app');
