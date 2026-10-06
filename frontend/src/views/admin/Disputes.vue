<template>
  <div class="page">
    <el-card>
      <div style="display:flex;justify-content:space-between;align-items:center" class="mb">
        <div class="card-title" style="margin:0">摊位边界争议复核（超面积整改扣分 / 图不清晰豁免发版）</div>
        <el-select v-model="eventId" placeholder="全部集市" clearable style="width: 240px" @change="load">
          <el-option v-for="e in events" :key="e.id" :label="`${e.name}（${e.marketDate}）`" :value="e.id" />
        </el-select>
      </div>

      <el-alert type="info" :closable="false" class="mb"
        title="规则：一方超出抽签面积→系统已自动生成整改单并扣 5 分；若抽签图本身不清晰→管理员重新测量、发布更新摊位图并免除本次扣罚（撤销整改、回滚信用）。同一位置争议达 2 次自动标记风险点。" />

      <el-table :data="disputes" size="small" max-height="600" @row-click="openDetail" highlight-current-row>
        <el-table-column label="#" width="50" prop="id" />
        <el-table-column label="争议摊位" min-width="130">
          <template #default="{ row }">
            <el-tag size="small">{{ row.resultA?.stallNo }}</el-tag>
            <span class="text-muted">×</span>
            <el-tag size="small" type="warning">{{ row.resultB?.stallNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="双方(抽签→实测)" min-width="260">
          <template #default="{ row }">
            <div class="text-muted" style="font-size:12px">
              A {{ fmt(row.lotWidthA) }}×{{ fmt(row.lotDepthA) }}→<b>{{ fmt(row.actualWidthA) }}×{{ fmt(row.actualDepthA) }}</b>
              ｜B {{ fmt(row.lotWidthB) }}×{{ fmt(row.lotDepthB) }}→<b>{{ fmt(row.actualWidthB) }}×{{ fmt(row.actualDepthB) }}</b>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="自动判定" width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="verdictType(row.verdict)">{{ verdictText(row.verdict) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            <el-icon v-if="row.riskMarked" color="#f56c6c"><WarningFilled /></el-icon>
          </template>
        </el-table-column>
        <el-table-column label="登记人" width="110">
          <template #default="{ row }">{{ row.reporter?.name }}</template>
        </el-table-column>
        <el-table-column label="时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情 + 复核 -->
    <el-drawer v-model="detailVisible" size="560px" title="边界争议复核">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="statusType(d.status)">{{ statusText(d.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="判定">{{ verdictText(d.verdict) }}</el-descriptions-item>
          <el-descriptions-item label="登记人">{{ d.reporter?.name }}</el-descriptions-item>
          <el-descriptions-item label="依据图版本">{{ d.mapVersion?.versionNo || '无' }}</el-descriptions-item>
          <el-descriptions-item label="A 摊主">{{ d.resultA?.application?.vendor?.name }}（{{ d.resultA?.stallNo }}）</el-descriptions-item>
          <el-descriptions-item label="A 抽签/实测">{{ fmt(d.lotWidthA) }}×{{ fmt(d.lotDepthA) }} / {{ fmt(d.actualWidthA) }}×{{ fmt(d.actualDepthA) }} m</el-descriptions-item>
          <el-descriptions-item label="B 摊主">{{ d.resultB?.application?.vendor?.name }}（{{ d.resultB?.stallNo }}）</el-descriptions-item>
          <el-descriptions-item label="B 抽签/实测">{{ fmt(d.lotWidthB) }}×{{ fmt(d.lotDepthB) }} / {{ fmt(d.actualWidthB) }}×{{ fmt(d.actualDepthB) }} m</el-descriptions-item>
          <el-descriptions-item label="通道净宽">{{ d.laneClearWidth ?? '-' }} m</el-descriptions-item>
          <el-descriptions-item label="风险标记">{{ d.riskMarked ? '已标记风险点' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="情况说明" :span="2">{{ d.description }}</el-descriptions-item>
          <el-descriptions-item v-if="d.reviewNote" label="复核意见" :span="2">{{ d.reviewNote }}</el-descriptions-item>
        </el-descriptions>

        <div class="card-title mt">现场照片与实际边界标注</div>
        <div v-if="photos(d.photos).length" class="mb">
          <el-image v-for="u in photos(d.photos)" :key="u" :src="u" :preview-src-list="photos(d.photos)"
            class="thumb" fit="cover" preview-teleported />
        </div>
        <div v-if="hasMark" class="mark-box">
          <img v-if="d.markBaseImage" :src="d.markBaseImage" class="mark-img" draggable="false" />
          <svg class="mark-svg" viewBox="0 0 100 100" preserveAspectRatio="none">
            <polygon v-if="mark.aOutline?.length >= 3" :points="pts(mark.aOutline)"
              fill="rgba(64,158,255,.28)" stroke="#409eff" stroke-width=".8" vector-effect="non-scaling-stroke" />
            <polygon v-if="mark.bOutline?.length >= 3" :points="pts(mark.bOutline)"
              fill="rgba(230,162,60,.28)" stroke="#e6a23c" stroke-width=".8" vector-effect="non-scaling-stroke" />
            <polyline v-if="mark.lane?.length >= 2" :points="pts(mark.lane)"
              fill="none" stroke="#f56c6c" stroke-width="1.2" stroke-dasharray="2 1.4" vector-effect="non-scaling-stroke" />
          </svg>
        </div>

        <div class="card-title mt">整改 / 扣罚单</div>
        <el-table :data="detail.rectifications" size="small">
          <el-table-column label="摊主" min-width="110">
            <template #default="{ row }">{{ row.vendor?.name }}（{{ row.result?.stallNo }}）</template>
          </el-table-column>
          <el-table-column label="扣分" width="60">
            <template #default="{ row }"><span style="color:#f56c6c">{{ row.creditDelta }}</span></template>
          </el-table-column>
          <el-table-column label="状态" min-width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="rectType(row.status)">{{ rectText(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!detail.rectifications.length" :image-size="50"
          description="未开整改单（双方均未超面积，多为抽签图不清晰，建议下方发版豁免）" />

        <div v-if="canAct" class="mt action-bar">
          <el-button type="success" @click="openReview('RESOLVED')">维持判定·办结</el-button>
          <el-button @click="openReview('REJECTED')">驳回（争议不成立）</el-button>
          <el-button type="warning" @click="unclearVisible = true">
            认定抽签图不清晰·发版并免除本次扣罚
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 复核意见 -->
    <el-dialog v-model="reviewVisible" :title="reviewStatus === 'RESOLVED' ? '办结争议' : '驳回争议'" width="460px">
      <el-input v-model="reviewNote" type="textarea" :rows="3" placeholder="复核意见（向当事摊主公开）" />
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReview">确认</el-button>
      </template>
    </el-dialog>

    <!-- 图不清晰：重新测量发版并豁免 -->
    <el-dialog v-model="unclearVisible" title="认定抽签图不清晰：重新测量发布更新图并豁免" width="560px">
      <el-alert type="warning" :closable="false" class="mb"
        title="提交后：发布新版摊位图（新老摊主可见调整依据）→ 撤销本次整改单 → 回滚已扣信用分 → 争议标记为已豁免，并自动解决该位置的风险点。" />
      <el-form :model="unclearForm" label-width="100px">
        <el-form-item label="新版本号"><el-input v-model="unclearForm.versionNo" placeholder="留空自动生成 v2/v3…" /></el-form-item>
        <el-form-item label="更新摊位图" required>
          <ImageUpload v-model="unclearForm.imageUrl" />
        </el-form-item>
        <el-form-item label="调整依据" required>
          <el-input v-model="unclearForm.changeReason" type="textarea" :rows="2"
            placeholder="必填，向新老摊主公开，如：B-03/B-04 边界争议频繁，实测通道偏窄，重测后将边界各退 0.3m" />
        </el-form-item>
        <el-form-item label="变更明细">
          <el-input v-model="unclearForm.changeDetails" type="textarea" :rows="2"
            placeholder='选填 JSON，如 [{"stallNo":"B-03","before":"面宽3.0m","after":"面宽2.7m","reason":"让通道"}]' />
        </el-form-item>
        <el-form-item label="复核说明">
          <el-input v-model="unclearForm.reviewNote" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="unclearVisible = false">取消</el-button>
        <el-button type="warning" :loading="saving" @click="submitUnclear">发布更新图并免除扣罚</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import api from '../../api'
import { fmtTime } from '../../utils/dict'
import ImageUpload from '../../components/ImageUpload.vue'

const events = ref([])
const disputes = ref([])
const eventId = ref(null)
const saving = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const reviewVisible = ref(false)
const reviewStatus = ref('RESOLVED')
const reviewNote = ref('')
const unclearVisible = ref(false)
const unclearForm = ref({ versionNo: '', imageUrl: '', changeReason: '', changeDetails: '', reviewNote: '' })

const d = computed(() => detail.value?.dispute)
const canAct = computed(() => d.value && ['OPEN', 'PENALIZED'].includes(d.value.status))
const hasMark = computed(() => {
  const m = d.value?.boundaryMark
  if (!m) return false
  try { const p = JSON.parse(m); return (p.aOutline?.length || p.bOutline?.length || p.lane?.length) > 0 } catch { return false }
})
const mark = computed(() => {
  try { return JSON.parse(d.value?.boundaryMark || '{}') } catch { return {} }
})

function photos(s) { return s ? s.split(',').filter(Boolean) : [] }
function pts(arr) { return (arr || []).map(p => `${p.x},${p.y}`).join(' ') }
function fmt(v) { return v == null ? '-' : Math.round(v * 100) / 100 }

async function load() {
  disputes.value = await api.get('/admin/disputes', { params: eventId.value ? { eventId: eventId.value } : {} })
}
async function openDetail(row) {
  detail.value = await api.get(`/admin/disputes/${row.id}`)
  detailVisible.value = true
}
function openReview(status) {
  reviewStatus.value = status
  reviewNote.value = ''
  reviewVisible.value = true
}
async function submitReview() {
  saving.value = true
  try {
    await api.post(`/admin/disputes/${d.value.id}/review`, { status: reviewStatus.value, reviewNote: reviewNote.value })
    ElMessage.success('已复核')
    reviewVisible.value = false
    detailVisible.value = false
    await load()
  } finally { saving.value = false }
}
async function submitUnclear() {
  if (!unclearForm.value.imageUrl) return ElMessage.warning('请上传重新测量后的摊位图')
  if (!unclearForm.value.changeReason) return ElMessage.warning('请填写调整依据')
  saving.value = true
  try {
    const v = await api.post(`/admin/disputes/${d.value.id}/unclear-map`, unclearForm.value)
    ElMessage.success(`已发布 ${v.versionNo} 并免除本次扣罚、回滚信用`)
    unclearVisible.value = false
    detailVisible.value = false
    unclearForm.value = { versionNo: '', imageUrl: '', changeReason: '', changeDetails: '', reviewNote: '' }
    await load()
  } finally { saving.value = false }
}

function verdictText(v) { return { A: '主张方超面积', B: '被诉方超面积', BOTH: '双方均超', NONE: '均未超(疑图不清)' }[v] || v }
function verdictType(v) { return { A: 'danger', B: 'warning', BOTH: 'danger', NONE: 'info' }[v] || 'info' }
function statusText(s) { return { OPEN: '待复核', PENALIZED: '已整改扣分', WAIVED: '图不清已豁免', RESOLVED: '已办结', REJECTED: '已驳回' }[s] || s }
function statusType(s) { return { OPEN: 'warning', PENALIZED: 'danger', WAIVED: 'success', RESOLVED: 'success', REJECTED: 'info' }[s] || 'info' }
function rectText(s) { return { PENDING: '待整改', RECTIFIED: '待复查', PASSED: '复查通过', FAILED: '复查不通过', CANCELLED_WAIVED: '已豁免撤销' }[s] || s }
function rectType(s) { return { PENDING: 'danger', RECTIFIED: 'warning', PASSED: 'success', FAILED: 'danger', CANCELLED_WAIVED: 'info' }[s] || 'info' }

onMounted(async () => {
  events.value = await api.get('/admin/events')
  await load()
})
</script>

<style scoped>
.thumb { width: 70px; height: 70px; margin: 2px; border-radius: 4px; }
.mt { margin-top: 12px; }
.mark-box { position: relative; width: 100%; aspect-ratio: 16/9; border: 1px solid #dcdfe6; border-radius: 6px; overflow: hidden; background: #fafafa; }
.mark-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; }
.mark-svg { position: absolute; inset: 0; width: 100%; height: 100%; }
.action-bar { display: flex; gap: 8px; flex-wrap: wrap; }
</style>
