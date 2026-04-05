<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  createAdminPsyCenter,
  deleteAdminPsyCenter,
  exportPsyCentersCsv,
  getAdminPsyCenters,
  importPsyCentersCsv,
  updateAdminPsyCenter,
  type AdminPsyCenter,
} from '@/api/cms'
import { DATA_SOURCE_LABELS, PSY_CENTER_CITY_OPTIONS, SOURCE_LEVEL_LABELS } from '@/constants/contentMeta'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const rows = ref<AdminPsyCenter[]>([])

const keyword = ref('')
const cityCode = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const editingRow = ref<AdminPsyCenter | null>(null)
const importInputRef = ref<HTMLInputElement | null>(null)

const form = reactive<Omit<AdminPsyCenter, 'id'>>({
  name: '',
  cityCode: '',
  cityName: '',
  district: '',
  address: '',
  phone: '',
  latitude: undefined,
  longitude: undefined,
  sourceName: '',
  sourceUrl: '',
  sourceLevel: 'official',
  recommended: false,
  enabled: true,
  seedKey: undefined,
  dataSource: undefined,
  isActive: true,
  createdAt: undefined,
  updatedAt: undefined,
})

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增心理中心' : '编辑心理中心'))

const filteredRows = computed(() => {
  return rows.value.filter((item) => {
    if (cityCode.value && item.cityCode !== cityCode.value.trim()) return false
    if (!keyword.value.trim()) return true
    const key = keyword.value.trim().toLowerCase()
    return (
      item.name.toLowerCase().includes(key) ||
      item.address.toLowerCase().includes(key) ||
      item.cityName.toLowerCase().includes(key) ||
      (item.sourceName || '').toLowerCase().includes(key)
    )
  })
})

const resetForm = () => {
  form.name = ''
  form.cityCode = ''
  form.cityName = ''
  form.district = ''
  form.address = ''
  form.phone = ''
  form.latitude = undefined
  form.longitude = undefined
  form.sourceName = ''
  form.sourceUrl = ''
  form.sourceLevel = 'official'
  form.recommended = false
  form.enabled = true
  form.seedKey = undefined
  form.dataSource = undefined
  form.isActive = true
  form.createdAt = undefined
  form.updatedAt = undefined
}

const hydrateForm = (row: AdminPsyCenter) => {
  form.name = row.name
  form.cityCode = row.cityCode
  form.cityName = row.cityName
  form.district = row.district ?? ''
  form.address = row.address
  form.phone = row.phone ?? ''
  form.latitude = row.latitude
  form.longitude = row.longitude
  form.sourceName = row.sourceName ?? ''
  form.sourceUrl = row.sourceUrl ?? ''
  form.sourceLevel = row.sourceLevel ?? 'official'
  form.recommended = row.recommended
  form.enabled = row.enabled
  form.seedKey = row.seedKey
  form.dataSource = row.dataSource
  form.isActive = row.isActive
  form.createdAt = row.createdAt
  form.updatedAt = row.updatedAt
}

const validateForm = () => {
  if (!form.name.trim() || !form.cityCode.trim() || !form.cityName.trim() || !form.address.trim()) {
    ElMessage.warning('名称、城市编码、城市名称、地址为必填项')
    return false
  }
  return true
}

const loadRows = async () => {
  loading.value = true
  errorState.value = null
  try {
    rows.value = await getAdminPsyCenters()
  } catch (error) {
    errorState.value = parseError(error, '心理中心管理数据加载失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  dialogMode.value = 'create'
  editingId.value = null
  editingRow.value = null
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: AdminPsyCenter) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  editingRow.value = row
  hydrateForm(row)
  dialogVisible.value = true
}

const saveCenter = async () => {
  if (!validateForm()) return
  saving.value = true
  const payload: Omit<AdminPsyCenter, 'id'> = {
    name: form.name.trim(),
    cityCode: form.cityCode.trim(),
    cityName: form.cityName.trim(),
    district: form.district?.trim() || undefined,
    address: form.address.trim(),
    phone: form.phone?.trim() || undefined,
    latitude: form.latitude,
    longitude: form.longitude,
    sourceName: form.sourceName?.trim() || undefined,
    sourceUrl: form.sourceUrl?.trim() || undefined,
    sourceLevel: form.sourceLevel || undefined,
    recommended: form.recommended,
    enabled: form.enabled,
    seedKey: form.seedKey,
    dataSource: form.dataSource,
    isActive: form.isActive,
    createdAt: form.createdAt,
    updatedAt: form.updatedAt,
  }

  try {
    if (dialogMode.value === 'create') {
      await createAdminPsyCenter(payload)
      ElMessage.success('新增成功')
    } else {
      await updateAdminPsyCenter(editingId.value as number, payload)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    await loadRows()
  } catch (error) {
    const parsed = parseError(error, '保存失败')
    ElMessage.error(parsed.detail)
  } finally {
    saving.value = false
  }
}

const removeCenter = async (row: AdminPsyCenter) => {
  try {
    await ElMessageBox.confirm('删除操作会将当前记录置为停用状态，用户端将不再展示。是否继续？', '停用确认', {
      type: 'warning',
      confirmButtonText: '停用',
      cancelButtonText: '取消',
    })
    await deleteAdminPsyCenter(row.id)
    ElMessage.success('已停用')
    await loadRows()
  } catch (error) {
    if (error === 'cancel') return
    const parsed = parseError(error, '停用失败')
    ElMessage.error(parsed.detail)
  }
}

const handleExport = async () => {
  try {
    const csv = await exportPsyCentersCsv()
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'psy-centers.csv'
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    const parsed = parseError(error, '导出失败')
    ElMessage.error(parsed.detail)
  }
}

const triggerImport = async () => {
  await nextTick()
  importInputRef.value?.click()
}

const handleImportChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  importing.value = true
  try {
    const content = await file.text()
    const result = await importPsyCentersCsv(content)
    ElMessage.success(`导入完成，共导入 ${result.imported ?? 0} 条`)
    await loadRows()
  } catch (error) {
    const parsed = parseError(error, '导入失败')
    ElMessage.error(parsed.detail)
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  await loadRows()
})
</script>

<template>
  <el-card shadow="hover">
    <template #header>
      <div class="header-row">
        <span>心理中心信息管理</span>
        <div class="header-actions">
          <el-button @click="loadRows">刷新</el-button>
          <el-button @click="handleExport">导出 CSV</el-button>
          <el-button :loading="importing" @click="triggerImport">导入 CSV</el-button>
          <el-button type="primary" @click="openCreate">新增心理中心</el-button>
        </div>
      </div>
    </template>

    <input ref="importInputRef" class="hidden-input" type="file" accept=".csv,text/csv" @change="handleImportChange" />

    <el-form inline>
      <el-form-item label="城市">
        <el-select v-model="cityCode" clearable placeholder="全部城市" style="width: 160px">
          <el-option v-for="item in PSY_CENTER_CITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="keyword" placeholder="名称 / 地址 / 来源" clearable />
      </el-form-item>
    </el-form>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadRows"
    />
    <EmptyState
      v-else-if="filteredRows.length === 0"
      title="暂无心理中心数据"
      description="当前筛选条件下没有匹配数据。"
      action-text="重新加载"
      @action="loadRows"
    />
    <el-table v-else :data="filteredRows" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="cityName" label="城市" width="100" />
      <el-table-column prop="district" label="区域" width="120" />
      <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
      <el-table-column prop="phone" label="电话" width="140" />
      <el-table-column label="来源等级" width="110">
        <template #default="scope">
          <el-tag type="info">{{ SOURCE_LEVEL_LABELS[scope.row.sourceLevel || ''] || '待补充' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数据来源" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.dataSource === 'seed' ? 'success' : 'info'">
            {{ DATA_SOURCE_LABELS[scope.row.dataSource || 'manual'] || '人工维护' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="seedKey" label="seedKey" min-width="180" show-overflow-tooltip />
      <el-table-column label="活跃" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.isActive ? 'success' : 'danger'">{{ scope.row.isActive ? '活跃' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="展示" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'warning'">{{ scope.row.enabled ? '启用' : '关闭' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="scope">
          <el-button type="primary" link @click="openEdit(scope.row)">编辑</el-button>
          <el-button type="danger" link @click="removeCenter(scope.row)">停用</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
    <el-alert
      v-if="editingRow?.dataSource === 'seed'"
      class="seed-alert"
      type="info"
      :closable="false"
      title="数据来源：默认种子"
      description="可编辑，但来源标记不变。"
    />

    <el-form label-width="118px">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="城市编码" required>
        <el-input v-model="form.cityCode" />
      </el-form-item>
      <el-form-item label="城市名称" required>
        <el-input v-model="form.cityName" />
      </el-form-item>
      <el-form-item label="区域">
        <el-input v-model="form.district" />
      </el-form-item>
      <el-form-item label="地址" required>
        <el-input v-model="form.address" />
      </el-form-item>
      <el-form-item label="电话">
        <el-input v-model="form.phone" />
      </el-form-item>
      <el-form-item label="纬度">
        <el-input-number v-model="form.latitude" :precision="6" :step="0.0001" />
      </el-form-item>
      <el-form-item label="经度">
        <el-input-number v-model="form.longitude" :precision="6" :step="0.0001" />
      </el-form-item>
      <el-form-item label="来源名称">
        <el-input v-model="form.sourceName" placeholder="例如：北京大学第六医院" />
      </el-form-item>
      <el-form-item label="来源链接">
        <el-input v-model="form.sourceUrl" placeholder="https://..." />
      </el-form-item>
      <el-form-item label="来源等级">
        <el-select v-model="form.sourceLevel" style="width: 220px">
          <el-option label="官方来源" value="official" />
          <el-option label="政务目录" value="gov_directory" />
          <el-option label="可信参考" value="trusted_reference" />
        </el-select>
      </el-form-item>
      <el-form-item label="seedKey" v-if="dialogMode === 'edit'">
        <el-input :model-value="form.seedKey || 'manual 记录无 seedKey'" disabled />
      </el-form-item>
      <el-form-item label="数据来源" v-if="dialogMode === 'edit'">
        <el-input :model-value="DATA_SOURCE_LABELS[form.dataSource || 'manual'] || '人工维护'" disabled />
      </el-form-item>
      <el-form-item label="推荐">
        <el-switch v-model="form.recommended" />
      </el-form-item>
      <el-form-item label="展示启用">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item label="记录活跃">
        <el-switch v-model="form.isActive" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveCenter">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hidden-input {
  display: none;
}

.seed-alert {
  margin-bottom: 16px;
}
</style>
