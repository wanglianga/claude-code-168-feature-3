<template>
  <div class="page">
    <el-row :gutter="16">
      <el-col :span="11">
        <el-card>
          <div class="card-title">边界争议现场登记与实际边界标注</div>
          <el-form :model="form" label-width="100px">
            <el-form-item label="集市" required>
              <el-select v-model="form.eventId" style="width: 100%" @change="loadAssignments">
                <el-option v-for="e in events" :key="e.id" :label="`${e.name}（${e.marketDate}）`" :value="e.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="摊主一方" required>
              <el-select v-model="form.resultAId" style="width: 100%" filterable placeholder="选择主张权利的摊主 A">
                <el-option v-for="a in assignments" :key="a.result.id"
                  :label="`${a.result.stallNo} - ${a.result.application?.vendor?.name}`" :value="a.result.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="摊主另一方">
              <el-select v-model="form.resultBId" style="width: 100%" filterable clearable
                placeholder="选择相邻摊主 B（流动摊贩可不选）">
                <el-option v-for="a in assignments.filter(x => x.result.id !== form.resultAId)" :key="a.result.id"
                  :label="`${a.result.stallNo} - ${a.result.application?.vendor?.name}`" :value="a.result.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="争议边界位置">
              <el-radio-group v-if="form.resultBId" v-model="form.stallId">
                <el-radio :label="aStallId">A 侧摊位边界</el-radio>
                <el-radio :label="bStallId">B 侧摊位边界</el-radio>
              </el-radio-group>
              <span v-else class="text-muted">默认记为 A 侧摊位</span>
            </el-form-item>
            <el-form-item label="争议情况" required>
              <el-input v-model="form.description" type="textarea" :rows="2"
                placeholder="双方各自主张、占用通道情况" />
            </el-form-item>
            <el-form-item label="现场照片" required>
              <ImageUpload v-model="form.scenePhotos" />
            </el-form-item>
            <el-form-item label="标注实际边界">
              <BoundaryMarker v-model="form.actualBoundaryData" :image-url="firstPhoto" />
            </el-form-item>
            <el-form-item label="边界测量说明">
              <el-input v-model="form.actualBoundaryNote" type="textarea" :rows="2"
                placeholder="如：A-03 实际右边界较抽签线内缩 0.3m，通道实测 2.1m" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="submit">提交争议调解</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="13">
        <el-card>
          <div class="card-title">本集市边界争议（认定进度）</div>
          <el-table :data="disputes" size="small" max-height="700">
            <el-table-column label="编号" width="60">
              <template #default="{ row }">#{{ row.id }}</template>
            </el-table-column>
            <el-table-column label="争议摊位" min-width="150">
              <template #default="{ row }">
                <el-tag size="small">{{ row.resultA?.stallNo }}</el-tag>
                <span v-if="row.resultB" class="text-muted"> / </span>
                <el-tag v-if="row.resultB" size="small" type="warning">{{ row.resultB.stallNo }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="情况" min-width="160" show-overflow-tooltip />
            <el-table-column label="认定结论" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'SUBMITTED'" size="small" type="warning">待认定</el-tag>
                <el-tag v-else size="small" :type="conclusionType(row.conclusion)">
                  {{ conclusionText(row.conclusion) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="扣分" width="70">
              <template #default="{ row }">
                <span v-if="row.penalized" style="color:#f56c6c;font-weight:600">-{{ row.penaltyPoints }}</span>
                <el-tag v-else-if="row.waived" size="small" type="success">已免除</el-tag>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="登记时间" width="145">
              <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!disputes.length" description="暂无边界争议" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { fmtTime } from '../../utils/dict'
import ImageUpload from '../../components/ImageUpload.vue'
import BoundaryMarker from '../../components/BoundaryMarker.vue'

const events = ref([])
const assignments = ref([])
const disputes = ref([])
const saving = ref(false)
const form = ref({
  eventId: null, resultAId: null, resultBId: null, stallId: null,
  description: '', scenePhotos: '', actualBoundaryData: '', actualBoundaryNote: ''
})

const firstPhoto = computed(() => (form.value.scenePhotos || '').split(',').filter(Boolean)[0] || '')
const aStallId = computed(() => assignments.value.find(a => a.result.id === form.value.resultAId)?.result?.stall?.id || null)
const bStallId = computed(() => assignments.value.find(a => a.result.id === form.value.resultBId)?.result?.stall?.id || null)

function conclusionText(c) {
  return { OVER_AREA: '超面积', MAP_UNCLEAR: '图不清晰', NO_VIOLATION: '无违规' }[c] || c
}
function conclusionType(c) {
  return { OVER_AREA: 'danger', MAP_UNCLEAR: 'warning', NO_VIOLATION: 'info' }[c] || 'info'
}

async function loadAssignments() {
  if (!form.value.eventId) return
  const [asgs, ds] = await Promise.all([
    api.get(`/staff/events/${form.value.eventId}/assignments`),
    api.get(`/staff/events/${form.value.eventId}/boundary-disputes`)
  ])
  assignments.value = asgs
  disputes.value = ds
}

async function submit() {
  if (!form.value.eventId || !form.value.resultAId || !form.value.description || !form.value.scenePhotos) {
    return ElMessage.warning('请完整填写集市、争议双方、情况说明并上传现场照片')
  }
  saving.value = true
  try {
    await api.post('/staff/boundary-disputes', form.value)
    ElMessage.success('争议已登记并提交市场管理员认定')
    form.value.description = ''
    form.value.scenePhotos = ''
    form.value.actualBoundaryData = ''
    form.value.actualBoundaryNote = ''
    form.value.resultBId = null
    await loadAssignments()
  } finally { saving.value = false }
}

onMounted(async () => {
  events.value = await api.get('/meta/events')
  const active = events.value.find(e => ['LOTTERY_DONE', 'IN_PROGRESS'].includes(e.status))
  if (active) { form.value.eventId = active.id; await loadAssignments() }
})
</script>
