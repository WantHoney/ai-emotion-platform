<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  createArticle,
  createBanner,
  createBook,
  createQuote,
  deleteArticle,
  deleteBanner,
  deleteBook,
  deleteQuote,
  getArticles,
  getBanners,
  getBooks,
  getQuotes,
  updateArticle,
  updateBanner,
  updateBook,
  updateQuote,
  type CmsArticle,
  type CmsBanner,
  type CmsBook,
  type CmsContentType,
  type CmsQuote,
} from '@/api/cms'
import { parseError, type ErrorStatePayload } from '@/utils/error'

type CmsRow = CmsBanner | CmsQuote | CmsArticle | CmsBook
const props = withDefaults(
  defineProps<{
    fixedTab?: CmsContentType
    title?: string
  }>(),
  {
    fixedTab: undefined,
    title: '内容运营管理',
  },
)

const loading = ref(false)
const saving = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const activeTab = ref<CmsContentType>(props.fixedTab ?? 'banner')

const banners = ref<CmsBanner[]>([])
const quotes = ref<CmsQuote[]>([])
const articles = ref<CmsArticle[]>([])
const books = ref<CmsBook[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)

const form = reactive({
  title: '',
  imageUrl: '',
  linkUrl: '',
  startsAt: '',
  endsAt: '',
  content: '',
  author: '',
  summary: '',
  contentUrl: '',
  publishedAt: '',
  description: '',
  purchaseUrl: '',
  sortOrder: 100,
  recommended: false,
  enabled: true,
})

const currentRows = computed<CmsRow[]>(() => {
  switch (activeTab.value) {
    case 'banner':
      return banners.value
    case 'quote':
      return quotes.value
    case 'article':
      return articles.value
    case 'book':
      return books.value
    default:
      return []
  }
})

const dialogTitle = computed(() => {
  const action = dialogMode.value === 'create' ? '新增' : '编辑'
  const nameMap: Record<CmsContentType, string> = {
    banner: '轮播图',
    quote: '语录',
    article: '文章',
    book: '书籍',
  }
  return `${action}${nameMap[activeTab.value]}`
})

const resetForm = () => {
  form.title = ''
  form.imageUrl = ''
  form.linkUrl = ''
  form.startsAt = ''
  form.endsAt = ''
  form.content = ''
  form.author = ''
  form.summary = ''
  form.contentUrl = ''
  form.publishedAt = ''
  form.description = ''
  form.purchaseUrl = ''
  form.sortOrder = 100
  form.recommended = false
  form.enabled = true
}

const hydrateForm = (row: CmsRow) => {
  resetForm()
  if ('title' in row) form.title = row.title ?? ''
  if ('imageUrl' in row) form.imageUrl = row.imageUrl ?? ''
  if ('linkUrl' in row) form.linkUrl = row.linkUrl ?? ''
  if ('startsAt' in row) form.startsAt = row.startsAt ?? ''
  if ('endsAt' in row) form.endsAt = row.endsAt ?? ''
  if ('content' in row) form.content = row.content ?? ''
  if ('author' in row) form.author = row.author ?? ''
  if ('summary' in row) form.summary = row.summary ?? ''
  if ('contentUrl' in row) form.contentUrl = row.contentUrl ?? ''
  if ('publishedAt' in row) form.publishedAt = row.publishedAt ?? ''
  if ('description' in row) form.description = row.description ?? ''
  if ('purchaseUrl' in row) form.purchaseUrl = row.purchaseUrl ?? ''
  if ('sortOrder' in row) form.sortOrder = Number(row.sortOrder ?? 100)
  if ('recommended' in row) form.recommended = Boolean(row.recommended)
  if ('enabled' in row) form.enabled = Boolean(row.enabled)
}

const validateForm = () => {
  if (activeTab.value === 'banner') {
    if (!form.title.trim() || !form.imageUrl.trim()) {
      ElMessage.warning('Banner 标题和图片地址必填')
      return false
    }
  }
  if (activeTab.value === 'quote' && !form.content.trim()) {
    ElMessage.warning('语录内容必填')
    return false
  }
  if (activeTab.value === 'article' && !form.title.trim()) {
    ElMessage.warning('文章标题必填')
    return false
  }
  if (activeTab.value === 'book' && !form.title.trim()) {
    ElMessage.warning('书籍标题必填')
    return false
  }
  return true
}

const loadTabData = async (tab: CmsContentType) => {
  loading.value = true
  errorState.value = null
  try {
    switch (tab) {
      case 'banner':
        banners.value = await getBanners()
        break
      case 'quote':
        quotes.value = await getQuotes()
        break
      case 'article':
        articles.value = await getArticles()
        break
      case 'book':
        books.value = await getBooks()
        break
    }
  } catch (error) {
    errorState.value = parseError(error, '内容运营数据加载失败')
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

const openEdit = (row: CmsRow) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  hydrateForm(row)
  dialogVisible.value = true
}

const saveContent = async () => {
  if (!validateForm()) return
  saving.value = true
  try {
    if (activeTab.value === 'banner') {
      const payload = {
        title: form.title.trim(),
        imageUrl: form.imageUrl.trim(),
        linkUrl: form.linkUrl.trim() || undefined,
        sortOrder: form.sortOrder,
        recommended: form.recommended,
        enabled: form.enabled,
        startsAt: form.startsAt || undefined,
        endsAt: form.endsAt || undefined,
      }
      if (dialogMode.value === 'create') await createBanner(payload)
      else await updateBanner(editingId.value as number, payload)
    }

    if (activeTab.value === 'quote') {
      const payload = {
        content: form.content.trim(),
        author: form.author.trim() || undefined,
        sortOrder: form.sortOrder,
        recommended: form.recommended,
        enabled: form.enabled,
      }
      if (dialogMode.value === 'create') await createQuote(payload)
      else await updateQuote(editingId.value as number, payload)
    }

    if (activeTab.value === 'article') {
      const payload = {
        title: form.title.trim(),
        coverImageUrl: form.imageUrl.trim() || undefined,
        summary: form.summary.trim() || undefined,
        contentUrl: form.contentUrl.trim() || undefined,
        sortOrder: form.sortOrder,
        recommended: form.recommended,
        enabled: form.enabled,
        publishedAt: form.publishedAt || undefined,
      }
      if (dialogMode.value === 'create') await createArticle(payload)
      else await updateArticle(editingId.value as number, payload)
    }

    if (activeTab.value === 'book') {
      const payload = {
        title: form.title.trim(),
        author: form.author.trim() || undefined,
        coverImageUrl: form.imageUrl.trim() || undefined,
        description: form.description.trim() || undefined,
        purchaseUrl: form.purchaseUrl.trim() || undefined,
        sortOrder: form.sortOrder,
        recommended: form.recommended,
        enabled: form.enabled,
      }
      if (dialogMode.value === 'create') await createBook(payload)
      else await updateBook(editingId.value as number, payload)
    }

    ElMessage.success(dialogMode.value === 'create' ? '创建成功' : '保存成功')
    dialogVisible.value = false
    await loadTabData(activeTab.value)
  } catch (error) {
    const parsed = parseError(error, '保存失败')
    ElMessage.error(parsed.detail)
  } finally {
    saving.value = false
  }
}

const removeContent = async (row: CmsRow) => {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认继续？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    if (activeTab.value === 'banner') await deleteBanner(row.id)
    if (activeTab.value === 'quote') await deleteQuote(row.id)
    if (activeTab.value === 'article') await deleteArticle(row.id)
    if (activeTab.value === 'book') await deleteBook(row.id)
    ElMessage.success('删除成功')
    await loadTabData(activeTab.value)
  } catch (error) {
    if (error === 'cancel') return
    const parsed = parseError(error, '删除失败')
    ElMessage.error(parsed.detail)
  }
}

const onTabChange = async (name: string | number) => {
  if (props.fixedTab) return
  activeTab.value = name as CmsContentType
  await loadTabData(activeTab.value)
}

onMounted(async () => {
  if (props.fixedTab) {
    activeTab.value = props.fixedTab
  }
  await loadTabData(activeTab.value)
})
</script>

<template>
  <el-card shadow="hover">
    <template #header>
      <div class="header-row">
        <span>{{ props.title }}</span>
        <div class="header-actions">
          <el-button @click="loadTabData(activeTab)">刷新</el-button>
          <el-button type="primary" @click="openCreate">新增内容</el-button>
        </div>
      </div>
    </template>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadTabData(activeTab)"
    />
    <template v-else>
      <el-tabs v-if="!props.fixedTab" :model-value="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="轮播图" name="banner" />
        <el-tab-pane label="语录" name="quote" />
        <el-tab-pane label="文章" name="article" />
        <el-tab-pane label="书籍" name="book" />
      </el-tabs>

      <EmptyState
        v-if="currentRows.length === 0"
        title="暂无内容"
        description="当前分类下没有内容，点击“新增内容”开始运营。"
        action-text="新增"
        @action="openCreate"
      />

      <el-table v-else :data="currentRows" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column v-if="activeTab === 'quote'" prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column v-if="activeTab !== 'quote'" prop="author" label="作者" min-width="120" />
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="推荐" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.recommended ? 'success' : 'info'">{{ scope.row.recommended ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'danger'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="removeContent(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px">
    <el-form label-width="110px">
      <template v-if="activeTab === 'quote'">
        <el-form-item label="语录内容" required>
          <el-input v-model="form.content" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="form.author" />
        </el-form-item>
      </template>

      <template v-else>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" />
        </el-form-item>
      </template>

      <template v-if="activeTab === 'banner'">
        <el-form-item label="图片地址" required>
          <el-input v-model="form.imageUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="form.linkUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="生效开始">
          <el-date-picker
            v-model="form.startsAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="可选"
          />
        </el-form-item>
        <el-form-item label="生效结束">
          <el-date-picker
            v-model="form.endsAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="可选"
          />
        </el-form-item>
      </template>

      <template v-if="activeTab === 'article'">
        <el-form-item label="封面地址">
          <el-input v-model="form.imageUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="内容链接">
          <el-input v-model="form.contentUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker
            v-model="form.publishedAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="可选"
          />
        </el-form-item>
      </template>

      <template v-if="activeTab === 'book'">
        <el-form-item label="作者">
          <el-input v-model="form.author" />
        </el-form-item>
        <el-form-item label="封面地址">
          <el-input v-model="form.imageUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="购买链接">
          <el-input v-model="form.purchaseUrl" placeholder="https://..." />
        </el-form-item>
      </template>

      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
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
      <el-button type="primary" :loading="saving" @click="saveContent">保存</el-button>
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
