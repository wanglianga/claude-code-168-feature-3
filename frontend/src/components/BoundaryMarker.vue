<template>
  <div class="boundary-marker">
    <div v-if="!imageUrl" class="hint">请先上传现场照片，再在图上标注实际边界</div>
    <div v-else class="canvas-wrap">
      <img :src="imageUrl" class="map-img" alt="现场照片" @click="onClick" draggable="false" />
      <svg class="layer" viewBox="0 0 100 100" preserveAspectRatio="none">
        <polyline v-if="points.length > 1" :points="line"
          fill="none" stroke="#f56c6c" stroke-width="1.2"
          vector-effect="non-scaling-stroke" stroke-dasharray="3 2" />
        <circle v-for="(p, i) in points" :key="i" :cx="p[0]" :cy="p[1]" r="1.6"
          fill="#f56c6c" stroke="#fff" stroke-width="0.4" vector-effect="non-scaling-stroke" />
      </svg>
    </div>
    <div v-if="imageUrl" class="ops">
      <span class="tip">点击图片依次落点标注实际边界线（{{ points.length }} 个点）</span>
      <el-button size="small" :disabled="!points.length" @click="undo">撤销一点</el-button>
      <el-button size="small" :disabled="!points.length" @click="clear">清空</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  imageUrl: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])
const points = ref([])

const line = computed(() => points.value.map(p => p.join(',')).join(' '))

function parse() {
  if (!props.modelValue) { points.value = []; return }
  try {
    const obj = JSON.parse(props.modelValue)
    points.value = Array.isArray(obj.points) ? obj.points : []
  } catch { points.value = [] }
}
watch(() => props.modelValue, parse, { immediate: true })
watch(() => props.imageUrl, () => { points.value = []; emitData() })

function onClick(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  const x = +(((e.clientX - rect.left) / rect.width) * 100).toFixed(2)
  const y = +(((e.clientY - rect.top) / rect.height) * 100).toFixed(2)
  points.value = [...points.value, [x, y]]
  emitData()
}
function undo() { points.value = points.value.slice(0, -1); emitData() }
function clear() { points.value = []; emitData() }
function emitData() {
  emit('update:modelValue', points.value.length ? JSON.stringify({ points: points.value }) : '')
}
</script>

<style scoped>
.boundary-marker { border: 1px dashed #dcdfe6; border-radius: 4px; padding: 8px; }
.hint { color: #909399; font-size: 13px; padding: 12px 4px; }
.canvas-wrap { position: relative; width: 100%; }
.map-img { width: 100%; display: block; border-radius: 4px; cursor: crosshair; }
.layer { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.ops { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.tip { color: #909399; font-size: 12px; flex: 1; }
</style>
