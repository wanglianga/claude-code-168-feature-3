<template>
  <div class="page">
    <el-card class="mb">
      <div style="display:flex;justify-content:space-between;align-items:center" class="mb">
        <div class="card-title" style="margin:0">摊位图与调整依据公示（新老摊主均可查看）</div>
        <el-tag v-if="mapInfo.current" type="success">当前版本 {{ mapInfo.current.versionNo }}</el-tag>
      </div>
      <el-alert v-if="mapInfo.openRiskCount > 0" type="error" :closable="false" class="mb"
        :title="`有 ${mapInfo.openRiskCount} 处边界争议频繁区域已标记为风险点，管理员将在下一次集市前重新测量并发布更新图。`" />
      <div v-if="mapInfo.current" style="display:flex;gap:14px;align-items:flex-start">
        <el-image :src="mapInfo.current.imageUrl" :preview-src-list="[mapInfo.current.imageUrl]"
          class="map-img" fit="contain" preview-teleported />
        <div style="flex:1">
          <div><b>本次调整依据：</b>{{ mapInfo.current.changeReason }}</div>
          <div class="text-muted mt" style="font-size:12px">
            发布人 {{ mapInfo.current.publisher?.name }} · {{ fmtTime(mapInfo.current.publishedAt) }}
          </div>
          <el-collapse v-if="mapInfo.history?.length > 1" class="mt">
            <el-collapse-item title="查看历史版本调整依据">
              <div v-for="v in mapInfo.history" :key="v.id" class="history-item">
                <el-tag size="small" type="success">{{ v.versionNo }}</el-tag>
                <span style="margin-left:6px">{{ v.changeReason }}</span>
                <span class="text-muted" style="margin-left:6px;font-size:12px">{{ fmtTime(v.publishedAt) }}</span>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
      <el-empty v-else description="摊位图尚未发布" :image-size="50" />
    </el-card>

    <el-card>
      <div class="card-title">摊位报名（赶集日前提交）</div>
      <el-alert v-if="!openEvent" type="warning" :closable="false" title="当前没有开放报名的集市" class="mb" />
      <template v-else>
        <el-alert type="info" :closable="false" class="mb"
          :title="`报名集市：${openEvent.name}（赶集日 ${openEvent.marketDate}）`"
          :description="openEvent.remark" />
        <el-alert v-if="existing" type="success" :closable="false" class="mb"
          :title="`您已提交报名，状态：${statusText(existing.status)}`" />
        <el-form v-else :model="form" label-width="110px" style="max-width: 640px">
          <el-form-item label="经营品类" required>
            <el-select v-model="form.category" placeholder="选择品类" style="width: 100%">
              <el-option v-for="(name, code) in categories" :key="code" :label="name" :value="code" />
            </el-select>
          </el-form-item>
          <el-form-item label="车辆尺寸(米)">
            <el-input-number v-model="form.vehicleLength" :min="0" :step="0.5" placeholder="长" />
            <span style="margin: 0 8px">×</span>
            <el-input-number v-model="form.vehicleWidth" :min="0" :step="0.5" placeholder="宽" />
          </el-form-item>
          <el-form-item label="用电需求">
            <el-switch v-model="form.needsPower" active-text="需要用电" />
          </el-form-item>
          <el-form-item label="售卖熟食">
            <el-switch v-model="form.sellsCookedFood" active-text="是（需冷链区+留样）" />
          </el-form-item>
          <el-form-item label="售卖活禽">
            <el-switch v-model="form.sellsLivePoultry" active-text="是（需隔离区）" />
          </el-form-item>
          <el-form-item label="证照照片">
            <ImageUpload v-model="form.licensePhotos" />
            <div class="text-muted">上传营业执照/食品经营许可证等</div>
          </el-form-item>
          <el-form-item label="历史经营记录">
            <el-input v-model="form.historyRecord" type="textarea" :rows="3"
              placeholder="如：连续三年在本集市经营卤味，无投诉记录" />
          </el-form-item>
          <el-form-item>
            <el-checkbox :model-value="true" disabled>
              老摊主认定：历史经营≥3次且信用≥60（当前：{{ auth.user?.historyCount }}次 / {{ auth.user?.creditScore }}分）
            </el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submit">提交报名</el-button>
          </el-form-item>
        </el-form>
      </template>
    </el-card>

    <el-card class="mt">
      <div class="card-title">我的报名记录</div>
      <el-table :data="applications" size="small">
        <el-table-column label="集市" min-width="140">
          <template #default="{ row }">{{ row.event?.name }}<br /><span class="text-muted">{{ row.event?.marketDate }}</span></template>
        </el-table-column>
        <el-table-column label="品类" width="110">
          <template #default="{ row }">{{ categories[row.category] }}</template>
        </el-table-column>
        <el-table-column label="需求" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.needsPower" size="small" class="mr">用电</el-tag>
            <el-tag v-if="row.sellsCookedFood" size="small" type="warning" class="mr">熟食</el-tag>
            <el-tag v-if="row.sellsLivePoultry" size="small" type="danger" class="mr">活禽</el-tag>
            <el-tag v-if="row.veteran" size="small" type="success" class="mr">老摊主</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewNote" label="审核备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="报名时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { useAuthStore } from '../../store/auth'
import { CATEGORIES, fmtTime } from '../../utils/dict'
import ImageUpload from '../../components/ImageUpload.vue'

const auth = useAuthStore()
const categories = CATEGORIES
const events = ref([])
const applications = ref([])
const mapInfo = ref({ current: null, history: [], openRiskCount: 0 })
const submitting = ref(false)
const form = ref({
  category: '', vehicleLength: null, vehicleWidth: null,
  needsPower: false, sellsCookedFood: false, sellsLivePoultry: false,
  licensePhotos: '', historyRecord: ''
})

const openEvent = computed(() => events.value.find(e => e.status === 'OPEN'))
const existing = computed(() => openEvent.value
  ? applications.value.find(a => a.event?.id === openEvent.value.id) : null)

function statusText(s) {
  return { PENDING: '待审核', APPROVED: '已通过(待抽签)', REJECTED: '已驳回', WON: '已中签', LOST: '未中签' }[s] || s
}
function statusType(s) {
  return { PENDING: 'warning', APPROVED: 'primary', REJECTED: 'danger', WON: 'success', LOST: 'info' }[s] || 'info'
}

async function load() {
  const [evs, apps, mi] = await Promise.all([
    api.get('/meta/events'), api.get('/vendor/applications'), api.get('/meta/stall-map/current')
  ])
  events.value = evs
  applications.value = apps
  mapInfo.value = mi
}

async function submit() {
  if (!form.value.category) return ElMessage.warning('请选择经营品类')
  submitting.value = true
  try {
    await api.post('/vendor/applications', { ...form.value, eventId: openEvent.value.id })
    ElMessage.success('报名提交成功，等待市场管理审核')
    await load()
  } finally { submitting.value = false }
}

onMounted(load)
</script>

<style scoped>
.mr { margin-right: 4px; }
.map-img { width: 260px; height: 150px; border: 1px solid #ebeef5; border-radius: 4px; background: #fafafa; flex-shrink: 0; }
.history-item { padding: 4px 0; border-bottom: 1px dashed #ebeef5; font-size: 13px; }
</style>
