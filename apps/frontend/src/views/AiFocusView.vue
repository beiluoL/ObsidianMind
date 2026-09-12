<script setup lang="ts">
/**
 * AI 焦点视图：全屏对话模式（复用 ChatPanel），提供示例问题快捷发起。
 */
import { useRouter } from 'vue-router';
import {
  Lightbulb,
  FileText,
  Link2,
  HelpCircle,
  GraduationCap,
  PenLine,
} from 'lucide-vue-next';
import { useChatStore } from '@/stores/chat';

const router = useRouter();
const chat = useChatStore();

const actions = [
  { icon: Lightbulb, key: 'explain', title: '解释', desc: '用大白话解释选中或指定的概念', prompt: '用大白话解释一下 RAG 的核心原理' },
  { icon: FileText, key: 'summarize', title: '总结', desc: '把一篇或多篇长笔记压缩成要点', prompt: '总结我知识库中关于 JVM 的所有笔记' },
  { icon: Link2, key: 'connect', title: '查找关联', desc: '发现主题之间隐藏的连接', prompt: '我的知识库中哪些笔记和 RAG、Milvus 相关？' },
  { icon: HelpCircle, key: 'challenge', title: '找出疑点', desc: '挑战笔记中可能错误或不严谨的地方', prompt: '检查我的并发编程笔记里有没有不严谨的表述' },
  { icon: GraduationCap, key: 'deep', title: '深入讲解', desc: '像导师一样逐层深入讲解', prompt: '从 Transformer 开始深入讲解 Embedding 是怎么来的' },
  { icon: PenLine, key: 'rewrite', title: '改写', desc: '润色表达，保持原意', prompt: '帮我把周复盘模板改写得更简洁' },
];

function runAction(prompt: string): void {
  chat.ask(prompt);
}
</script>

<template>
  <div class="ai-focus">
    <div class="ai-focus__inner">
      <h1 class="ai-focus__title">Ask your Knowledge</h1>
      <p class="ai-focus__sub">AI 不只是聊天，它理解你知识库的结构、连接与薄弱点。</p>

      <div class="ai-focus__grid">
        <button v-for="a in actions" :key="a.key" class="ai-focus__card" @click="runAction(a.prompt)">
          <component :is="a.icon" :size="17" class="ai-focus__icon" />
          <div class="ai-focus__card-body">
            <div class="ai-focus__card-title">{{ a.title }}</div>
            <div class="ai-focus__card-desc">{{ a.desc }}</div>
          </div>
        </button>
      </div>

      <button class="ai-focus__back" @click="router.push({ name: 'home' })">返回首页</button>
    </div>
  </div>
</template>

<style scoped>
.ai-focus {
  flex: 1;
  display: flex;
  justify-content: center;
}

.ai-focus__inner {
  width: 100%;
  max-width: 760px;
  padding: var(--sp-10) var(--sp-6);
}

.ai-focus__title {
  font-size: var(--fs-2xl);
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(120deg, var(--text-1), var(--primary));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.ai-focus__sub {
  margin-top: var(--sp-2);
  color: var(--text-3);
  font-size: var(--fs-base);
}

.ai-focus__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
  margin-top: var(--sp-6);
}

.ai-focus__card {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  padding: var(--sp-4);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  text-align: left;
  transition: all var(--dur-fast) var(--ease);
}

.ai-focus__card:hover {
  border-color: var(--primary-border);
  background: var(--surface-2);
  transform: translateY(-1px);
}

.ai-focus__icon {
  color: var(--primary);
  margin-top: 2px;
}

.ai-focus__card-title {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-1);
}

.ai-focus__card-desc {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--text-3);
  line-height: 1.6;
}

.ai-focus__back {
  margin-top: var(--sp-6);
  font-size: var(--fs-sm);
  color: var(--text-3);
  transition: color var(--dur-fast) var(--ease);
}

.ai-focus__back:hover {
  color: var(--text-1);
}
</style>
