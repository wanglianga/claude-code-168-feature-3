<template>
  <div class="page">
    <el-card>
      <div style="display:flex;justify-content:space-between;align-items:center" class="mb">
        <div class="card-title" style="margin:0">摊位图风险点与重新测量发版</div>
        <el-button type="primary" @click="openPublish">重新测量·发布更新图</el-button>
      </div>
      <el-alert type="error" :closable="false" class="mb"
        title="边界争议频繁（同一相邻摊位对达 2 次）的区域自动标记为风险点；管理员须在下一次集市前重新测量并发布更新图，发版后风险点关闭，调整依据对老摊主与新摊主公开。" />

      <el-table :data="risks" size="small">
        <el-table-column label="#" width="50" prop="id" />
        <el-table-column label="风险位置" min-width="180">
          <template #default="{ row }">
            {{ row.location }}
            <el-tag size="small" style="margin-left:6px">{{ row.zone?.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="争议次数" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="danger">{{ row.disputeCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="riskType(row.status)">{{ riskText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="解决版本" width="100">
          <template #default="{ row }">{{ row.resolvedVersion?.versionNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <template v-if="row.status === 'OPEN'">
              <el-button size="small" type="primary" @click="openPublish(row)">重测发版</el-button>
              <el-button size="small" @click="ignore(row)">忽略</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="mt">
      <div class="card-title">摊位图版本历史（新老摊主均可见调整依据）</div>
      <el-timeline>
        <el-timeline-item v-for="v in versions" :key="v.id"
          :type="v.status === 'PUBLISHED' ? 'success' : 'info'"
          :timestamp="`${v.versionNo} · ${fmtTime(v.publishedAt || v.createdAt)}`">
          <el-card shadow="never">
            <div style="display:flex;gap:14px;align-items:flex-start">
              <el-image :src="v.imageUrl" :preview-src-list="[v.imageUrl]" class="map-thumb" fit="contain" preview-teleported />
              <div style="flex:1">
                <div><b>调整依据：</b>{{ v.changeReason }}</div>
                <div class="text-muted mt" style="font-size:12px">发布人：{{ v.publisher?.name }}</div>
                <div v-if="parsedDetails(v.changeDetails).length" class="mt">
                  <el-tag v-for="(c, i) in parsedDetails(v.changeDetails)" :key="i" size="small" class="mr"
                    type="warning">{{ c.stallNo }}：{{ c.before }} → {{ c.after }}</el-tag>
                </div>
              </div>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="!versions.length" description="暂无摊位图版本" :image-size="60" />
    </el-card>

    <!-- 重新测量发版 -->
    <el-dialog v-model="publishVisible" title="重新测量·发布新版摊位图" width="580px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="新版本号">
          <el-input v-model="form.versionNo" placeholder="留空自动生成，如 v2、v3" />
        </el-form-item>
        <el-form-item label="更新摊位图" required>
          <ImageUpload v-model="form.imageUrl" />
        </el-form-item>
        <el-form-item label="调整依据" required>
          <el-input v-model="form.changeReason" type="textarea" :rows="2"
            placeholder="必填，向新老摊主公开，说明本次为何重测、边界如何调整" />
        </el-form-item>
        <el-form-item label="变更明细">
          <el-input v-model="form.changeDetails" type="textarea" :rows="2"
            placeholder='选填 JSON，如 [{"stallNo":"B-03","before":"面宽3.0m","after":"面宽2.7m","reason":"让通道0.3m"}]' />
        </el-form-item>
        <el-form-item label="解决风险点">
          <el-select v-model="form.riskIds" multiple style="width:100%" placeholder="本次重测后关闭的风险点">
            <el-option v-for="r in openRisks" :key="r.id" :label="`#${r.id} ${r.location}`" :value="r.id" />
          </el-select>
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { fmtTime } from '../../utils/dict'
import ImageUpload from '../../components/ImageUpload.vue'

const risks = ref([])
const versions = ref([])
const saving = ref(false)
const publishVisible = ref(false)
const form = ref({ versionNo: '', imageUrl: '', changeReason: '', changeDetails: '', riskIds: [] })

const openRisks = computed(() => risks.value.filter(r => r.status === 'OPEN'))

function parsedDetails(s) {
  if (!s) return []
  try { return JSON.parse(s) } catch { return [] }
}
function riskText(s) { return { OPEN: '待重测', RESOLVED: '已解决', IGNORED: '已忽略' }[s] || s }
function riskType(s) { return { OPEN: 'danger', RESOLVED: 'success', IGNORED: 'info' }[s] || 'info' }

async function load() {
  const [rs, vs] = await Promise.all([api.get('/admin/map-risks'), api.get('/admin/map-versions')])
  risks.value = rs
  versions.value = vs
}

function openPublish(risk) {
  form.value = {
    versionNo: '', imageUrl: '', changeReason: '', changeDetails: '',
    riskIds: risk && risk.status === 'OPEN' ? [risk.id] : []
  }
  publishVisible.value = true
}

async function submitPublish() {
  if (!form.value.imageUrl) return ElMessage.warning('请上传更新后的摊位图')
  if (!form.value.changeReason) return ElMessage.warning('请填写调整依据')
  saving.value = true
  try {
    const v = await api.post('/admin/map-versions', form.value)
    ElMessage.success(`已发布 ${v.versionNo}，新老摊主均可查看调整依据`)
    publishVisible.value = false
    await load()
  } finally { saving.value = false }
}

async function ignore(row) {
  try {
    const { value } = await ElMessageBox.prompt('确认忽略该风险点？请说明原因（不会发版重测）', '忽略风险点', {
      confirmButtonText: '确认忽略', cancelButtonText: '取消', inputType: 'textarea'
    })
    await api.post(`/admin/map-risks/${row.id}/ignore`, { remark: value || '经核实无需重测' })
    ElMessage.success('已忽略')
    await load()
  } catch { /* 取消 */ }
}

onMounted(load)
</script>

<style scoped>
.mt { margin-top: 14px; }
.mr { margin: 2px; }
.map-thumb { width: 180px; height: 110px; border: 1px solid #ebeef5; border-radius: 4px; background: #fafafa; flex-shrink: 0; }
</style>
