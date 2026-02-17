<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  createAdminPsyCenter,
  deleteAdminPsyCenter,
  getAdminPsyCenters,
  updateAdminPsyCenter,
  type AdminPsyCenter,
} from '@/api/cms'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const saving = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const rows = ref<AdminPsyCenter[]>([])

const keyword = ref('')
const cityCode = ref('')

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)

const form = reactive<Omit<AdminPsyCenter, 'id'>>({
  name: '',
  cityCode: '',
  cityName: '',
  district: '',
  address: '',
  phone: '',
  latitude: undefined,
  longitude: undefined,
  recommended: false,
  enabled: true,
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
      item.cityName.toLowerCase().includes(key)
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
  form.recommended = false
  form.enabled = true
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
  form.recommended = row.recommended
  form.enabled = row.enabled
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
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: AdminPsyCenter) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
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
    recommended: form.recommended,
    enabled: form.enabled,
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
    await ElMessageBox.confirm('删除后不可恢复，确认继续？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteAdminPsyCenter(row.id)
    ElMessage.success('删除成功')
    await loadRows()
  } catch (error) {
    if (error === 'cancel') return
    const parsed = parseError(error, '删除失败')
    ElMessage.error(parsed.detail)
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
          <el-button type="primary" @click="openCreate">新增心理中心</el-button>
        </div>
      </div>
    </template>

    <el-form inline>
      <el-form-item label="城市编码">
        <el-input v-model="cityCode" placeholder="例如 310100" clearable />
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="keyword" placeholder="名称/地址/城市" clearable />
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
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="cityCode" label="城市编码" width="110" />
      <el-table-column prop="cityName" label="城市" width="110" />
      <el-table-column prop="district" label="区域" width="120" />
      <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column label="推荐" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.recommended ? 'success' : 'info'">{{ scope.row.recommended ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'danger'">{{ scope.row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="scope">
          <el-button type="primary" link @click="openEdit(scope.row)">编辑</el-button>
          <el-button type="danger" link @click="removeCenter(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
    <el-form label-width="110px">
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
      <el-form-item label="推荐">
        <el-switch v-model="form.recommended" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" />
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
}

.header-actions {
  display: flex;
  gap: 8px;
}
</style>
