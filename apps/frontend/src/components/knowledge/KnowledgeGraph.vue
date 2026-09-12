<script setup lang="ts">
/**
 * 知识图谱：SVG 力导向布局（requestAnimationFrame 迭代斥力/弹簧力收敛），
 * 初始化 fitToView 保证世界坐标（1200×800）节点落在可视区。
 * 节点点击展示详情面板（真实 backlinks 驱动），拖拽临时固定节点位置。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import type { GraphTopic } from '@/types/knowledge';
import { useKnowledgeStore } from '@/stores/knowledge';
import { useRouter } from 'vue-router';
import { GRAPH_NODE_NOTES } from '@/services/mock/graph';
import { knowledgeService } from '@/services/knowledgeService';
import { FileText, X } from 'lucide-vue-next';

interface SimNode {
  id: string;
  label: string;
  category: string;
  x: number;
  y: number;
  vx: number;
  vy: number;
}

const props = defineProps<{
  topics: GraphTopic[];
  edges: { source: string; target: string; label: string }[];
}>();

const emit = defineEmits<{ select: [topicId: string] }>();

const knowledge = useKnowledgeStore();
const router = useRouter();

const W = 1200;
const H = 800;

const nodes = reactive<SimNode[]>([]);
const selected = ref<string | null>(null);
const hovered = ref<string | null>(null);
const transform = reactive({ x: 0, y: 0, k: 1 });
const svgRef = ref<SVGSVGElement | null>(null);

let raf = 0;
let running = false;

const clusterCenter: Record<string, { x: number; y: number }> = {
  java: { x: W * 0.26, y: H * 0.42 },
  ai: { x: W * 0.68, y: H * 0.45 },
  project: { x: W * 0.46, y: H * 0.78 },
  media: { x: W * 0.86, y: H * 0.8 },
  root: { x: W * 0.5, y: H * 0.5 },
};

function initNodes(): void {
  nodes.length = 0;
  props.topics.forEach((topic, index) => {
    const center = clusterCenter[topic.category] ?? clusterCenter.root;
    const angle = (index / props.topics.length) * Math.PI * 2;
    nodes.push({
      id: topic.id,
      label: topic.label,
      category: topic.category,
      x: center.x + Math.cos(angle) * 70,
      y: center.y + Math.sin(angle) * 70,
      vx: 0,
      vy: 0,
    });
  });
}

const nodeById = (id: string): SimNode | undefined => nodes.find((n) => n.id === id);

function step(): void {
  // 斥力
  for (let i = 0; i < nodes.length; i++) {
    for (let j = i + 1; j < nodes.length; j++) {
      const a = nodes[i];
      const b = nodes[j];
      let dx = b.x - a.x;
      let dy = b.y - a.y;
      let d2 = dx * dx + dy * dy;
      if (d2 < 1) {
        dx = Math.random() - 0.5;
        dy = Math.random() - 0.5;
        d2 = 1;
      }
      const d = Math.sqrt(d2);
      const rep = 26000 / d2;
      const fx = (dx / d) * rep;
      const fy = (dy / d) * rep;
      a.vx -= fx;
      a.vy -= fy;
      b.vx += fx;
      b.vy += fy;
    }
  }
  // 弹簧
  for (const edge of props.edges) {
    const a = nodeById(edge.source);
    const b = nodeById(edge.target);
    if (!a || !b) continue;
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const d = Math.max(Math.sqrt(dx * dx + dy * dy), 1);
    const target = 170;
    const f = (d - target) * 0.012;
    const fx = (dx / d) * f;
    const fy = (dy / d) * f;
    a.vx += fx;
    a.vy += fy;
    b.vx -= fx;
    b.vy -= fy;
  }
  // 向聚类中心 + 画布中心收敛
  for (const node of nodes) {
    const cat = props.topics.find((t) => t.id === node.id)?.category ?? 'root';
    const center = clusterCenter[cat] ?? clusterCenter.root;
    node.vx += (center.x - node.x) * 0.012;
    node.vy += (center.y - node.y) * 0.012;
    node.vx *= 0.82;
    node.vy *= 0.82;
    if (!dragging) {
      node.x += node.vx;
      node.y += node.vy;
    }
  }
}

function loop(): void {
  step();
  raf = requestAnimationFrame(loop);
}

const catColor: Record<string, string> = {
  java: 'var(--cat-java)',
  ai: 'var(--cat-ai)',
  project: 'var(--cat-project)',
  media: 'var(--cat-media)',
  root: 'var(--cat-root)',
};

const colorOf = (id: string): string => {
  const topic = props.topics.find((t) => t.id === id);
  return catColor[topic?.category ?? 'root'] ?? 'var(--cat-root)';
};

/* 交互：拖拽 / 缩放 / 平移 */
let dragging: SimNode | null = null;
let panning = false;
let lastMouse = { x: 0, y: 0 };

function toWorld(clientX: number, clientY: number): { x: number; y: number } {
  const rect = svgRef.value?.getBoundingClientRect();
  if (!rect) return { x: 0, y: 0 };
  return {
    x: (clientX - rect.left - transform.x) / transform.k,
    y: (clientY - rect.top - transform.y) / transform.k,
  };
}

function onNodeDown(event: PointerEvent, node: SimNode): void {
  event.stopPropagation();
  dragging = node;
  (event.target as Element).setPointerCapture?.(event.pointerId);
}

function onBgDown(event: PointerEvent): void {
  panning = true;
  lastMouse = { x: event.clientX, y: event.clientY };
}

function onMove(event: PointerEvent): void {
  if (dragging) {
    const p = toWorld(event.clientX, event.clientY);
    dragging.x = p.x;
    dragging.y = p.y;
    dragging.vx = 0;
    dragging.vy = 0;
  } else if (panning) {
    transform.x += event.clientX - lastMouse.x;
    transform.y += event.clientY - lastMouse.y;
    lastMouse = { x: event.clientX, y: event.clientY };
  }
}

function onUp(): void {
  dragging = null;
  panning = false;
}

function onWheel(event: WheelEvent): void {
  event.preventDefault();
  const rect = svgRef.value?.getBoundingClientRect();
  if (!rect) return;
  const mx = event.clientX - rect.left;
  const my = event.clientY - rect.top;
  const factor = event.deltaY < 0 ? 1.1 : 1 / 1.1;
  const k = Math.max(0.4, Math.min(3, transform.k * factor));
  transform.x = mx - ((mx - transform.x) * k) / transform.k;
  transform.y = my - ((my - transform.y) * k) / transform.k;
  transform.k = k;
}

/* 选中节点详情 */
const selectedTopic = computed(() => props.topics.find((t) => t.id === selected.value) ?? null);
/** 真实 Vault：节点即笔记，展示该笔记 + 引用它的笔记；Mock：查表 */
const selectedNotes = ref<{ noteId: string; title: string }[]>([]);

watch(selected, async (id) => {
  if (!id) {
    selectedNotes.value = [];
    return;
  }
  if (knowledgeService.isVaultConnected()) {
    const title = knowledgeService.getNoteTitle(id);
    const backs = await knowledgeService.getBacklinks(id);
    selectedNotes.value = [
      ...(title ? [{ noteId: id, title }] : []),
      ...backs.map((b) => ({ noteId: b.sourcePath, title: b.sourceTitle })),
    ];
  } else {
    selectedNotes.value = GRAPH_NODE_NOTES[id] ?? [];
  }
});
const selectedRelations = computed(() => {
  if (!selected.value) return [];
  const names = props.topics.map((t) => ({ id: t.id, label: t.label }));
  return props.edges
    .filter((e) => e.source === selected.value || e.target === selected.value)
    .map((e) => {
      const otherId = e.source === selected.value ? e.target : e.source;
      const other = names.find((n) => n.id === otherId);
      return { id: otherId, label: other?.label ?? otherId, relation: e.label, direction: e.source === selected.value ? 'out' : 'in' };
    });
});

const isNeighbor = (id: string): boolean => {
  if (!selected.value && !hovered.value) return true;
  const focus = selected.value ?? hovered.value;
  if (focus === id) return true;
  return props.edges.some(
    (e) => (e.source === focus && e.target === id) || (e.target === focus && e.source === id),
  );
};

function selectNode(id: string): void {
  selected.value = selected.value === id ? null : id;
  if (selected.value) emit('select', selected.value);
}

function openNote(noteId: string): void {
  knowledge.openNote(noteId);
  router.push({ name: 'note', params: { id: noteId } });
}

onMounted(() => {
  initNodes();
  fitToView();
  running = true;
  raf = requestAnimationFrame(loop);
  // 6 秒后停止模拟，节点已稳定，节省 CPU
  setTimeout(() => {
    if (running) {
      cancelAnimationFrame(raf);
      running = false;
    }
  }, 6000);
});

/** 初始视图：把整个图谱世界缩放居中到可视区 */
function fitToView(): void {
  const rect = svgRef.value?.getBoundingClientRect();
  if (!rect || rect.height === 0) {
    requestAnimationFrame(fitToView);
    return;
  }
  const k = Math.min(rect.width / W, rect.height / H) * 0.9;
  transform.k = k;
  transform.x = (rect.width - W * k) / 2;
  transform.y = (rect.height - H * k) / 2;
}

onBeforeUnmount(() => {
  running = false;
  cancelAnimationFrame(raf);
});
</script>

<template>
  <div class="graph">
    <svg
      ref="svgRef"
      class="graph__svg"
      @pointerdown="onBgDown"
      @pointermove="onMove"
      @pointerup="onUp"
      @pointerleave="onUp"
      @wheel="onWheel"
    >
      <g :transform="`translate(${transform.x},${transform.y}) scale(${transform.k})`">
        <!-- 边 -->
        <g>
          <g v-for="edge in edges" :key="`${edge.source}-${edge.target}`">
            <line
              class="graph__edge"
              :class="{ 'graph__edge--active': isNeighbor(edge.source) && isNeighbor(edge.target) }"
              :x1="nodeById(edge.source)?.x"
              :y1="nodeById(edge.source)?.y"
              :x2="nodeById(edge.target)?.x"
              :y2="nodeById(edge.target)?.y"
            />
            <text
              v-if="isNeighbor(edge.source) && isNeighbor(edge.target)"
              class="graph__edge-label"
              :x="((nodeById(edge.source)?.x ?? 0) + (nodeById(edge.target)?.x ?? 0)) / 2"
              :y="((nodeById(edge.source)?.y ?? 0) + (nodeById(edge.target)?.y ?? 0)) / 2 - 5"
              text-anchor="middle"
            >
              {{ edge.label }}
            </text>
          </g>
        </g>

        <!-- 节点 -->
        <g
          v-for="node in nodes"
          :key="node.id"
          class="graph__node"
          :class="{ 'graph__node--dim': !isNeighbor(node.id) }"
          @pointerdown="onNodeDown($event, node)"
          @pointerenter="hovered = node.id"
          @pointerleave="hovered = null"
          @click="selectNode(node.id)"
        >
          <circle
            class="graph__halo"
            :cx="node.x"
            :cy="node.y"
            :r="selected === node.id || hovered === node.id ? 30 : 0"
            :fill="colorOf(node.id)"
            opacity="0.12"
          />
          <circle
            class="graph__circle"
            :cx="node.x"
            :cy="node.y"
            r="11"
            :fill="colorOf(node.id)"
            :opacity="selected === node.id || hovered === node.id ? 1 : 0.85"
          />
          <text class="graph__label" :x="node.x" :y="node.y + 27" text-anchor="middle">
            {{ node.label }}
          </text>
        </g>
      </g>
    </svg>

    <!-- 图例 -->
    <div class="graph__legend">
      <div v-for="(c, name) in catColor" :key="name" class="graph__legend-item">
        <span class="graph__legend-dot" :style="{ background: c }"></span>
        <span>{{ { java: 'Java', ai: 'AI', project: '项目', media: '自媒体', root: '核心' }[name] }}</span>
      </div>
    </div>

    <div class="graph__hint">拖动节点 · 滚轮缩放 · 拖动空白平移 · 点击节点查看关联</div>

    <!-- 节点详情侧板 -->
    <Transition name="slide">
      <aside v-if="selectedTopic" class="graph__detail">
        <header class="graph__detail-head">
          <span class="graph__detail-dot" :style="{ background: colorOf(selectedTopic.id) }"></span>
          <h2 class="graph__detail-title">{{ selectedTopic.label }}</h2>
          <button class="graph__detail-close" @click="selected = null"><X :size="14" /></button>
        </header>

        <div class="graph__detail-section">
          <div class="section-label">相关笔记</div>
          <button
            v-for="n in selectedNotes"
            :key="n.noteId"
            class="graph__note"
            @click="openNote(n.noteId)"
          >
            <FileText :size="13" />
            {{ n.title }}
          </button>
          <p v-if="!selectedNotes.length" class="graph__none">暂无关联笔记</p>
        </div>

        <div class="graph__detail-section">
          <div class="section-label">关联知识</div>
          <div v-for="rel in selectedRelations" :key="rel.id" class="graph__rel">
            <button class="graph__rel-name" @click="selectNode(rel.id)">{{ rel.label }}</button>
            <span class="graph__rel-type">
              {{ rel.direction === 'out' ? `→ ${rel.relation}` : `← ${rel.relation}` }}
            </span>
          </div>
        </div>
      </aside>
    </Transition>
  </div>
</template>

<style scoped>
.graph {
  position: relative;
  height: 100%;
  background: radial-gradient(ellipse at 50% 40%, var(--graph-glow), var(--bg) 75%);
  overflow: hidden;
}

.graph__svg {
  width: 100%;
  height: 100%;
  cursor: grab;
  touch-action: none;
}

.graph__svg:active {
  cursor: grabbing;
}

.graph__edge {
  stroke: var(--border-strong);
  stroke-width: 1.2;
  opacity: 0.35;
  transition: opacity var(--dur) var(--ease);
}

.graph__edge--active {
  stroke: var(--primary);
  opacity: 0.65;
}

.graph__edge-label {
  fill: var(--text-3);
  font-size: 10px;
}

.graph__node {
  cursor: pointer;
}

.graph__node--dim {
  opacity: 0.25;
}

.graph__halo {
  transition: all var(--dur) var(--ease);
}

.graph__circle {
  transition: opacity var(--dur) var(--ease);
}

.graph__label {
  fill: var(--text-2);
  font-size: 12px;
  pointer-events: none;
}

.graph__legend {
  position: absolute;
  left: var(--sp-4);
  bottom: var(--sp-4);
  display: flex;
  gap: var(--sp-4);
  padding: var(--sp-2) var(--sp-3);
  background: var(--glass-1);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  backdrop-filter: blur(8px);
}

.graph__legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-xs);
  color: var(--text-2);
}

.graph__legend-dot {
  width: 8px;
  height: 8px;
  border-radius: var(--r-full);
}

.graph__hint {
  position: absolute;
  right: var(--sp-4);
  bottom: var(--sp-4);
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.graph__detail {
  position: absolute;
  top: var(--sp-4);
  right: var(--sp-4);
  width: 272px;
  max-height: calc(100% - var(--sp-8));
  overflow-y: auto;
  padding: var(--sp-4);
  background: var(--glass-2);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(12px);
}

.graph__detail-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);
}

.graph__detail-dot {
  width: 10px;
  height: 10px;
  border-radius: var(--r-full);
}

.graph__detail-title {
  flex: 1;
  font-size: var(--fs-md);
  font-weight: 650;
}

.graph__detail-close {
  color: var(--text-3);
  padding: 3px;
  border-radius: var(--r-sm);
}

.graph__detail-close:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

.graph__detail-section {
  margin-bottom: var(--sp-4);
}

.graph__note {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;
  padding: var(--sp-2);
  margin-top: var(--sp-1);
  font-size: var(--fs-sm);
  color: var(--text-2);
  border-radius: var(--r-sm);
  text-align: left;
  transition: all var(--dur-fast) var(--ease);
}

.graph__note:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

.graph__none {
  margin-top: var(--sp-1);
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.graph__rel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-1) 0;
  font-size: var(--fs-sm);
}

.graph__rel-name {
  color: var(--primary);
  font-weight: 550;
}

.graph__rel-name:hover {
  text-decoration: underline;
}

.graph__rel-type {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.slide-enter-active,
.slide-leave-active {
  transition: opacity var(--dur) var(--ease), transform var(--dur) var(--ease);
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateX(12px);
}
</style>
