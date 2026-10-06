<template>
  <div class="page">
    <!-- 争议认定 -->
    <el-card>
      <div style="display:flex;justify-content:space-between;align-items:center" class="mb">
        <div class="card-title" style="margin:0">摊位边界争议认定（现场照片 + 实际边界标注）</div>
        <el-select v-model="eventId" placeholder="全部集市" clearable style="width: 260px" @change="loadDisputes">
          <el-option v-for="e in events" :key="e.id" :label="`${e.name}（${e.marketDate}）`" :value="e.id" />
        </el-select>
      </div>
      <el-table :data="disputes" size="small">
        <el-table-column label="编号" width="60"><template #default="{ row }">#{{ row.id }}</template></el-table-column>
        <el-table-column label="集市" min-width="120">
          <template #default="{ row }">{{ row.event?.name }}</template>
        </el-table-column>
        <el-table-column label="争议摊位/摊主" min-width="220">
          <template #default="{ row }">
            <div>{{ row.resultA?.stallNo }} · {{ row.resultA?.application?.vendor?.name }}</div>
            <div v-if="row.resultB" class="text-muted">
              {{ row.resultB.stallNo }} · {{ row.resultB.application?.vendor?.name }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="争议情况" min-width="150" show-overflow-tooltip />
        <el-table-column label="现场/标注" width="90">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="viewDetail(row)">查看</el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态/结论" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'SUBMITTED'" size="small" type="warning">待认定</el-tag>
            <template v-else>
              <el-tag size="small" :type="DISPUTE_CONCLUSION[row.conclusion]?.type">
                {{ DISPUTE_CONCLUSION[row.conclusion]?.text || row.conclusion }}
              </el-tag>
              <el-tag v-if="row.waived" size="small" type="success" class="ml">已免罚</el-tag>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUBMITTED'" size="small" type="primary"
              @click="openHandle(row)">认定处理</el-button>
            <span v-else class="text-muted">{{ row.handler?.name }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!disputes.length" description="暂无边界争议" :image-size="60" />
    </el-card>

    <el-row :gutter="16" class="mt">
      <!-- 风险点 -->
      <el-col :span="12">
        <el-card>
          <div class="card-title">摊位图风险点（争议频发区域，下次集市前须重新测量）</div>
          <el-table :data="risks" size="small">
            <el-table-column label="区域" min-width="130">
              <template #default="{ row }">{{ row.zone?.name }}</template>
            </el-table-column>
            <el-table-column label="争议起数" width="80" align="center">
              <template #default="{ row }">
                <el-badge :value="row.disputeCount" type="danger" />
              </template>
            </el-table-column>
            <el-table-column label="超面积/图不清" width="110" align="center">
              <template #default="{ row }">{{ row.overAreaCount }} / {{ row.mapUnclearCount }}</template>
            </el-table-column>
            <el-table-column label="处置状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="RISK_STATUS[row.riskStatus]?.type">
                  {{ RISK_STATUS[row.riskStatus]?.text }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="small" type="primary" :disabled="row.riskStatus === 'RESOLVED'"
                  @click="openPublish(row.zone)">发布更新图</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-alert type="info" :closable="false" class="mt"
            title="同一区域边界争议累计达到 2 起即列为风险点；发布重新测量后的更新图后自动解除。" />
        </el-card>
      </el-col>

      <!-- 摊位图版本 -->
      <el-col :span="12">
        <el-card>
          <div style="display:flex;justify-content:space-between" class="mb">
            <div class="card-title" style="margin:0">摊位图版本（调整依据对新老摊主公示）</div>
            <el-button size="small" type="primary" @click="openPublish(null)">发布新图</el-button>
          </div>
          <el-table :data="mapVersions" size="small" max-height="320">
            <el-table-column label="版本" width="70">
              <template #default="{ row }">v{{ row.versionNo }}</template>
            </el-table-column>
            <el-table-column label="范围" min-width="120">
              <template #default="{ row }">{{ row.scopeLabel }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="MAP_VERSION_STATUS[row.status]?.type">
                  {{ MAP_VERSION_STATUS[row.status]?.text }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="发布时间" width="140">
              <template #default="{ row }">{{ fmtTime(row.publishedAt || row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 整改通知复查 -->
    <el-card class="mt">
      <div class="card-title">整改通知（认定超抽签面积后自动生成，复查整改情况）</div>
      <el-table :data="rectifications" size="small">
        <el-table-column label="编号" width="60"><template #default="{ row }">#{{ row.id }}</template></el-table-column>
        <el-table-column label="摊位/摊主" min-width="150">
          <template #default="{ row }">{{ row.stallNo }} · {{ row.vendor?.name }}</template>
        </el-table-column>
        <el-table-column prop="requirement" label="整改要求" min-width="260" show-overflow-tooltip />
        <el-table-column label="扣分" width="70">
          <template #default="{ row }">
            <span v-if="row.creditDelta" style="color:#f56c6c;font-weight:600">{{ row.creditDelta }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="限期" width="145">
          <template #default="{ row }">{{ fmtTime(row.deadline) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="RECTIFY_STATUS[row.status]?.type">{{ RECTIFY_STATUS[row.status]?.text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="复查" width="150">
          <template #default="{ row }">
            <template v-if="row.status === 'ISSUED'">
              <el-button size="small" type="success" @click="verifyRect(row, true)">已整改</el-button>
              <el-button size="small" type="warning" @click="verifyRect(row, false)">未达标</el-button>
            </template>
            <span v-else class="text-muted">{{ fmtTime(row.doneAt) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rectifications.length" description="暂无整改通知" :image-size="60" />
    </el-card>

    <!-- 现场详情 -->
    <el-dialog v-model="detailVisible" title="争议现场与实际边界标注" width="640px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="争议情况">{{ detail.description }}</el-descriptions-item>
          <el-descriptions-item label="边界测量说明">{{ detail.actualBoundaryNote || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="mt">
          <div class="text-muted mb">现场照片（红线为标注的实际边界）：</div>
          <template v-for="url in photos(detail.scenePhotos)" :key="url">
            <div class="photo-box">
              <img :src="url" />
              <svg class="mark" viewBox="0 0 100 100" preserveAspectRatio="none">
                <polyline v-if="markedPoints(detail.actualBoundaryData).length > 1"
                  :points="toPoly(detail.actualBoundaryData)" fill="none" stroke="#f56c6c"
                  stroke-width="1.2" vector-effect="non-scaling-stroke" stroke-dasharray="3 2" />
              </svg>
            </div>
          </template>
        </div>
      </template>
    </el-dialog>

    <!-- 认定处理 -->
    <el-dialog v-model="handleVisible" title="边界争议认定" width="560px">
      <template v-if="current">
        <el-alert type="info" :closable="false" class="mb"
          :title="`争议 #${current.id}：${current.resultA?.stallNo} 与 ${current.resultB?.stallNo || '相邻摊'}`" />
        <el-form label-width="110px">
          <el-form-item label="认定结论" required>
            <el-radio-group v-model="handleForm.conclusion">
              <el-radio label="OVER_AREA">一方超抽签面积（整改+扣分）</el-radio>
              <el-radio label="MAP_UNCLEAR">抽签图不清晰（免除扣罚，更新摊位图）</el-radio>
              <el-radio label="NO_VIOLATION">双方均未违规</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="handleForm.conclusion === 'OVER_AREA'" label="超面积一方" required>
            <el-select v-model="handleForm.violatorResultId" style="width: 100%">
              <el-option :label="`${current.resultA?.stallNo} - ${current.resultA?.application?.vendor?.name}`"
                :value="current.resultA?.id" />
              <el-option v-if="current.resultB"
                :label="`${current.resultB.stallNo} - ${current.resultB.application?.vendor?.name}`"
                :value="current.resultB.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="handleForm.conclusion === 'OVER_AREA'" label="扣信用分">
            <el-input-number v-model="handleForm.penaltyPoints" :min="1" :max="30" />
            <span class="text-muted ml">默认 5 分，同时下发限期整改通知</span>
          </el-form-item>
          <el-form-item label="处理说明" required>
            <el-input v-model="handleForm.resolution" type="textarea" :rows="3"
              placeholder="向双方公开的认定依据与处理结果" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitHandle">提交认定</el-button>
      </template>
    </el-dialog>

    <!-- 发布更新图 -->
    <el-dialog v-model="publishVisible" title="发布重新测量后的更新版摊位图" width="560px">
      <el-form :model="publishForm" label-width="110px">
        <el-form-item label="覆盖范围" required>
          <el-radio-group v-model="publishForm.scopeType">
            <el-radio label="ZONE">区域图</el-radio>
            <el-radio label="GLOBAL">全场图</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="publishForm.scopeType === 'ZONE'" label="所属区域" required>
          <el-select v-model="publishForm.zoneId" style="width: 100%">
            <el-option v-for="z in zones" :key="z.id" :label="z.name" :value="z.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="范围说明">
          <el-input v-model="publishForm.scopeLabel" placeholder="如：D区 D-03~D-04 边界通道" />
        </el-form-item>
        <el-form-item label="重新测量图" required>
          <ImageUpload v-model="publishForm.imageUrl" />
        </el-form-item>
        <el-form-item label="更新原因" required>
          <el-input v-model="publishForm.reason" type="textarea" :rows="2"
            placeholder="必填，向新老摊主公开，如：该区域两起边界争议实测通道仅2.1m，据实测重新划定边界" />
        </el-form-item>
        <el-form-item label="调整依据/测量数据">
          <el-input v-model="publishForm.changeSummary" type="textarea" :rows="2"
            placeholder="如：D-03 右边界内移0.3m，通道由2.1m恢复至2.4m；依据争议#3、#5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPublish">发布并公示</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { fmtTime, DISPUTE_CONCLUSION, RISK_STATUS, MAP_VERSION_STATUS, RECTIFY_STATUS } from '../../utils/dict'
import ImageUpload from '../../components/ImageUpload.vue'

const events = ref([])
const zones = ref([])
const disputes = ref([])
const risks = ref([])
const mapVersions = ref([])
const rectifications = ref([])
const eventId = ref(null)
const saving = ref(false)

const detailVisible = ref(false)
const detail = ref(null)
const handleVisible = ref(false)
const current = ref(null)
const handleForm = ref({ conclusion: 'OVER_AREA', violatorResultId: null, penaltyPoints: 5, resolution: '' })
const publishVisible = ref(false)
const publishForm = ref({ scopeType: 'ZONE', zoneId: null, scopeLabel: '', imageUrl: '', reason: '', changeSummary: '' })

function photos(s) { return (s || '').split(',').filter(Boolean) }
function markedPoints(data) {
  try { return data ? JSON.parse(data).points || [] : [] } catch { return [] }
}
function toPoly(data) { return markedPoints(data).map(p => p.join(',')).join(' ') }

function viewDetail(row) { detail.value = row; detailVisible.value = true }

function openHandle(row) {
  current.value = row
  handleForm.value = {
    conclusion: 'OVER_AREA',
    violatorResultId: row.resultA?.id,
    penaltyPoints: 5,
    resolution: ''
  }
  handleVisible.value = true
}

async function submitHandle() {
  if (!handleForm.value.resolution) return ElMessage.warning('请填写处理说明')
  if (handleForm.value.conclusion === 'OVER_AREA' && !handleForm.value.violatorResultId) {
    return ElMessage.warning('请选择超面积一方')
  }
  saving.value = true
  try {
    await api.post(`/admin/boundary-disputes/${current.value.id}/handle`, handleForm.value)
    ElMessage.success(handleForm.value.conclusion === 'OVER_AREA' ? '已下发整改并扣分留痕'
      : handleForm.value.conclusion === 'MAP_UNCLEAR' ? '已免除本次扣罚，并列入重新测量' : '已办结')
    handleVisible.value = false
    await loadAll()
  } finally { saving.value = false }
}

function openPublish(zone) {
  publishForm.value = {
    scopeType: zone ? 'ZONE' : 'ZONE',
    zoneId: zone?.id || zones.value[0]?.id || null,
    scopeLabel: zone ? zone.name + ' 边界通道' : '',
    imageUrl: '', reason: '', changeSummary: ''
  }
  publishVisible.value = true
}

async function submitPublish() {
  const f = publishForm.value
  if (!f.imageUrl || !f.reason) return ElMessage.warning('请上传测量图并填写更新原因')
  if (f.scopeType === 'ZONE' && !f.zoneId) return ElMessage.warning('请选择区域')
  saving.value = true
  try {
    await api.post('/admin/stall-map-versions/publish', f)
    ElMessage.success('更新版摊位图已发布，新老摊主均可查看调整依据')
    publishVisible.value = false
    await loadAll()
  } finally { saving.value = false }
}

async function loadDisputes() {
  disputes.value = await api.get('/admin/boundary-disputes',
    { params: eventId.value ? { eventId: eventId.value } : {} })
}
async function loadAll() {
  const [, rs, ms, rects] = await Promise.all([
    loadDisputes(),
    api.get('/admin/boundary-risk-points'),
    api.get('/admin/stall-map-versions'),
    api.get('/admin/rectifications', { params: eventId.value ? { eventId: eventId.value } : {} })
  ])
  risks.value = rs
  mapVersions.value = ms
  rectifications.value = rects
}

async function verifyRect(row, done) {
  let note = ''
  try {
    const res = await ElMessageBox.prompt(
      done ? '复查备注（确认已退回抽签边界、腾出通道）' : '复查备注（说明仍未达标之处）',
      done ? '整改复查通过' : '标记逾期未改',
      { confirmButtonText: '提交', cancelButtonText: '取消', inputType: 'textarea' }
    )
    note = res.value || ''
  } catch { return }
  await api.post(`/admin/rectifications/${row.id}/verify`, { done, note })
  ElMessage.success(done ? '已确认整改完成' : '已标记逾期未改')
  await loadAll()
}

onMounted(async () => {
  const [evs, zs] = await Promise.all([api.get('/admin/events'), api.get('/admin/zones')])
  events.value = evs; zones.value = zs
  await loadAll()
})
</script>

<style scoped>
.photo-box { position: relative; margin-bottom: 8px; }
.photo-box img { width: 100%; border-radius: 4px; display: block; }
.mark { position: absolute; inset: 0; width: 100%; height: 100%; }
.ml { margin-left: 6px; }
</style>
