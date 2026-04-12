<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import SmartImage from '@/components/ui/SmartImage.vue'
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
import {
  ARTICLE_CATEGORY_OPTIONS,
  ARTICLE_DIFFICULTY_OPTIONS,
  DATA_SOURCE_LABELS,
} from '@/constants/contentMeta'
import { parseError, type ErrorStatePayload } from '@/utils/error'
import { resolveImageUrl } from '@/utils/contentMedia'

type CmsRow = CmsBanner | CmsQuote | CmsArticle | CmsBook

const props = withDefaults(
  defineProps<{
    fixedTab?: CmsContentType
    title?: string
  }>(),
  {
    fixedTab: undefined,
    title: '内容管理',
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
const editingRow = ref<CmsRow | null>(null)

const form = reactive({
  title: '',
  imageUrl: '',
  linkUrl: '',
  startsAt: '',
  endsAt: '',
  content: '',
  author: '',
  summary: '',
  recommendReason: '',
  fitFor: '',
  highlights: '',
  readingMinutes: undefined as number | undefined,
  category: 'stress',
  sourceName: '',
  sourceUrl: '',
  isExternal: true,
  difficultyTag: 'beginner',
  publishedAt: '',
  description: '',
  purchaseUrl: '',
  sortOrder: 100,
  recommended: false,
  enabled: true,
  isActive: true,
  seedKey: '',
  dataSource: '',
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

const previewImageKind = computed(() => (activeTab.value === 'book' ? 'book' : 'article'))
const previewImageUrl = computed(() => {
  if (activeTab.value === 'book') return resolveImageUrl(form.imageUrl, 'book')
  if (activeTab.value === 'article') return resolveImageUrl(form.imageUrl, 'article')
  return form.imageUrl
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
  form.recommendReason = ''
  form.fitFor = ''
  form.highlights = ''
  form.readingMinutes = undefined
  form.category = 'stress'
  form.sourceName = ''
  form.sourceUrl = ''
  form.isExternal = true
  form.difficultyTag = 'beginner'
  form.publishedAt = ''
  form.description = ''
  form.purchaseUrl = ''
  form.sortOrder = 100
  form.recommended = false
  form.enabled = true
  form.isActive = true
  form.seedKey = ''
  form.dataSource = ''
}

const hydrateForm = (row: CmsRow) => {
  resetForm()
  if ('title' in row) form.title = row.title ?? ''
  if ('imageUrl' in row) form.imageUrl = row.imageUrl ?? ''
  if ('coverImageUrl' in row) form.imageUrl = row.coverImageUrl ?? ''
  if ('linkUrl' in row) form.linkUrl = row.linkUrl ?? ''
  if ('startsAt' in row) form.startsAt = row.startsAt ?? ''
  if ('endsAt' in row) form.endsAt = row.endsAt ?? ''
  if ('content' in row) form.content = row.content ?? ''
  if ('author' in row) form.author = row.author ?? ''
  if ('summary' in row) form.summary = row.summary ?? ''
  if ('recommendReason' in row) form.recommendReason = row.recommendReason ?? ''
  if ('fitFor' in row) form.fitFor = row.fitFor ?? ''
  if ('highlights' in row) form.highlights = row.highlights ?? ''
  if ('readingMinutes' in row) form.readingMinutes = row.readingMinutes ?? undefined
  if ('category' in row) form.category = row.category ?? 'stress'
  if ('sourceName' in row) form.sourceName = row.sourceName ?? ''
  if ('sourceUrl' in row) form.sourceUrl = row.sourceUrl ?? row.contentUrl ?? ''
  if ('isExternal' in row) form.isExternal = row.isExternal ?? true
  if ('difficultyTag' in row) form.difficultyTag = row.difficultyTag ?? 'beginner'
  if ('publishedAt' in row) form.publishedAt = row.publishedAt ?? ''
  if ('description' in row) form.description = row.description ?? ''
  if ('purchaseUrl' in row) form.purchaseUrl = row.purchaseUrl ?? ''
  if ('sortOrder' in row) form.sortOrder = Number(row.sortOrder ?? 100)
  if ('recommended' in row) form.recommended = Boolean(row.recommended)
  if ('enabled' in row) form.enabled = Boolean(row.enabled)
  if ('isActive' in row) form.isActive = Boolean(row.isActive)
  if ('seedKey' in row) form.seedKey = row.seedKey ?? ''
  if ('dataSource' in row) form.dataSource = row.dataSource ?? ''
}

const validateForm = () => {
  if (activeTab.value === 'banner') {
    if (!form.title.trim() || !form.imageUrl.trim()) {
      ElMessage.warning('轮播图标题和图片地址不能为空')
      return false
    }
  }

  if (activeTab.value === 'quote' && !form.content.trim()) {
    ElMessage.warning('语录内容必填')
    return false
  }

  if (activeTab.value === 'article') {
    if (!form.title.trim()) {
      ElMessage.warning('文章标题必填')
      return false
    }
    if (!form.sourceUrl.trim()) {
      ElMessage.warning('文章来源链接必填')
      return false
    }
  }

  if (activeTab.value === 'book') {
    if (!form.title.trim()) {
      ElMessage.warning('书籍标题必填')
      return false
    }
    if (!form.category) {
      ElMessage.warning('请为书籍选择所属主题')
      return false
    }
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
  editingRow.value = null
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: CmsRow) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  editingRow.value = row
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
        createdAt: undefined,
        updatedAt: undefined,
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
        seedKey: form.seedKey || undefined,
        dataSource: form.dataSource || undefined,
        isActive: form.isActive,
        createdAt: undefined,
        updatedAt: undefined,
      }
      if (dialogMode.value === 'create') await createQuote(payload)
      else await updateQuote(editingId.value as number, payload)
    }

    if (activeTab.value === 'article') {
      const payload = {
        title: form.title.trim(),
        coverImageUrl: form.imageUrl.trim() || undefined,
        summary: form.summary.trim() || undefined,
        recommendReason: form.recommendReason.trim() || undefined,
        fitFor: form.fitFor.trim() || undefined,
        highlights: form.highlights.trim() || undefined,
        readingMinutes: form.readingMinutes,
        category: form.category,
        sourceName: form.sourceName.trim() || undefined,
        sourceUrl: form.sourceUrl.trim(),
        contentUrl: form.sourceUrl.trim(),
        isExternal: form.isExternal,
        difficultyTag: form.difficultyTag,
        sortOrder: form.sortOrder,
        recommended: form.recommended,
        enabled: form.enabled,
        seedKey: form.seedKey || undefined,
        dataSource: form.dataSource || undefined,
        isActive: form.isActive,
        publishedAt: form.publishedAt || undefined,
        createdAt: undefined,
        updatedAt: undefined,
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
        category: form.category,
        recommendReason: form.recommendReason.trim() || undefined,
        fitFor: form.fitFor.trim() || undefined,
        highlights: form.highlights.trim() || undefined,
        purchaseUrl: form.purchaseUrl.trim() || undefined,
        sortOrder: form.sortOrder,
        recommended: form.recommended,
        enabled: form.enabled,
        seedKey: form.seedKey || undefined,
        dataSource: form.dataSource || undefined,
        isActive: form.isActive,
        createdAt: undefined,
        updatedAt: undefined,
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
  const isBanner = activeTab.value === 'banner'
  const title = isBanner ? '删除确认' : '停用确认'
  const message = isBanner ? '删除后不可恢复，确认继续？' : '该操作会将记录置为停用，用户端将不再展示。是否继续？'
  const successText = isBanner ? '删除成功' : '已停用'

  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: isBanner ? '删除' : '停用',
      cancelButtonText: '取消',
    })
    if (activeTab.value === 'banner') await deleteBanner(row.id)
    if (activeTab.value === 'quote') await deleteQuote(row.id)
    if (activeTab.value === 'article') await deleteArticle(row.id)
    if (activeTab.value === 'book') await deleteBook(row.id)
    ElMessage.success(successText)
    await loadTabData(activeTab.value)
  } catch (error) {
    if (error === 'cancel') return
    const parsed = parseError(error, isBanner ? '删除失败' : '停用失败')
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
        description="当前分类下没有数据，点击“新增内容”即可开始维护。"
        action-text="新增"
        @action="openCreate"
      />

      <el-table v-else :data="currentRows" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column
          v-if="activeTab === 'quote'"
          prop="content"
          label="内容"
          min-width="320"
          show-overflow-tooltip
        />
        <el-table-column v-else prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column v-if="activeTab === 'article' || activeTab === 'book'" prop="category" label="主题" width="120">
          <template #default="scope">
            {{ ARTICLE_CATEGORY_OPTIONS.find((item) => item.value === scope.row.category)?.label || '-' }}
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'article'" prop="sourceName" label="来源" min-width="140" />
        <el-table-column v-if="activeTab === 'article'" prop="readingMinutes" label="时长" width="90">
          <template #default="scope">{{ scope.row.readingMinutes ? `${scope.row.readingMinutes} 分钟` : '-' }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'book'" prop="author" label="作者" min-width="140" />
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="推荐" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.recommended ? 'success' : 'info'">{{ scope.row.recommended ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="展示" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'warning'">{{ scope.row.enabled ? '启用' : '关闭' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'banner'" label="活跃" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.isActive ? 'success' : 'danger'">{{ scope.row.isActive ? '活跃' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'banner'" label="数据来源" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.dataSource === 'seed' ? 'success' : 'info'">
              {{ DATA_SOURCE_LABELS[scope.row.dataSource || 'manual'] || '人工维护' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="removeContent(scope.row)">
              {{ activeTab === 'banner' ? '删除' : '停用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px">
    <el-alert
      v-if="editingRow && activeTab !== 'banner' && 'dataSource' in editingRow && editingRow.dataSource === 'seed'"
      class="seed-alert"
      type="info"
      :closable="false"
      title="数据来源：默认种子"
      description="可编辑，但来源标记不变。"
    />

    <el-form label-width="120px">
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
        <el-form-item label="链接地址">
          <el-input v-model="form.linkUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="预览">
          <img v-if="form.imageUrl" class="banner-preview" :src="form.imageUrl" alt="banner preview" />
          <span v-else class="hint-text">输入图片地址后显示预览</span>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.startsAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="可选"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="form.endsAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="可选"
          />
        </el-form-item>
      </template>

      <template v-if="activeTab === 'article'">
        <el-form-item label="主题插画（可选）">
          <el-input v-model="form.imageUrl" placeholder="/assets/articles/..." />
        </el-form-item>
        <el-form-item label="插画预览">
          <div class="preview-wrap">
            <SmartImage :src="previewImageUrl" alt="article preview" :kind="previewImageKind" />
          </div>
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="推荐理由">
          <el-input v-model="form.recommendReason" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="适合谁">
          <el-input v-model="form.fitFor" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="核心收获">
          <el-input
            v-model="form.highlights"
            type="textarea"
            :rows="3"
            placeholder="每行一个要点，前端会按列表展示。"
          />
        </el-form-item>
        <el-form-item label="阅读时长">
          <el-input-number v-model="form.readingMinutes" :min="1" :max="120" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 220px">
            <el-option
              v-for="item in ARTICLE_CATEGORY_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源名称">
          <el-input v-model="form.sourceName" placeholder="例如：WHO / CDC / 国家卫生健康委" />
        </el-form-item>
        <el-form-item label="来源链接" required>
          <el-input v-model="form.sourceUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="外部链接">
          <el-switch v-model="form.isExternal" />
        </el-form-item>
        <el-form-item label="难度标签">
          <el-select v-model="form.difficultyTag" style="width: 220px">
            <el-option
              v-for="item in ARTICLE_DIFFICULTY_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
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
        <el-form-item label="封面路径">
          <el-input v-model="form.imageUrl" placeholder="/assets/books/..." />
        </el-form-item>
        <el-form-item label="封面预览">
          <div class="preview-wrap book-preview-wrap">
            <SmartImage :src="previewImageUrl" alt="book preview" :kind="previewImageKind" />
          </div>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="主题分类">
          <el-select v-model="form.category" style="width: 220px">
            <el-option
              v-for="item in ARTICLE_CATEGORY_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="推荐理由">
          <el-input v-model="form.recommendReason" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="适合谁">
          <el-input v-model="form.fitFor" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="核心收获">
          <el-input
            v-model="form.highlights"
            type="textarea"
            :rows="3"
            placeholder="每行一个要点，前端会按列表展示。"
          />
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="form.purchaseUrl" placeholder="https://..." />
        </el-form-item>
      </template>

      <el-form-item v-if="activeTab !== 'banner' && dialogMode === 'edit'" label="seedKey">
        <el-input :model-value="form.seedKey || 'manual 记录无 seedKey'" disabled />
      </el-form-item>
      <el-form-item v-if="activeTab !== 'banner' && dialogMode === 'edit'" label="数据来源">
        <el-input :model-value="DATA_SOURCE_LABELS[form.dataSource || 'manual'] || '人工维护'" disabled />
      </el-form-item>

      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
      </el-form-item>
      <el-form-item label="推荐">
        <el-switch v-model="form.recommended" />
      </el-form-item>
      <el-form-item label="展示启用">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item v-if="activeTab !== 'banner'" label="记录活跃">
        <el-switch v-model="form.isActive" />
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
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.seed-alert {
  margin-bottom: 16px;
}

.hint-text {
  color: #7a8ca5;
  font-size: 13px;
}

.banner-preview {
  width: min(100%, 320px);
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  display: block;
}

.preview-wrap {
  width: min(100%, 320px);
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #0f172a;
}

.book-preview-wrap {
  aspect-ratio: 3 / 4;
}
</style>
