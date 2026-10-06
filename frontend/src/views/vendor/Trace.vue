<template>
  <div class="page">
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card>
          <div class="card-title">涉及我的调整记录（调位/减免/扣罚/信用，均附原因与来源）</div>
          <el-table :data="adjustments" size="small">
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="adjType(row.type).type">{{ adjType(row.type).text }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="变更" min-width="200">
              <template #default="{ row }">{{ row.beforeValue }} → {{ row.afterValue }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip />
            <el-table-column label="来源" width="100">
              <template #default="{ row }">{{ sourceText(row.source) }}</template>
            </el-table-column>
            <el-table-column label="操作人" width="110">
              <template #default="{ row }">{{ row.operator?.name }}</template>
            </el-table-column>
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!adjustments.length" description="暂无调整记录" :image-size="60" />
        </el-card>

        <el-card class="mt">
          <div class="card-title">信用变动记录（当前 {{ auth.user?.creditScore }} 分）</div>
          <el-table :data="creditLogs" size="small">
            <el-table-column label="变动" width="90">
              <template #default="{ row }">
                <span :style="{ color: row.delta < 0 ? '#f56c6c' : '#67c23a', fontWeight: 600 }">
                  {{ row.delta > 0 ? '+' : '' }}{{ row.delta }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="scoreAfter" label="变动后" width="80" />
            <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
            <el-table-column label="来源" width="90">
              <template #default="{ row }">
                {{ { PATROL: '巡查', COMPLAINT: '投诉', ADJUSTMENT: '人工调整', SYSTEM: '系统', BOUNDARY_DISPUTE: '边界超面积', BOUNDARY_WAIVER: '图不清豁免回滚' }[row.sourceType] }}
              </template>
            </el-table-column>
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!creditLogs.length" description="暂无信用变动" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card>
          <div class="card-title">我的申诉 / 被投诉记录</div>
          <el-button type="primary" size="small" class="mb" @click="dialogVisible = true">提交申诉</el-button>
          <div v-for="c in complaints" :key="c.id" class="complaint-item">
            <div>
              <el-tag size="small" :type="c.reporterType === 'VENDOR' ? 'primary' : 'danger'">
                {{ c.reporterType === 'VENDOR' ? '我的申诉' : '消费者投诉' }}
              </el-tag>
              <el-tag size="small" style="margin-left:6px" :type="statusType(c.status)">{{ statusText(c.status) }}</el-tag>
            </div>
            <div class="mt"><b>{{ typeText(c.type) }}</b>：{{ c.description }}</div>
            <div v-if="c.resolution" class="resolution">处理结果：{{ c.resolution }}</div>
            <div class="text-muted mt">{{ fmtTime(c.createdAt) }}</div>
          </div>
          <el-empty v-if="!complaints.length" description="暂无记录" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-card class="mt">
      <div class="card-title">我的摊位边界争议与整改（含抽签面积、现场实测、判定与豁免依据）</div>
      <el-table :data="disputes" size="small" class="mb">
        <el-table-column label="争议摊位" min-width="130">
          <template #default="{ row }">
            <el-tag size="small">{{ row.resultA?.stallNo }}</el-tag>
            <span class="text-muted">×</span>
            <el-tag size="small" type="warning">{{ row.resultB?.stallNo }}</el-tag>
            <el-tag size="small" :type="mySide(row) === 'A' ? 'primary' : 'warning'" style="margin-left:6px">
              我是{{ mySide(row) }}方
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="我方抽签→实测" min-width="180">
          <template #default="{ row }">
            <span class="text-muted">{{ fmt(mySide(row) === 'A' ? row.lotWidthA : row.lotWidthB) }}×{{ fmt(mySide(row) === 'A' ? row.lotDepthA : row.lotDepthB) }}m</span>
            →
            <b :style="iExceed(row) ? 'color:#f56c6c' : ''">{{ fmt(mySide(row) === 'A' ? row.actualWidthA : row.actualWidthB) }}×{{ fmt(mySide(row) === 'A' ? row.actualDepthA : row.actualDepthB) }}m</b>
          </template>
        </el-table-column>
        <el-table-column label="判定" width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="verdictType(row.verdict)">{{ verdictText(row.verdict) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理状态" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="dStatusType(row.status)">{{ dStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="依据图版本" width="100">
          <template #default="{ row }">{{ row.mapVersion?.versionNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="复核/豁免说明" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reviewNote || '—' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!disputes.length" description="暂无边界争议记录" :image-size="60" />

      <div class="card-title mt">整改单（图不清晰被豁免时扣分自动回滚，见上方信用记录）</div>
      <el-table :data="rectifications" size="small">
        <el-table-column label="摊位" width="90">
          <template #default="{ row }">{{ row.result?.stallNo }}</template>
        </el-table-column>
        <el-table-column prop="requirement" label="整改要求" min-width="260" show-overflow-tooltip />
        <el-table-column label="扣分" width="80">
          <template #default="{ row }">
            <span :style="{ color: row.status === 'CANCELLED_WAIVED' ? '#67c23a' : '#f56c6c' }">
              {{ row.status === 'CANCELLED_WAIVED' ? '已回滚' : row.creditDelta }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="rectType(row.status)">{{ rectText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="复查说明" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.recheckNote || '—' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rectifications.length" description="暂无整改单" :image-size="60" />
    </el-card>

    <el-dialog v-model="dialogVisible" title="提交申诉（费用争议/扣罚争议/边界纠纷等）" width="520px">
      <el-form :model="appealForm" label-width="90px">
        <el-form-item label="所属集市" required>
          <el-select v-model="appealForm.eventId" style="width: 100%" @change="onEventChange">
            <el-option v-for="e in myEvents" :key="e.id" :label="`${e.name}（${e.marketDate}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="申诉类型" required>
          <el-select v-model="appealForm.type" style="width: 100%">
            <el-option label="费用争议" value="FEE_DISPUTE" />
            <el-option label="保证金扣罚争议" value="DEPOSIT_DISPUTE" />
            <el-option label="摊位边界纠纷" value="BOUNDARY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="申诉说明" required>
          <el-input v-model="appealForm.description" type="textarea" :rows="4"
            placeholder="请说明争议事项、诉求与依据" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAppeal">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { useAuthStore } from '../../store/auth'
import { fmtTime } from '../../utils/dict'

const auth = useAuthStore()
const adjustments = ref([])
const creditLogs = ref([])
const complaints = ref([])
const disputes = ref([])
const rectifications = ref([])
const results = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const appealForm = ref({ eventId: null, resultId: null, type: 'FEE_DISPUTE', description: '' })

const myEvents = computed(() => {
  const seen = new Map()
  results.value.forEach(r => seen.set(r.application.event.id, r.application.event))
  return [...seen.values()]
})

function adjType(t) {
  const map = {
    RELOCATE: { text: '临时调位', type: 'warning' },
    RAIN_RELOCATE: { text: '雨天调位', type: 'primary' },
    FEE_WAIVER: { text: '费用减免', type: 'success' },
    DEPOSIT_PENALTY: { text: '保证金扣罚', type: 'danger' },
    CREDIT_ADJUST: { text: '信用调整', type: 'warning' },
    CATEGORY_CHANGE: { text: '品类变更', type: 'info' }
  }
  return map[t] || { text: t, type: 'info' }
}
function sourceText(s) {
  return { RAIN: '雨天', COMPLAINT: '投诉处理', PATROL: '巡查记录', DISPUTE: '争议处理', REQUEST: '摊主申请', POLICY: '政策减免', OTHER: '其他' }[s] || s
}
function statusText(s) {
  return { SUBMITTED: '已提交', PROCESSING: '处理中', RESOLVED: '已办结', REJECTED: '已驳回' }[s] || s
}
function statusType(s) {
  return { SUBMITTED: 'warning', PROCESSING: 'primary', RESOLVED: 'success', REJECTED: 'info' }[s] || 'info'
}
function typeText(t) {
  return { DIARRHEA: '腹泻/食品安全', PRICE: '价格问题', SHORT_WEIGHT: '缺斤少两', HYGIENE: '卫生问题', QUALITY: '质量问题', BOUNDARY: '边界纠纷', FEE_DISPUTE: '费用争议', DEPOSIT_DISPUTE: '扣罚争议', OTHER: '其他' }[t] || t
}
function onEventChange(eventId) {
  const r = results.value.find(x => x.application.event.id === eventId)
  appealForm.value.resultId = r?.id || null
}

async function load() {
  const [adjs, logs, comps, res, ds, recs] = await Promise.all([
    api.get('/vendor/adjustments'), api.get('/vendor/credit-logs'),
    api.get('/vendor/complaints'), api.get('/vendor/results'),
    api.get('/vendor/boundary-disputes'), api.get('/vendor/rectifications')
  ])
  adjustments.value = adjs; creditLogs.value = logs; complaints.value = comps; results.value = res
  disputes.value = ds; rectifications.value = recs
}

const myId = computed(() => auth.user?.id)
function mySide(row) {
  return row.resultA?.application?.vendor?.id === myId.value ? 'A' : 'B'
}
function iExceed(row) {
  return (mySide(row) === 'A' && ['A', 'BOTH'].includes(row.verdict))
    || (mySide(row) === 'B' && ['B', 'BOTH'].includes(row.verdict))
}
function fmt(v) { return v == null ? '-' : Math.round(v * 100) / 100 }
function verdictText(v) { return { A: '主张方超面积', B: '被诉方超面积', BOTH: '双方均超', NONE: '均未超(疑图不清)' }[v] || v }
function verdictType(v) { return { A: 'danger', B: 'warning', BOTH: 'danger', NONE: 'info' }[v] || 'info' }
function dStatusText(s) { return { OPEN: '待复核', PENALIZED: '已整改扣分', WAIVED: '图不清已豁免', RESOLVED: '已办结', REJECTED: '已驳回' }[s] || s }
function dStatusType(s) { return { OPEN: 'warning', PENALIZED: 'danger', WAIVED: 'success', RESOLVED: 'success', REJECTED: 'info' }[s] || 'info' }
function rectText(s) { return { PENDING: '待整改', RECTIFIED: '待复查', PASSED: '复查通过', FAILED: '复查不通过', CANCELLED_WAIVED: '已豁免撤销' }[s] || s }
function rectType(s) { return { PENDING: 'danger', RECTIFIED: 'warning', PASSED: 'success', FAILED: 'danger', CANCELLED_WAIVED: 'info' }[s] || 'info' }

async function submitAppeal() {
  if (!appealForm.value.eventId || !appealForm.value.description) return ElMessage.warning('请完整填写')
  submitting.value = true
  try {
    await api.post('/vendor/complaints', appealForm.value)
    ElMessage.success('申诉已提交')
    dialogVisible.value = false
    appealForm.value.description = ''
    await load()
  } finally { submitting.value = false }
}

onMounted(async () => {
  await load()
  if (myEvents.value.length) {
    appealForm.value.eventId = myEvents.value[0].id
    onEventChange(appealForm.value.eventId)
  }
})
</script>

<style scoped>
.complaint-item { border-bottom: 1px solid #ebeef5; padding: 10px 0; }
.resolution { background: #f0f9eb; padding: 6px 10px; border-radius: 4px; margin-top: 6px; font-size: 13px; }
</style>
