<template>
  <div class="boundary-marker">
    <div class="toolbar">
      <el-radio-group v-model="mode" size="small">
        <el-radio-button label="a">A方实际范围</el-radio-button>
        <el-radio-button label="b">B方实际范围</el-radio-button>
        <el-radio-button label="lane">实际通道</el-radio-button>
      </el-radio-group>
      <el-button size="small" @click="undoPoint">撤销一点</el-button>
      <el-button size="small" @click="clearLayer">清空当前</el-button>
      <el-button size="small" @click="clearAll">全部清空</el-button>
    </div>
    <div class="hint text-muted">
      在图上点击逐点描绘：蓝/橙为两摊主实际占地范围（自动闭合），红线为实测通道边界。坐标按底图比例保存。
    </div>
    <div ref="boxRef" class="canvas-box" @click="addPoint">
      <img v-if="baseImage" :src="baseImage" class="base-img" draggable="false" />
      <div v-else class="placeholder">
        <el-icon :size="28"><PictureFilled /></el-icon>
        <span>暂无底图（可使用平台摊位图或现场照片）</span>
      </div>
      <svg class="draw-layer" viewBox="0 0 100 100" preserveAspectRatio="none">
        <polygon v-if="layers.a.length >= 3" :points="toPoints(layers.a)"
          fill="rgba(64,158,255,.28)" stroke="#409eff" stroke-width="0.8" vector-effect="non-scaling-stroke" />
        <polygon v-if="layers.b.length >= 3" :points="toPoints(layers.b)"
          fill="rgba(230,162,60,.28)" stroke="#e6a23c" stroke-width="0.8" vector-effect="non-scaling-stroke" />
        <polyline v-if="layers.lane.length >= 2" :points="toPoints(layers.lane)"
          fill="none" stroke="#f56c6c" stroke-width="1.2" stroke-dasharray="2 1.4" vector-effect="non-scaling-stroke" />
      </svg>
      <div
        v-for="(p, i) in currentPoints" :key="mode + i"
        class="dot" :style="{ left: p.x + '%', top: p.y + '%' }" :class="'dot-' + mode"
      >{{ i + 1 }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { PictureFilled } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  baseImage: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const mode = ref('a')
const boxRef = ref(null)
const layers = ref({ a: [], b: [], lane: [] })

const currentPoints = computed(() => layers.value[mode.value] || [])

watch(() => props.modelValue, v => {
  if (!v) { layers.value = { a: [], b: [], lane: [] }; return }
  try {
    const parsed = JSON.parse(v)
    layers.value = {
      a: parsed.aOutline || parsed.a || [],
      b: parsed.bOutline || parsed.b || [],
      lane: parsed.lane || []
    }
  } catch { layers.value = { a: [], b: [], lane: [] } }
}, { immediate: true })

function sync() {
  emit('update:modelValue', JSON.stringify({
    aOutline: layers.value.a, bOutline: layers.value.b, lane: layers.value.lane
  }))
}

function addPoint(e) {
  const box = boxRef.value.getBoundingClientRect()
  const x = Math.min(100, Math.max(0, ((e.clientX - box.left) / box.width) * 100))
  const y = Math.min(100, Math.max(0, ((e.clientY - box.top) / box.height) * 100))
  layers.value[mode.value].push({ x: Math.round(x * 10) / 10, y: Math.round(y * 10) / 10 })
  sync()
}

function undoPoint() { layers.value[mode.value].pop(); sync() }
function clearLayer() { layers.value[mode.value] = []; sync() }
function clearAll() { layers.value = { a: [], b: [], lane: [] }; sync() }

function toPoints(pts) {
  return pts.map(p => `${p.x},${p.y}`).join(' ')
}
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 6px; }
.hint { font-size: 12px; margin-bottom: 6px; }
.canvas-box {
  position: relative; width: 100%; aspect-ratio: 16 / 9;
  border: 1px solid #dcdfe6; border-radius: 6px; overflow: hidden;
  background: #fafafa; cursor: crosshair; user-select: none;
}
.base-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; pointer-events: none; }
.draw-layer { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.placeholder { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #a8abb2; }
.dot {
  position: absolute; transform: translate(-50%, -50%);
  width: 16px; height: 16px; border-radius: 50%;
  color: #fff; font-size: 10px; line-height: 16px; text-align: center;
  pointer-events: none; box-shadow: 0 0 2px rgba(0,0,0,.4);
}
.dot-a { background: #409eff; }
.dot-b { background: #e6a23c; }
.dot-lane { background: #f56c6c; }
</style>
