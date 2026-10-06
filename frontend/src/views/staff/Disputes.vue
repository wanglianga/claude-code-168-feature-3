<template>
  <div class="page">
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card>
          <div class="card-title">边界争议现场立案</div>
          <el-form :model="form" label-width="98px">
            <el-form-item label="集市">
              <el-select v-model="form.eventId" style="width: 100%" @change="loadAssignments">
                <el-option v-for="e in events" :key="e.id" :label="`${e.name}（${e.marketDate}）`" :value="e.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="主张方A" required>
              <el-select v-model="form.resultAId" style="width: 100%" filterable placeholder="反映被占道的摊主">
                <el-option v-for="a in assignments" :key="'a'+a.result.id"
                  :label="`${a.result.stallNo} - ${a.result.application?.vendor?.name}`" :value="a.result.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="被诉方B" required>
              <el-select v-model="form.resultBId" style="width: 100%" filterable placeholder="被指占道的相邻摊主">
                <el-option v-for="a in assignments" :key="'b'+a.result.id"
                  :label="`${a.result.stallNo} - ${a.result.application?.vendor?.name}`" :value="a.result.id" />
              </el-select>
            </el-form-item>

            <el-divider content-position="left">抽签面积 vs 现场实测（米）</el-divider>
            <el-form-item label="A 实测面宽×进深">
              <el-input-number v-model="form.actualWidthA" :min="0" :step="0.1" :precision="2" style="width:46%" />
              <span style="margin:0 4px">×</span>
              <el-input-number v-model="form.actualDepthA" :min="0" :step="0.1" :precision="2" style="width:46%" />
            </el-form-item>
            <el-form-item label="B 实测面宽×进深">
              <el-input-number v-model="form.actualWidthB" :min="0" :step="0.1" :precision="2" style="width:46%" />
              <span style="margin:0 4px">×</span>
              <el-input-number v-model="form.actualDepthB" :min="0" :step="0.1" :precision="2" style="width:46%" />
            </el-form-item>
            <el-form-item label="通道净宽(米)">
              <el-input-number v-model="form.laneClearWidth" :min="0" :step="0.1" :precision="2" />
            </el-form-item>

            <el-form-item label="现场照片">
              <ImageUpload v-model="form.photos" />
            </el-form-item>
            <el-form-item label="标注实际边界">
              <BoundaryMarker v-model="form.boundaryMark" :base-image="form.markBaseImage || currentMap" />
            </el-form-item>
            <el-form-item label="情况说明">
              <el-input v-model="form.description" type="textarea" :rows="2"
                placeholder="双方各自主张、现场情况；超面积将自动生成整改并扣5分" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="submit">现场立案并自动判定</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card>
          <div style="display:flex;justify-content:space-between;align-items:center" class="mb">
            <div class="card-title" style="margin:0">争议调解记录</div>
            <el-radio-group v-model="tab" size="small">
              <el-radio-button label="disputes">争议单</el-radio-button>
              <el-radio-button label="rects">整改复查</el-radio-button>
            </el-radio-group>
          </div>

          <el-table v-if="tab === 'disputes'" :data="disputes" size="small" max-height="620"
            @row-click="openDetail" highlight-current-row>
            <el-table-column label="摊位" min-width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ row.resultA?.stallNo }}</el-tag>
                <span class="text-muted">×</span>
                <el-tag size="small" type="warning">{{ row.resultB?.stallNo }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="自动判定" width="150">
              <template #default="{ row }">
                <el-tag size="small" :type="verdictType(row.verdict)">{{ verdictText(row.verdict) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
                <el-icon v-if="row.riskMarked" color="#f56c6c" title="已标记风险点"><WarningFilled /></el-icon>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="140">
              <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>

          <el-table v-else :data="rects" size="small" max-height="620">
            <el-table-column label="整改单" width="70">
              <template #default="{ row }">#{{ row.id }}</template>
            </el-table-column>
            <el-table-column label="摊主/摊位" min-width="140">
              <template #default="{ row }">{{ row.vendor?.name }}（{{ row.result?.stallNo }}）</template>
            </el-table-column>
            <el-table-column prop="requirement" label="整改要求" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="rectStatusType(row.status)">{{ rectStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button v-if="['PENDING','RECTIFIED','FAILED'].includes(row.status)"
                  size="small" type="primary" @click.stop="openRecheck(row)">复查</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="tab==='disputes' && !disputes.length" description="暂无争议单" :image-size="60" />
          <el-empty v-if="tab==='rects' && !rects.length" description="暂无整改单" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 争议详情抽屉 -->
    <el-drawer v-model="detailVisible" size="520px" title="边界争议详情">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="statusType(detail.dispute.status)">{{ statusText(detail.dispute.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="自动判定">{{ verdictText(detail.dispute.verdict) }}</el-descriptions-item>
          <el-descriptions-item label="A 摊位">{{ detail.dispute.resultA?.stallNo }}（抽签 {{ detail.dispute.lotWidthA }}×{{ detail.dispute.lotDepthA }}m，实测 {{ detail.dispute.actualWidthA }}×{{ detail.dispute.actualDepthA }}m）</el-descriptions-item>
          <el-descriptions-item label="B 摊位">{{ detail.dispute.resultB?.stallNo }}（抽签 {{ detail.dispute.lotWidthB }}×{{ detail.dispute.lotDepthB }}m，实测 {{ detail.dispute.actualWidthB }}×{{ detail.dispute.actualDepthB }}m）</el-descriptions-item>
          <el-descriptions-item label="通道净宽">{{ detail.dispute.laneClearWidth ?? '-' }} m</el-descriptions-item>
          <el-descriptions-item label="依据图版本">{{ detail.dispute.mapVersion?.versionNo || '未发版' }}</el-descriptions-item>
          <el-descriptions-item label="情况说明" :span="2">{{ detail.dispute.description }}</el-descriptions-item>
          <el-descriptions-item label="现场照片" :span="2">
            <el-image v-for="u in photos(detail.dispute.photos)" :key="u" :src="u" :preview-src-list="photos(detail.dispute.photos)"
              class="thumb" fit="cover" preview-teleported />
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.dispute.reviewNote" label="复核意见" :span="2">{{ detail.dispute.reviewNote }}</el-descriptions-item>
        </el-descriptions>

        <div class="card-title mt">整改 / 扣罚单</div>
        <el-table :data="detail.rectifications" size="small">
          <el-table-column label="摊主" min-width="100">
            <template #default="{ row }">{{ row.vendor?.name }}（{{ row.result?.stallNo }}）</template>
          </el-table-column>
          <el-table-column label="扣分" width="60">
            <template #default="{ row }"><span style="color:#f56c6c">{{ row.creditDelta }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="rectStatusType(row.status)">{{ rectStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button v-if="['PENDING','RECTIFIED','FAILED'].includes(row.status)"
                size="small" type="primary" @click="openRecheck(row)">复查</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!detail.rectifications.length" description="未生成整改单（双方均未超抽签面积，待管理员复核是否图不清晰）" :image-size="60" />
      </template>
    </el-drawer>

    <!-- 整改复查弹窗 -->
    <el-dialog v-model="recheckVisible" title="整改现场复查" width="480px">
      <el-form :model="recheckForm" label-width="100px">
        <el-form-item label="复查结论">
          <el-radio-group v-model="recheckForm.passed">
            <el-radio :label="true">已退回边界，通过</el-radio>
            <el-radio :label="false">仍超占，不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复查实测宽×深">
          <el-input-number v-model="recheckForm.recheckedWidth" :min="0" :step="0.1" :precision="2" style="width:46%" />
          <span style="margin:0 4px">×</span>
          <el-input-number v-model="recheckForm.recheckedDepth" :min="0" :step="0.1" :precision="2" style="width:46%" />
        </el-form-item>
        <el-form-item label="复查照片"><ImageUpload v-model="recheckForm.photos" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="recheckForm.note" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recheckVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRecheck">提交复查</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import api from '../../api'
import { fmtTime } from '../../utils/dict'
import ImageUpload from '../../components/ImageUpload.vue'
import BoundaryMarker from '../../components/BoundaryMarker.vue'

const events = ref([])
const assignments = ref([])
const disputes = ref([])
const rects = ref([])
const currentMap = ref('')
const tab = ref('disputes')
const saving = ref(false)
const form = ref(blankForm())

const detailVisible = ref(false)
const detail = ref(null)
const recheckVisible = ref(false)
const recheckForm = ref({ id: null, passed: true, photos: '', note: '', recheckedWidth: null, recheckedDepth: null })

function blankForm() {
  return {
    eventId: null, resultAId: null, resultBId: null, photos: '', boundaryMark: '', markBaseImage: '',
    actualWidthA: null, actualDepthA: null, actualWidthB: null, actualDepthB: null,
    laneClearWidth: null, description: ''
  }
}

function photos(s) { return s ? s.split(',').filter(Boolean) : [] }

async function loadAssignments() {
  if (!form.value.eventId) return
  const [asgs, ds] = await Promise.all([
    api.get(`/staff/events/${form.value.eventId}/assignments`),
    api.get(`/staff/events/${form.value.eventId}/disputes`)
  ])
  assignments.value = asgs
  disputes.value = ds
}

async function loadRects() {
  rects.value = await api.get('/staff/rectifications')
}

async function submit() {
  const f = form.value
  if (!f.eventId || !f.resultAId || !f.resultBId) return ElMessage.warning('请选择集市与争议双方')
  if (f.resultAId === f.resultBId) return ElMessage.warning('争议双方不能为同一摊位')
  if (f.actualWidthA == null || f.actualDepthA == null || f.actualWidthB == null || f.actualDepthB == null) {
    return ElMessage.warning('请填写双方现场实测尺寸')
  }
  saving.value = true
  try {
    const d = await api.post('/staff/disputes', f)
    ElMessage.success(`已立案，判定：${verdictText(d.verdict)}${d.status === 'PENALIZED' ? '，已生成整改并扣分' : '，待管理员复核'}`)
    form.value = { ...blankForm(), eventId: f.eventId, markBaseImage: currentMap.value }
    await loadAssignments()
  } finally { saving.value = false }
}

async function openDetail(row) {
  detail.value = await api.get(`/staff/disputes/${row.id}`)
  detailVisible.value = true
}

function openRecheck(row) {
  recheckForm.value = { id: row.id, passed: true, photos: '', note: '', recheckedWidth: null, recheckedDepth: null }
  recheckVisible.value = true
}

async function submitRecheck() {
  saving.value = true
  try {
    await api.post(`/staff/rectifications/${recheckForm.value.id}/recheck`, {
      passed: recheckForm.value.passed, photos: recheckForm.value.photos, note: recheckForm.value.note,
      recheckedWidth: recheckForm.value.recheckedWidth, recheckedDepth: recheckForm.value.recheckedDepth
    })
    ElMessage.success('复查已提交')
    recheckVisible.value = false
    detailVisible.value = false
    await Promise.all([loadRects(), form.value.eventId ? loadAssignments() : Promise.resolve()])
  } finally { saving.value = false }
}

function verdictText(v) {
  return { A: '主张方超面积', B: '被诉方超面积', BOTH: '双方均超', NONE: '均未超(疑图不清)' }[v] || v
}
function verdictType(v) {
  return { A: 'danger', B: 'warning', BOTH: 'danger', NONE: 'info' }[v] || 'info'
}
function statusText(s) {
  return { OPEN: '待复核', PENALIZED: '已整改扣分', WAIVED: '图不清已豁免', RESOLVED: '已办结', REJECTED: '已驳回' }[s] || s
}
function statusType(s) {
  return { OPEN: 'warning', PENALIZED: 'danger', WAIVED: 'success', RESOLVED: 'success', REJECTED: 'info' }[s] || 'info'
}
function rectStatusText(s) {
  return { PENDING: '待整改', RECTIFIED: '待复查', PASSED: '复查通过', FAILED: '复查不通过', CANCELLED_WAIVED: '已豁免撤销' }[s] || s
}
function rectStatusType(s) {
  return { PENDING: 'danger', RECTIFIED: 'warning', PASSED: 'success', FAILED: 'danger', CANCELLED_WAIVED: 'info' }[s] || 'info'
}

onMounted(async () => {
  const [evs, mapInfo] = await Promise.all([
    api.get('/meta/events'), api.get('/meta/stall-map/current')
  ])
  events.value = evs
  currentMap.value = mapInfo.current?.imageUrl || ''
  const active = evs.find(e => ['LOTTERY_DONE', 'IN_PROGRESS'].includes(e.status))
  if (active) { form.value.eventId = active.id; await loadAssignments() }
  form.value.markBaseImage = currentMap.value
  await loadRects()
})
</script>

<style scoped>
.thumb { width: 64px; height: 64px; margin: 2px; border-radius: 4px; }
.mt { margin-top: 12px; }
</style>
