<template>
  <div class="page">
    <el-card>
      <div class="card-title">摊位图公示（重新测量后的更新图，新老摊主均可查看调整依据）</div>
      <el-table :data="mapVersions" size="small">
        <el-table-column label="版本" width="70">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="覆盖范围" min-width="150">
          <template #default="{ row }">{{ row.scopeLabel }}</template>
        </el-table-column>
        <el-table-column label="更新图" width="80">
          <template #default="{ row }">
            <el-image v-if="row.imageUrl" :src="row.imageUrl" :preview-src-list="[row.imageUrl]"
              class="thumb" fit="cover" preview-teleported />
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="更新原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="changeSummary" label="调整依据/测量数据" min-width="200" show-overflow-tooltip />
        <el-table-column label="发布人" width="110">
          <template #default="{ row }">{{ row.publisher?.name }}</template>
        </el-table-column>
        <el-table-column label="发布时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.publishedAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!mapVersions.length" description="暂未发布更新版摊位图" :image-size="60" />
    </el-card>

    <el-row :gutter="16" class="mt">
      <el-col :span="12">
        <el-card>
          <div class="card-title">涉及我的边界争议</div>
          <div v-for="d in disputes" :key="d.id" class="item">
            <div>
              <el-tag size="small">#{{ d.id }}</el-tag>
              <el-tag size="small" class="ml" type="primary">{{ d.resultA?.stallNo }}{{ d.resultB ? ' / ' + d.resultB.stallNo : '' }}</el-tag>
              <el-tag v-if="d.status === 'SUBMITTED'" size="small" type="warning" class="ml">待认定</el-tag>
              <el-tag v-else size="small" :type="conclusionType(d.conclusion)" class="ml">
                {{ conclusionText(d.conclusion) }}
              </el-tag>
              <el-tag v-if="d.penalized" size="small" type="danger" class="ml">扣 {{ d.penaltyPoints }} 分</el-tag>
              <el-tag v-if="d.waived" size="small" type="success" class="ml">已免罚</el-tag>
            </div>
            <div class="mt">{{ d.description }}</div>
            <div v-if="d.actualBoundaryNote" class="text-muted mt">实际边界：{{ d.actualBoundaryNote }}</div>
            <div v-if="d.resolution" class="resolution mt">认定结果：{{ d.resolution }}</div>
            <div class="text-muted mt">{{ fmtTime(d.createdAt) }}</div>
          </div>
          <el-empty v-if="!disputes.length" description="暂无边界争议" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <div class="card-title">我的整改通知（超抽签面积/占道）</div>
          <div v-for="r in rectifications" :key="r.id" class="item">
            <div>
              <el-tag size="small" type="danger">{{ r.stallNo }}</el-tag>
              <el-tag size="small" class="ml" :type="RECTIFY_STATUS[r.status]?.type">
                {{ RECTIFY_STATUS[r.status]?.text }}
              </el-tag>
              <el-tag v-if="r.creditDelta" size="small" type="warning" class="ml">信用 {{ r.creditDelta }}</el-tag>
            </div>
            <div class="mt">整改要求：{{ r.requirement }}</div>
            <div class="text-muted mt">限期：{{ fmtTime(r.deadline) }}</div>
            <div v-if="r.doneAt" class="text-muted mt">复查通过时间：{{ fmtTime(r.doneAt) }}</div>
          </div>
          <el-empty v-if="!rectifications.length" description="暂无整改通知" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../../api'
import { fmtTime, RECTIFY_STATUS } from '../../utils/dict'

const mapVersions = ref([])
const disputes = ref([])
const rectifications = ref([])

function conclusionText(c) {
  return { OVER_AREA: '我方/对方超面积', MAP_UNCLEAR: '抽签图不清晰·已免罚', NO_VIOLATION: '双方未违规' }[c] || c
}
function conclusionType(c) {
  return { OVER_AREA: 'danger', MAP_UNCLEAR: 'warning', NO_VIOLATION: 'info' }[c] || 'info'
}

onMounted(async () => {
  // 摊位图走公开元数据接口，新老摊主（含刚报名的新摊主）均可查看
  const [maps, ds, rs] = await Promise.all([
    api.get('/meta/stall-map-versions'),
    api.get('/vendor/boundary-disputes'),
    api.get('/vendor/rectifications')
  ])
  mapVersions.value = maps
  disputes.value = ds
  rectifications.value = rs
})
</script>

<style scoped>
.item { border-bottom: 1px solid #ebeef5; padding: 10px 0; }
.resolution { background: #f0f9eb; padding: 6px 10px; border-radius: 4px; font-size: 13px; }
.thumb { width: 50px; height: 38px; border-radius: 4px; }
.ml { margin-left: 6px; }
</style>
