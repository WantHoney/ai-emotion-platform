<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import type { ContentArticle, ContentBook, ContentTheme } from '@/api/content'
import ArchiveCalendar from '@/components/content/ArchiveCalendar.vue'
import ArticleFeatureCard from '@/components/content/ArticleFeatureCard.vue'
import BookShelfCard from '@/components/content/BookShelfCard.vue'
import ContentStateTabs from '@/components/content/ContentStateTabs.vue'
import QuoteHero from '@/components/content/QuoteHero.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  createSchedule,
  deleteSchedule,
  getArticles,
  getBooks,
  getQuotes,
  getSchedules,
  updateSchedule,
  type CmsArticle,
  type CmsBook,
  type CmsQuote,
  type CmsSchedule,
} from '@/api/cms'
import { ARTICLE_CATEGORY_LABELS, ARTICLE_CATEGORY_OPTIONS, CONTENT_STATE_TABS } from '@/constants/contentMeta'
import { parseError, type ErrorStatePayload } from '@/utils/error'

type DraftStatus = 'ACTIVE' | 'DRAFT'
type PoolCategory = 'all' | (typeof ARTICLE_CATEGORY_OPTIONS)[number]['value']
type ContentPoolKind = 'QUOTE' | 'ARTICLE' | 'BOOK'
type DropZone = 'quote' | 'featuredArticle' | 'secondaryArticle' | 'featuredBook' | 'secondaryBook'
type PendingAction = {
  label: string
  run: () => Promise<void>
  revert?: () => void
}

interface ScheduleDraft {
  scheduleDate: string
  themeKey: string
  themeTitle: string
  themeSubtitle: string
  quoteId?: number
  status: DraftStatus
  featuredArticleId?: number
  secondaryArticleIds: number[]
  featuredBookId?: number
  secondaryBookIds: number[]
}

interface DragPayload {
  kind: ContentPoolKind
  itemId: number
  source: 'pool' | DropZone
  index?: number
}

interface NormalizedDraft {
  scheduleDate: string
  themeKey: string
  themeTitle: string
  themeSubtitle: string | null
  quoteId: number | null
  status: DraftStatus
  featuredArticleId: number | null
  secondaryArticleIds: number[]
  featuredBookId: number | null
  secondaryBookIds: number[]
}

interface DropEvaluation {
  allowed: boolean
  insertIndex: number
  message: string
}

const loading = ref(false)
const saving = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)

const schedules = ref<CmsSchedule[]>([])
const quotes = ref<CmsQuote[]>([])
const articles = ref<CmsArticle[]>([])
const books = ref<CmsBook[]>([])

const appliedFilterDate = ref('')
const filterDateInput = ref('')
const poolSearch = ref('')
const poolCategory = ref<PoolCategory>('all')
const activeScheduleId = ref<number | null>(null)
const leaveDialogVisible = ref(false)
const pendingAction = ref<PendingAction | null>(null)

const dragPayload = ref<DragPayload | null>(null)
const dropState = reactive({
  zone: '' as DropZone | '',
  allowed: false,
  insertIndex: -1,
  message: '',
})

const draft = reactive<ScheduleDraft>({
  scheduleDate: '',
  themeKey: 'stress',
  themeTitle: '',
  themeSubtitle: '',
  quoteId: undefined,
  status: 'ACTIVE',
  featuredArticleId: undefined,
  secondaryArticleIds: [],
  featuredBookId: undefined,
  secondaryBookIds: [],
})

const savedSnapshot = ref<NormalizedDraft>({
  scheduleDate: '',
  themeKey: 'stress',
  themeTitle: '',
  themeSubtitle: null,
  quoteId: null,
  status: 'ACTIVE',
  featuredArticleId: null,
  secondaryArticleIds: [],
  featuredBookId: null,
  secondaryBookIds: [],
})

const presetMap = Object.fromEntries(
  CONTENT_STATE_TABS.map((item) => [
    item.value,
    {
      title: ARTICLE_CATEGORY_OPTIONS.find((option) => option.value === item.value)?.label || item.label,
      subtitle: item.description,
    },
  ]),
) as Record<string, { title: string; subtitle: string }>

const quoteMap = computed(() => new Map(quotes.value.map((item) => [item.id, item])))
const articleMap = computed(() => new Map(articles.value.map((item) => [item.id, item])))
const bookMap = computed(() => new Map(books.value.map((item) => [item.id, item])))

const activeQuote = computed(() => (draft.quoteId ? quoteMap.value.get(draft.quoteId) ?? null : null))
const featuredArticle = computed(() => (draft.featuredArticleId ? articleMap.value.get(draft.featuredArticleId) ?? null : null))
const featuredBook = computed(() => (draft.featuredBookId ? bookMap.value.get(draft.featuredBookId) ?? null : null))
const secondaryArticles = computed(() => draft.secondaryArticleIds.map((id) => articleMap.value.get(id)).filter((item): item is CmsArticle => Boolean(item)))
const secondaryBooks = computed(() => draft.secondaryBookIds.map((id) => bookMap.value.get(id)).filter((item): item is CmsBook => Boolean(item)))

function normalizeBlank(value?: string | null) {
  if (value == null) return null
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function uniqueIds(ids: number[]) {
  return Array.from(new Set(ids.filter((item) => Number.isFinite(item))))
}

function normalizeDraft(value: ScheduleDraft): NormalizedDraft {
  return {
    scheduleDate: value.scheduleDate || '',
    themeKey: value.themeKey || 'stress',
    themeTitle: value.themeTitle.trim(),
    themeSubtitle: normalizeBlank(value.themeSubtitle),
    quoteId: value.quoteId ?? null,
    status: value.status,
    featuredArticleId: value.featuredArticleId ?? null,
    secondaryArticleIds: uniqueIds(value.secondaryArticleIds).filter((id) => id !== value.featuredArticleId),
    featuredBookId: value.featuredBookId ?? null,
    secondaryBookIds: uniqueIds(value.secondaryBookIds).filter((id) => id !== value.featuredBookId),
  }
}

const normalizedDraft = computed(() => normalizeDraft(draft))
const isDirty = computed(() => JSON.stringify(normalizedDraft.value) !== JSON.stringify(savedSnapshot.value))

const activeQuoteOptions = computed(() => quotes.value.filter((item) => item.enabled && item.isActive))
const activeArticleOptions = computed(() => articles.value.filter((item) => item.enabled && item.isActive))
const activeBookOptions = computed(() => books.value.filter((item) => item.enabled && item.isActive))

const scheduleRows = computed(() => [...schedules.value].sort((left, right) => right.scheduleDate.localeCompare(left.scheduleDate)))
const selectedThemeLabel = computed(() => ARTICLE_CATEGORY_LABELS[draft.themeKey] || presetMap[draft.themeKey]?.title || draft.themeKey)
const visibleQuotes = computed(() => {
  const keyword = poolSearch.value.trim().toLowerCase()
  return activeQuoteOptions.value.filter((item) => {
    if (!keyword) return true
    return `${item.content} ${item.author || ''}`.toLowerCase().includes(keyword)
  })
})
const visibleArticles = computed(() => {
  const keyword = poolSearch.value.trim().toLowerCase()
  return activeArticleOptions.value.filter((item) => {
    const categoryMatched = poolCategory.value === 'all' || item.category === poolCategory.value
    if (!categoryMatched) return false
    if (!keyword) return true
    return `${item.title} ${item.summary || ''} ${item.sourceName || ''}`.toLowerCase().includes(keyword)
  })
})
const visibleBooks = computed(() => {
  const keyword = poolSearch.value.trim().toLowerCase()
  return activeBookOptions.value.filter((item) => {
    const categoryMatched = poolCategory.value === 'all' || item.category === poolCategory.value
    if (!categoryMatched) return false
    if (!keyword) return true
    return `${item.title} ${item.author || ''} ${item.description || ''}`.toLowerCase().includes(keyword)
  })
})

const previewTheme = computed<ContentTheme>(() => ({
  scheduleDate: draft.scheduleDate,
  themeKey: draft.themeKey,
  themeTitle: draft.themeTitle,
  themeSubtitle: draft.themeSubtitle,
  status: draft.status,
}))
const previewFeaturedArticle = computed(() => toPreviewArticle(featuredArticle.value))
const previewSecondaryArticles = computed(() =>
  secondaryArticles.value
    .map((item) => toPreviewArticle(item))
    .filter((item): item is ContentArticle => Boolean(item)),
)
const previewFeaturedBook = computed(() => toPreviewBook(featuredBook.value))
const previewSecondaryBooks = computed(() =>
  secondaryBooks.value
    .map((item) => toPreviewBook(item))
    .filter((item): item is ContentBook => Boolean(item)),
)
const previewArchiveDates = computed(() =>
  Array.from(new Set([...scheduleRows.value.map((item) => item.scheduleDate), draft.scheduleDate].filter(Boolean))).sort(),
)
const draftArticleCount = computed(() => (draft.featuredArticleId ? 1 : 0) + normalizedDraft.value.secondaryArticleIds.length)
const draftBookCount = computed(() => (draft.featuredBookId ? 1 : 0) + normalizedDraft.value.secondaryBookIds.length)
const draftModeLabel = computed(() => (activeScheduleId.value ? '编辑排期' : '新建排期'))

const formatDateLabel = (value?: string) => {
  if (!value) return '未选择日期'
  const [year, month, day] = value.split('-')
  if (!year || !month || !day) return value
  return `${year} 年 ${month} 月 ${day} 日`
}

const getShanghaiToday = () =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
  }).format(new Date())

const createEmptyDraft = (scheduleDate = appliedFilterDate.value || getShanghaiToday()): ScheduleDraft => {
  const preset = presetMap.stress
  return {
    scheduleDate,
    themeKey: 'stress',
    themeTitle: preset?.title || '压力管理',
    themeSubtitle: preset?.subtitle || '',
    quoteId: undefined,
    status: 'ACTIVE',
    featuredArticleId: undefined,
    secondaryArticleIds: [],
    featuredBookId: undefined,
    secondaryBookIds: [],
  }
}

function toPreviewArticle(article: CmsArticle | null | undefined): ContentArticle | null {
  if (!article) return null
  return {
    ...article,
    highlights:
      typeof article.highlights === 'string'
        ? article.highlights
            .split(/\r?\n/)
            .map((item) => item.trim())
            .filter(Boolean)
        : [],
  }
}

function toPreviewBook(book: CmsBook | null | undefined): ContentBook | null {
  if (!book) return null
  return {
    ...book,
    highlights:
      typeof book.highlights === 'string'
        ? book.highlights
            .split(/\r?\n/)
            .map((item) => item.trim())
            .filter(Boolean)
        : [],
  }
}

function applyDraft(nextDraft: ScheduleDraft) {
  draft.scheduleDate = nextDraft.scheduleDate
  draft.themeKey = nextDraft.themeKey
  draft.themeTitle = nextDraft.themeTitle
  draft.themeSubtitle = nextDraft.themeSubtitle
  draft.quoteId = nextDraft.quoteId
  draft.status = nextDraft.status
  draft.featuredArticleId = nextDraft.featuredArticleId
  draft.secondaryArticleIds = [...nextDraft.secondaryArticleIds]
  draft.featuredBookId = nextDraft.featuredBookId
  draft.secondaryBookIds = [...nextDraft.secondaryBookIds]
}

function refreshActiveScheduleSnapshot() {
  savedSnapshot.value = normalizeDraft(draft)
}

function scheduleToDraft(schedule: CmsSchedule): ScheduleDraft {
  return {
    scheduleDate: schedule.scheduleDate,
    themeKey: schedule.themeKey,
    themeTitle: schedule.themeTitle,
    themeSubtitle: schedule.themeSubtitle ?? '',
    quoteId: schedule.quoteId,
    status: schedule.status,
    featuredArticleId: schedule.items.find((item) => item.contentType === 'ARTICLE' && item.slotRole === 'FEATURED')?.contentId,
    secondaryArticleIds: schedule.items
      .filter((item) => item.contentType === 'ARTICLE' && item.slotRole === 'SECONDARY')
      .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
      .map((item) => item.contentId),
    featuredBookId: schedule.items.find((item) => item.contentType === 'BOOK' && item.slotRole === 'FEATURED')?.contentId,
    secondaryBookIds: schedule.items
      .filter((item) => item.contentType === 'BOOK' && item.slotRole === 'SECONDARY')
      .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
      .map((item) => item.contentId),
  }
}

function applyThemePreset(themeKey: string) {
  const preset = presetMap[themeKey]
  draft.themeKey = themeKey
  draft.themeTitle = preset?.title || draft.themeTitle
  draft.themeSubtitle = preset?.subtitle || draft.themeSubtitle
}

async function loadPage(date = appliedFilterDate.value) {
  loading.value = true
  errorState.value = null
  try {
    const [scheduleRows, quoteRows, articleRows, bookRows] = await Promise.all([
      getSchedules(date || undefined),
      getQuotes(),
      getArticles(),
      getBooks(),
    ])
    schedules.value = scheduleRows
    quotes.value = quoteRows
    articles.value = articleRows
    books.value = bookRows
    appliedFilterDate.value = date || ''
    filterDateInput.value = date || ''
  } catch (error) {
    errorState.value = parseError(error, '每日排期加载失败')
  } finally {
    loading.value = false
  }
}

function clearPendingPrompt() {
  leaveDialogVisible.value = false
  pendingAction.value = null
}

function buildSavePayload() {
  const normalized = normalizedDraft.value
  const items: Array<{
    contentType: 'ARTICLE' | 'BOOK'
    contentId: number
    slotRole: 'FEATURED' | 'SECONDARY'
    sortOrder: number
  }> = [
    normalized.featuredArticleId
      ? { contentType: 'ARTICLE' as const, contentId: normalized.featuredArticleId, slotRole: 'FEATURED' as const, sortOrder: 10 }
      : null,
    ...normalized.secondaryArticleIds.map((itemId, index) => ({
      contentType: 'ARTICLE' as const,
      contentId: itemId,
      slotRole: 'SECONDARY' as const,
      sortOrder: 20 + index,
    })),
    normalized.featuredBookId
      ? { contentType: 'BOOK' as const, contentId: normalized.featuredBookId, slotRole: 'FEATURED' as const, sortOrder: 10 }
      : null,
    ...normalized.secondaryBookIds.map((itemId, index) => ({
      contentType: 'BOOK' as const,
      contentId: itemId,
      slotRole: 'SECONDARY' as const,
      sortOrder: 20 + index,
    })),
  ].filter((item): item is {
    contentType: 'ARTICLE' | 'BOOK'
    contentId: number
    slotRole: 'FEATURED' | 'SECONDARY'
    sortOrder: number
  } => Boolean(item))

  return {
    scheduleDate: normalized.scheduleDate,
    themeKey: normalized.themeKey,
    themeTitle: normalized.themeTitle,
    themeSubtitle: normalized.themeSubtitle ?? undefined,
    quoteId: normalized.quoteId ?? 0,
    status: normalized.status,
    items,
  }
}

function validateDraft() {
  if (!normalizedDraft.value.scheduleDate) {
    ElMessage.warning('请先选择排期日期')
    return false
  }
  if (!normalizedDraft.value.themeTitle) {
    ElMessage.warning('主题标题不能为空')
    return false
  }
  if (!normalizedDraft.value.quoteId) {
    ElMessage.warning('请先放入一条语录')
    return false
  }
  if (!normalizedDraft.value.featuredArticleId) {
    ElMessage.warning('需要且仅需要一篇主推文章')
    return false
  }
  if (!normalizedDraft.value.featuredBookId) {
    ElMessage.warning('需要且仅需要一本主推书籍')
    return false
  }
  if (draftArticleCount.value === 0 || draftBookCount.value === 0) {
    ElMessage.warning('排期至少需要一篇文章和一本书')
    return false
  }
  return true
}

async function requestTransition(action: PendingAction) {
  if (!isDirty.value) {
    await action.run()
    return
  }
  pendingAction.value = action
  leaveDialogVisible.value = true
}

async function discardAndContinue() {
  const action = pendingAction.value
  clearPendingPrompt()
  await action?.run()
}

function keepEditing() {
  pendingAction.value?.revert?.()
  clearPendingPrompt()
}

async function saveDraft() {
  if (!validateDraft()) return false

  saving.value = true
  try {
    const payload = buildSavePayload()
    if (activeScheduleId.value) await updateSchedule(activeScheduleId.value, payload)
    else await createSchedule(payload)

    await loadPage(payload.scheduleDate)
    const matched =
      schedules.value.find((item) => item.id === activeScheduleId.value) ||
      schedules.value.find((item) => item.scheduleDate === payload.scheduleDate)

    if (matched) {
      activeScheduleId.value = matched.id
      applyDraft(scheduleToDraft(matched))
    } else {
      activeScheduleId.value = null
      applyDraft({ ...draft, scheduleDate: payload.scheduleDate })
    }

    refreshActiveScheduleSnapshot()
    ElMessage.success('排期已保存')
    return true
  } catch (error) {
    const parsed = parseError(error, '排期保存失败')
    ElMessage.error(parsed.detail)
    return false
  } finally {
    saving.value = false
  }
}

async function saveAndContinue() {
  const action = pendingAction.value
  const saved = await saveDraft()
  if (!saved) {
    leaveDialogVisible.value = false
    pendingAction.value = null
    return
  }
  clearPendingPrompt()
  await action?.run()
}

async function startNewDraft(scheduleDate = appliedFilterDate.value || getShanghaiToday()) {
  activeScheduleId.value = null
  applyDraft(createEmptyDraft(scheduleDate))
  refreshActiveScheduleSnapshot()
}

async function attemptNewDraft() {
  await requestTransition({
    label: '切换到空白工作台',
    run: async () => {
      await startNewDraft(filterDateInput.value || appliedFilterDate.value || getShanghaiToday())
    },
  })
}

async function openScheduleInWorkbench(schedule: CmsSchedule) {
  activeScheduleId.value = schedule.id
  applyDraft(scheduleToDraft(schedule))
  refreshActiveScheduleSnapshot()
}

async function attemptOpenSchedule(schedule: CmsSchedule) {
  await requestTransition({
    label: `载入 ${schedule.scheduleDate} 的排期`,
    run: async () => {
      await openScheduleInWorkbench(schedule)
    },
  })
}

async function attemptFilterDateChange(nextDate: string) {
  await requestTransition({
    label: nextDate ? `按 ${nextDate} 筛选排期` : '清除日期筛选',
    revert: () => {
      filterDateInput.value = appliedFilterDate.value
    },
    run: async () => {
      await loadPage(nextDate)
      await startNewDraft(nextDate || getShanghaiToday())
    },
  })
}

async function attemptRefreshData() {
  await requestTransition({
    label: '刷新排期与素材',
    run: async () => {
      const currentId = activeScheduleId.value
      const currentDate = draft.scheduleDate || appliedFilterDate.value || getShanghaiToday()
      await loadPage(appliedFilterDate.value)
      const matched = currentId
        ? schedules.value.find((item) => item.id === currentId) || schedules.value.find((item) => item.scheduleDate === currentDate)
        : null
      if (matched) {
        activeScheduleId.value = matched.id
        applyDraft(scheduleToDraft(matched))
      } else {
        activeScheduleId.value = null
        applyDraft(createEmptyDraft(currentDate))
      }
      refreshActiveScheduleSnapshot()
    },
  })
}

async function removeScheduleRow(row: CmsSchedule) {
  try {
    await ElMessageBox.confirm(`删除后 ${row.scheduleDate} 的排期将无法恢复，是否继续？`, '删除排期', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteSchedule(row.id)
    if (row.id === activeScheduleId.value) {
      activeScheduleId.value = null
      applyDraft(createEmptyDraft(appliedFilterDate.value || row.scheduleDate || getShanghaiToday()))
      refreshActiveScheduleSnapshot()
    }
    await loadPage(appliedFilterDate.value)
    ElMessage.success('排期已删除')
  } catch (error) {
    if (error === 'cancel') return
    const parsed = parseError(error, '删除排期失败')
    ElMessage.error(parsed.detail)
  }
}

function assignQuote(id: number) {
  draft.quoteId = id
}

function clearQuote() {
  draft.quoteId = undefined
}

function setFeaturedArticle(id: number) {
  if (draft.featuredArticleId === id) return
  const previous = draft.featuredArticleId
  draft.featuredArticleId = id
  const nextSecondary = draft.secondaryArticleIds.filter((item) => item !== id)
  if (previous && previous !== id) nextSecondary.unshift(previous)
  draft.secondaryArticleIds = uniqueIds(nextSecondary)
}

function setFeaturedBook(id: number) {
  if (draft.featuredBookId === id) return
  const previous = draft.featuredBookId
  draft.featuredBookId = id
  const nextSecondary = draft.secondaryBookIds.filter((item) => item !== id)
  if (previous && previous !== id) nextSecondary.unshift(previous)
  draft.secondaryBookIds = uniqueIds(nextSecondary)
}

function insertIntoSecondary(zone: 'ARTICLE' | 'BOOK', id: number, insertIndex = Number.MAX_SAFE_INTEGER) {
  if (zone === 'ARTICLE') {
    const nextList = draft.secondaryArticleIds.filter((item) => item !== id)
    if (draft.featuredArticleId === id) draft.featuredArticleId = undefined
    nextList.splice(Math.max(0, Math.min(insertIndex, nextList.length)), 0, id)
    draft.secondaryArticleIds = uniqueIds(nextList)
    return
  }
  const nextList = draft.secondaryBookIds.filter((item) => item !== id)
  if (draft.featuredBookId === id) draft.featuredBookId = undefined
  nextList.splice(Math.max(0, Math.min(insertIndex, nextList.length)), 0, id)
  draft.secondaryBookIds = uniqueIds(nextList)
}

function moveSecondary(zone: 'ARTICLE' | 'BOOK', fromIndex: number, insertIndex: number) {
  const sourceList = zone === 'ARTICLE' ? [...draft.secondaryArticleIds] : [...draft.secondaryBookIds]
  if (fromIndex < 0 || fromIndex >= sourceList.length) return
  const [moved] = sourceList.splice(fromIndex, 1)
  if (moved == null) return
  const nextIndex = Math.max(0, Math.min(insertIndex > fromIndex ? insertIndex - 1 : insertIndex, sourceList.length))
  sourceList.splice(nextIndex, 0, moved)
  if (zone === 'ARTICLE') draft.secondaryArticleIds = sourceList
  else draft.secondaryBookIds = sourceList
}

function removeSecondary(zone: 'ARTICLE' | 'BOOK', id: number) {
  if (zone === 'ARTICLE') {
    draft.secondaryArticleIds = draft.secondaryArticleIds.filter((item) => item !== id)
    return
  }
  draft.secondaryBookIds = draft.secondaryBookIds.filter((item) => item !== id)
}

function clearFeaturedArticle() {
  draft.featuredArticleId = undefined
}

function clearFeaturedBook() {
  draft.featuredBookId = undefined
}

function isAssigned(kind: ContentPoolKind, id: number) {
  if (kind === 'QUOTE') return draft.quoteId === id
  if (kind === 'ARTICLE') return draft.featuredArticleId === id || draft.secondaryArticleIds.includes(id)
  return draft.featuredBookId === id || draft.secondaryBookIds.includes(id)
}

function isFeatured(kind: 'ARTICLE' | 'BOOK', id: number) {
  return kind === 'ARTICLE' ? draft.featuredArticleId === id : draft.featuredBookId === id
}

function countScheduleItems(row: CmsSchedule, type: 'ARTICLE' | 'BOOK') {
  return row.items.filter((item) => item.contentType === type).length
}

function scheduleRowClassName(payload: { row: CmsSchedule }) {
  return payload.row.id === activeScheduleId.value ? 'schedule-row--active' : ''
}

function getInsertIndexFromEvent(event: DragEvent, fallbackIndex: number) {
  const target = event.currentTarget as HTMLElement | null
  if (!target) return fallbackIndex
  const rect = target.getBoundingClientRect()
  return event.clientY > rect.top + rect.height / 2 ? fallbackIndex + 1 : fallbackIndex
}

function evaluateDrop(payload: DragPayload, zone: DropZone, insertIndex = -1): DropEvaluation {
  if (zone === 'quote') {
    if (payload.kind !== 'QUOTE') return { allowed: false, insertIndex: -1, message: '这里只能放语录' }
    if (draft.quoteId === payload.itemId) return { allowed: false, insertIndex: -1, message: '这条语录已经在当前槽位' }
    return { allowed: true, insertIndex: -1, message: draft.quoteId ? '放下后会替换当前语录' : '放下后设为今日语录' }
  }

  if (zone === 'featuredArticle') {
    if (payload.kind !== 'ARTICLE') return { allowed: false, insertIndex: -1, message: '这里只能放文章' }
    if (draft.featuredArticleId === payload.itemId && payload.source !== 'secondaryArticle') {
      return { allowed: false, insertIndex: -1, message: '这篇文章已经是主推' }
    }
    return {
      allowed: true,
      insertIndex: -1,
      message: draft.featuredArticleId && draft.featuredArticleId !== payload.itemId ? '当前主推将降级到补充区第一位' : '放下后设为主推文章',
    }
  }

  if (zone === 'featuredBook') {
    if (payload.kind !== 'BOOK') return { allowed: false, insertIndex: -1, message: '这里只能放书籍' }
    if (draft.featuredBookId === payload.itemId && payload.source !== 'secondaryBook') {
      return { allowed: false, insertIndex: -1, message: '这本书已经是主推' }
    }
    return {
      allowed: true,
      insertIndex: -1,
      message: draft.featuredBookId && draft.featuredBookId !== payload.itemId ? '当前主推将降级到补充区第一位' : '放下后设为主推书籍',
    }
  }

  if (zone === 'secondaryArticle') {
    if (payload.kind !== 'ARTICLE') return { allowed: false, insertIndex: -1, message: '这里只能放文章' }
    if (payload.source === 'pool' && isAssigned('ARTICLE', payload.itemId)) {
      return { allowed: false, insertIndex, message: '这篇文章已经在当前编排中' }
    }
    return {
      allowed: true,
      insertIndex,
      message: payload.source === 'secondaryArticle' ? '拖动后将调整补充文章顺序' : payload.source === 'featuredArticle' ? '放下后主推文章将移入补充区' : '放下后加入补充文章',
    }
  }

  if (payload.kind !== 'BOOK') return { allowed: false, insertIndex: -1, message: '这里只能放书籍' }
  if (payload.source === 'pool' && isAssigned('BOOK', payload.itemId)) {
    return { allowed: false, insertIndex, message: '这本书已经在当前编排中' }
  }
  return {
    allowed: true,
    insertIndex,
    message: payload.source === 'secondaryBook' ? '拖动后将调整补充书籍顺序' : payload.source === 'featuredBook' ? '放下后主推书籍将移入补充区' : '放下后加入补充书籍',
  }
}

function applyDrop(zone: DropZone, payload: DragPayload, insertIndex = -1) {
  if (zone === 'quote') return assignQuote(payload.itemId)
  if (zone === 'featuredArticle') return setFeaturedArticle(payload.itemId)
  if (zone === 'featuredBook') return setFeaturedBook(payload.itemId)
  if (zone === 'secondaryArticle') {
    if (payload.source === 'secondaryArticle' && payload.index != null) return moveSecondary('ARTICLE', payload.index, insertIndex)
    return insertIntoSecondary('ARTICLE', payload.itemId, insertIndex)
  }
  if (payload.source === 'secondaryBook' && payload.index != null) return moveSecondary('BOOK', payload.index, insertIndex)
  return insertIntoSecondary('BOOK', payload.itemId, insertIndex)
}

function startDrag(kind: ContentPoolKind, itemId: number, source: DragPayload['source'], index?: number) {
  dragPayload.value = { kind, itemId, source, index }
}

function clearDragState() {
  dragPayload.value = null
  dropState.zone = ''
  dropState.allowed = false
  dropState.insertIndex = -1
  dropState.message = ''
}

function handleDragOver(event: DragEvent, zone: DropZone, insertIndex = -1) {
  if (!dragPayload.value) return
  const evaluation = evaluateDrop(dragPayload.value, zone, insertIndex)
  dropState.zone = zone
  dropState.allowed = evaluation.allowed
  dropState.insertIndex = evaluation.insertIndex
  dropState.message = evaluation.message
  if (event.dataTransfer) event.dataTransfer.dropEffect = evaluation.allowed ? 'move' : 'none'
}

function handleDrop(event: DragEvent, zone: DropZone, insertIndex = -1) {
  event.preventDefault()
  if (!dragPayload.value) return
  const evaluation = evaluateDrop(dragPayload.value, zone, insertIndex)
  if (evaluation.allowed) applyDrop(zone, dragPayload.value, evaluation.insertIndex)
  clearDragState()
}

function showInsertLine(zone: DropZone, index: number) {
  return dropState.zone === zone && dropState.allowed && dropState.insertIndex === index
}

function zoneClasses(zone: DropZone) {
  return {
    'is-drop-target': dropState.zone === zone && dropState.allowed,
    'is-drop-invalid': dropState.zone === zone && !dropState.allowed,
    'is-drop-replacing': dropState.zone === zone && dropState.allowed && Boolean(dropState.message),
  }
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!isDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(async () => {
  await loadPage()
  await startNewDraft()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <div class="schedule-page">
    <LoadingState v-if="loading && !quotes.length && !articles.length && !books.length && !scheduleRows.length" />
    <ErrorState
      v-else-if="errorState && !quotes.length && !articles.length && !books.length"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="attemptRefreshData"
    />
    <template v-else>
      <section class="schedule-toolbar content-stage">
        <div class="schedule-toolbar__summary">
          <p class="schedule-toolbar__eyebrow">每日排期工作台</p>
          <h1>{{ draftModeLabel }}</h1>
          <p>用一个页面完成选内容、拖拽编排、缩略预览和保存。当前主题：<strong>{{ selectedThemeLabel }}</strong></p>
        </div>
        <div class="schedule-toolbar__actions">
          <el-date-picker
            v-model="filterDateInput"
            type="date"
            value-format="YYYY-MM-DD"
            clearable
            placeholder="按日期筛选排期"
            @change="attemptFilterDateChange(String($event ?? ''))"
          />
          <el-button :loading="loading" @click="attemptRefreshData">刷新</el-button>
          <el-button @click="attemptNewDraft">新建排期</el-button>
          <el-button type="primary" :loading="saving" @click="saveDraft">保存当前排期</el-button>
        </div>
      </section>

      <el-alert
        v-if="errorState"
        class="schedule-alert"
        type="error"
        :closable="false"
        :title="errorState.title"
        :description="errorState.detail"
      />

      <section class="schedule-dirty content-stage" :class="{ active: isDirty }">
        <div>
          <strong>{{ isDirty ? '未保存变更' : '当前草稿已同步' }}</strong>
          <p>{{ isDirty ? '切换日期、载入其他排期或离开页面前，请先保存或放弃当前草稿。' : '草稿与最后一次加载或保存的排期一致。' }}</p>
        </div>
        <el-tag :type="isDirty ? 'warning' : 'success'">{{ isDirty ? 'Dirty' : 'Clean' }}</el-tag>
      </section>

      <section class="schedule-workbench">
        <aside class="workbench-panel content-stage pool-panel">
          <div class="panel-head">
            <div>
              <p class="panel-head__eyebrow">内容素材池</p>
              <h2>左侧选材</h2>
            </div>
            <span>{{ visibleQuotes.length + visibleArticles.length + visibleBooks.length }} 条素材</span>
          </div>

          <div class="pool-filters">
            <el-input v-model="poolSearch" placeholder="搜索标题、摘要、作者或来源" clearable />
            <el-select v-model="poolCategory">
              <el-option label="全部主题" value="all" />
              <el-option v-for="item in ARTICLE_CATEGORY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </div>

          <section class="pool-group">
            <div class="pool-group__head">
              <h3>语录</h3>
              <span>{{ visibleQuotes.length }}</span>
            </div>
            <EmptyState v-if="visibleQuotes.length === 0" title="没有匹配的语录" description="试试清空搜索条件，或者先去内容管理页补充素材。" action-text="清空搜索" @action="poolSearch = ''" />
            <div v-else class="pool-list">
              <article
                v-for="item in visibleQuotes"
                :key="`quote-${item.id}`"
                class="pool-card"
                :class="{ 'is-assigned': draft.quoteId === item.id }"
                draggable="true"
                @dragstart="startDrag('QUOTE', item.id, 'pool')"
                @dragend="clearDragState"
              >
                <div class="pool-card__body">
                  <strong>{{ item.content }}</strong>
                  <p>{{ item.author || '内容专栏编辑部' }}</p>
                </div>
                <div class="pool-card__actions">
                  <el-tag v-if="draft.quoteId === item.id" type="success">当前语录</el-tag>
                  <el-button size="small" @click="assignQuote(item.id)">{{ draft.quoteId === item.id ? '已放入' : '放入语录' }}</el-button>
                </div>
              </article>
            </div>
          </section>

          <section class="pool-group">
            <div class="pool-group__head">
              <h3>文章</h3>
              <span>{{ visibleArticles.length }}</span>
            </div>
            <EmptyState v-if="visibleArticles.length === 0" title="没有匹配的文章" description="可以切换主题筛选，或先在内容管理里补充文章。" action-text="清空筛选" @action="poolSearch = ''; poolCategory = 'all'" />
            <div v-else class="pool-list">
              <article
                v-for="item in visibleArticles"
                :key="`article-${item.id}`"
                class="pool-card"
                :class="{ 'is-assigned': isAssigned('ARTICLE', item.id) }"
                draggable="true"
                @dragstart="startDrag('ARTICLE', item.id, 'pool')"
                @dragend="clearDragState"
              >
                <div class="pool-card__body">
                  <div class="pool-card__meta">
                    <span>{{ ARTICLE_CATEGORY_LABELS[item.category || ''] || '文章' }}</span>
                    <span>{{ item.sourceName || '内容来源' }}</span>
                  </div>
                  <strong>{{ item.title }}</strong>
                  <p>{{ item.summary || '暂无摘要' }}</p>
                </div>
                <div class="pool-card__actions">
                  <el-tag v-if="isFeatured('ARTICLE', item.id)" type="warning">主推中</el-tag>
                  <el-tag v-else-if="draft.secondaryArticleIds.includes(item.id)" type="success">补充中</el-tag>
                  <el-button size="small" @click="setFeaturedArticle(item.id)">设为主推</el-button>
                  <el-button size="small" plain @click="insertIntoSecondary('ARTICLE', item.id, draft.secondaryArticleIds.length)">加入补充</el-button>
                </div>
              </article>
            </div>
          </section>

          <section class="pool-group">
            <div class="pool-group__head">
              <h3>书籍</h3>
              <span>{{ visibleBooks.length }}</span>
            </div>
            <EmptyState v-if="visibleBooks.length === 0" title="没有匹配的书籍" description="可以切换主题筛选，或先在内容管理里补充书籍。" action-text="清空筛选" @action="poolSearch = ''; poolCategory = 'all'" />
            <div v-else class="pool-list">
              <article
                v-for="item in visibleBooks"
                :key="`book-${item.id}`"
                class="pool-card"
                :class="{ 'is-assigned': isAssigned('BOOK', item.id) }"
                draggable="true"
                @dragstart="startDrag('BOOK', item.id, 'pool')"
                @dragend="clearDragState"
              >
                <div class="pool-card__body">
                  <div class="pool-card__meta">
                    <span>{{ ARTICLE_CATEGORY_LABELS[item.category || ''] || '书籍' }}</span>
                    <span>{{ item.author || '推荐阅读' }}</span>
                  </div>
                  <strong>{{ item.title }}</strong>
                  <p>{{ item.description || '暂无推荐文案' }}</p>
                </div>
                <div class="pool-card__actions">
                  <el-tag v-if="isFeatured('BOOK', item.id)" type="warning">主推中</el-tag>
                  <el-tag v-else-if="draft.secondaryBookIds.includes(item.id)" type="success">补充中</el-tag>
                  <el-button size="small" @click="setFeaturedBook(item.id)">设为主推</el-button>
                  <el-button size="small" plain @click="insertIntoSecondary('BOOK', item.id, draft.secondaryBookIds.length)">加入补充</el-button>
                </div>
              </article>
            </div>
          </section>
        </aside>

        <section class="workbench-panel content-stage canvas-panel">
          <div class="panel-head">
            <div>
              <p class="panel-head__eyebrow">编排画布</p>
              <h2>中间排版</h2>
            </div>
            <span>{{ formatDateLabel(draft.scheduleDate) }}</span>
          </div>

          <div class="canvas-meta">
            <el-form label-position="top" class="canvas-meta__form">
              <div class="canvas-meta__grid">
                <el-form-item label="排期日期" required>
                  <el-date-picker v-model="draft.scheduleDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
                </el-form-item>
                <el-form-item label="状态">
                  <el-select v-model="draft.status">
                    <el-option label="启用" value="ACTIVE" />
                    <el-option label="草稿" value="DRAFT" />
                  </el-select>
                </el-form-item>
                <el-form-item label="主题分类" required>
                  <el-select :model-value="draft.themeKey" @change="applyThemePreset">
                    <el-option v-for="item in ARTICLE_CATEGORY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="主题标题" required>
                  <el-input v-model="draft.themeTitle" maxlength="80" show-word-limit />
                </el-form-item>
              </div>
              <el-form-item label="主题副标题">
                <el-input v-model="draft.themeSubtitle" type="textarea" :rows="2" maxlength="180" show-word-limit />
              </el-form-item>
            </el-form>
          </div>

          <section class="composer-group">
            <div class="composer-group__head">
              <h3>今日语录槽</h3>
              <span>{{ activeQuote ? '1 / 1' : '0 / 1' }}</span>
            </div>
            <div class="drop-slot" :class="zoneClasses('quote')" @dragover.prevent="handleDragOver($event, 'quote')" @drop.prevent="handleDrop($event, 'quote')">
              <div v-if="activeQuote" class="slot-entity" draggable="true" @dragstart="startDrag('QUOTE', activeQuote.id, 'quote')" @dragend="clearDragState">
                <div class="slot-entity__content">
                  <strong>{{ activeQuote.content }}</strong>
                  <p>{{ activeQuote.author || '内容专栏编辑部' }}</p>
                </div>
                <div class="slot-entity__actions">
                  <el-button size="small" plain @click="clearQuote">移除</el-button>
                </div>
              </div>
              <div v-else class="slot-placeholder">
                <strong>将语录拖到这里</strong>
                <p>空槽会显示虚线占位，也可以在左侧点击“放入语录”。</p>
              </div>
              <small v-if="dropState.zone === 'quote'" class="drop-message">{{ dropState.message }}</small>
            </div>
          </section>

          <section class="composer-group">
            <div class="composer-group__head">
              <h3>主推文章</h3>
              <span>{{ draftArticleCount }} 篇文章</span>
            </div>
            <div class="drop-slot" :class="zoneClasses('featuredArticle')" @dragover.prevent="handleDragOver($event, 'featuredArticle')" @drop.prevent="handleDrop($event, 'featuredArticle')">
              <div v-if="featuredArticle" class="slot-entity slot-entity--visual" draggable="true" @dragstart="startDrag('ARTICLE', featuredArticle.id, 'featuredArticle')" @dragend="clearDragState">
                <div class="slot-entity__content">
                  <div class="slot-entity__meta">
                    <span>主推文章</span>
                    <span>{{ ARTICLE_CATEGORY_LABELS[featuredArticle.category || ''] || '文章' }}</span>
                  </div>
                  <strong>{{ featuredArticle.title }}</strong>
                  <p>{{ featuredArticle.summary || featuredArticle.sourceName || '拖入新文章时，当前主推会自动降级到补充区第一位。' }}</p>
                </div>
                <div class="slot-entity__actions">
                  <el-button size="small" plain @click="insertIntoSecondary('ARTICLE', featuredArticle.id, 0)">降到补充</el-button>
                  <el-button size="small" plain @click="clearFeaturedArticle">移除</el-button>
                </div>
              </div>
              <div v-else class="slot-placeholder">
                <strong>将文章拖到这里设为主推</strong>
                <p>只能有一篇主推文章，替换时旧主推会自动降级到补充区第一位。</p>
              </div>
              <small v-if="dropState.zone === 'featuredArticle'" class="drop-message">{{ dropState.message }}</small>
            </div>

            <div class="sortable-zone" :class="zoneClasses('secondaryArticle')" @dragover.prevent="handleDragOver($event, 'secondaryArticle', draft.secondaryArticleIds.length)" @drop.prevent="handleDrop($event, 'secondaryArticle', draft.secondaryArticleIds.length)">
              <div class="sortable-zone__head">
                <h4>补充文章</h4>
                <p>支持拖拽排序，排序过程会显示插入指示线。</p>
              </div>
              <div v-if="showInsertLine('secondaryArticle', 0)" class="insert-line" />
              <template v-if="secondaryArticles.length">
                <div
                  v-for="(item, index) in secondaryArticles"
                  :key="`secondary-article-${item.id}`"
                  class="sortable-card"
                  draggable="true"
                  @dragstart="startDrag('ARTICLE', item.id, 'secondaryArticle', index)"
                  @dragend="clearDragState"
                  @dragover.prevent="handleDragOver($event, 'secondaryArticle', getInsertIndexFromEvent($event, index))"
                  @drop.prevent="handleDrop($event, 'secondaryArticle', getInsertIndexFromEvent($event, index))"
                >
                  <div class="sortable-card__body">
                    <div class="slot-entity__meta">
                      <span>补充文章 {{ index + 1 }}</span>
                      <span>{{ ARTICLE_CATEGORY_LABELS[item.category || ''] || '文章' }}</span>
                    </div>
                    <strong>{{ item.title }}</strong>
                    <p>{{ item.summary || item.sourceName || '暂无摘要' }}</p>
                  </div>
                  <div class="sortable-card__actions">
                    <el-button size="small" @click="setFeaturedArticle(item.id)">设为主推</el-button>
                    <el-button size="small" plain @click="removeSecondary('ARTICLE', item.id)">移除</el-button>
                  </div>
                </div>
                <div v-for="index in secondaryArticles.length" :key="`article-insert-${index}`" v-show="showInsertLine('secondaryArticle', index)" class="insert-line" />
              </template>
              <div v-else class="sortable-empty">
                <strong>补充文章区为空</strong>
                <p>可以从左侧点击“加入补充”，也可以把文章拖到这里。</p>
              </div>
              <div v-if="showInsertLine('secondaryArticle', secondaryArticles.length)" class="insert-line" />
              <small v-if="dropState.zone === 'secondaryArticle'" class="drop-message">{{ dropState.message }}</small>
            </div>
          </section>

          <section class="composer-group">
            <div class="composer-group__head">
              <h3>主推书籍</h3>
              <span>{{ draftBookCount }} 本书</span>
            </div>
            <div class="drop-slot" :class="zoneClasses('featuredBook')" @dragover.prevent="handleDragOver($event, 'featuredBook')" @drop.prevent="handleDrop($event, 'featuredBook')">
              <div v-if="featuredBook" class="slot-entity slot-entity--visual" draggable="true" @dragstart="startDrag('BOOK', featuredBook.id, 'featuredBook')" @dragend="clearDragState">
                <div class="slot-entity__content">
                  <div class="slot-entity__meta">
                    <span>主推书籍</span>
                    <span>{{ ARTICLE_CATEGORY_LABELS[featuredBook.category || ''] || '书籍' }}</span>
                  </div>
                  <strong>{{ featuredBook.title }}</strong>
                  <p>{{ featuredBook.description || featuredBook.author || '拖入新书籍时，当前主推会自动降级到补充区第一位。' }}</p>
                </div>
                <div class="slot-entity__actions">
                  <el-button size="small" plain @click="insertIntoSecondary('BOOK', featuredBook.id, 0)">降到补充</el-button>
                  <el-button size="small" plain @click="clearFeaturedBook">移除</el-button>
                </div>
              </div>
              <div v-else class="slot-placeholder">
                <strong>将书籍拖到这里设为主推</strong>
                <p>只能有一本主推书籍，替换时旧主推会自动降级到补充区第一位。</p>
              </div>
              <small v-if="dropState.zone === 'featuredBook'" class="drop-message">{{ dropState.message }}</small>
            </div>

            <div class="sortable-zone" :class="zoneClasses('secondaryBook')" @dragover.prevent="handleDragOver($event, 'secondaryBook', draft.secondaryBookIds.length)" @drop.prevent="handleDrop($event, 'secondaryBook', draft.secondaryBookIds.length)">
              <div class="sortable-zone__head">
                <h4>补充书籍</h4>
                <p>支持拖拽排序，排序过程会显示插入指示线。</p>
              </div>
              <div v-if="showInsertLine('secondaryBook', 0)" class="insert-line" />
              <template v-if="secondaryBooks.length">
                <div
                  v-for="(item, index) in secondaryBooks"
                  :key="`secondary-book-${item.id}`"
                  class="sortable-card"
                  draggable="true"
                  @dragstart="startDrag('BOOK', item.id, 'secondaryBook', index)"
                  @dragend="clearDragState"
                  @dragover.prevent="handleDragOver($event, 'secondaryBook', getInsertIndexFromEvent($event, index))"
                  @drop.prevent="handleDrop($event, 'secondaryBook', getInsertIndexFromEvent($event, index))"
                >
                  <div class="sortable-card__body">
                    <div class="slot-entity__meta">
                      <span>补充书籍 {{ index + 1 }}</span>
                      <span>{{ ARTICLE_CATEGORY_LABELS[item.category || ''] || '书籍' }}</span>
                    </div>
                    <strong>{{ item.title }}</strong>
                    <p>{{ item.description || item.author || '暂无简介' }}</p>
                  </div>
                  <div class="sortable-card__actions">
                    <el-button size="small" @click="setFeaturedBook(item.id)">设为主推</el-button>
                    <el-button size="small" plain @click="removeSecondary('BOOK', item.id)">移除</el-button>
                  </div>
                </div>
                <div v-for="index in secondaryBooks.length" :key="`book-insert-${index}`" v-show="showInsertLine('secondaryBook', index)" class="insert-line" />
              </template>
              <div v-else class="sortable-empty">
                <strong>补充书籍区为空</strong>
                <p>可以从左侧点击“加入补充”，也可以把书籍拖到这里。</p>
              </div>
              <div v-if="showInsertLine('secondaryBook', secondaryBooks.length)" class="insert-line" />
              <small v-if="dropState.zone === 'secondaryBook'" class="drop-message">{{ dropState.message }}</small>
            </div>
          </section>
        </section>

        <aside class="workbench-panel content-stage preview-panel">
          <div class="panel-head">
            <div>
              <p class="panel-head__eyebrow">高保真缩略预览</p>
              <h2>右侧预览</h2>
            </div>
            <span>固定桌面宽度</span>
          </div>
          <div class="preview-note">
            <p>这里还原层级、排序和主推关系，不追求逐像素一致，也不会复刻全部 hover 或动画。</p>
          </div>
          <div class="preview-frame">
            <div class="preview-shell">
              <QuoteHero compact :theme="previewTheme" :quote="activeQuote" :date-label="formatDateLabel(draft.scheduleDate)" />
              <section class="preview-block">
                <div class="preview-block__head">
                  <h3>状态标签</h3>
                  <p>这里只展示缩略版层级，不参与编辑。</p>
                </div>
                <ContentStateTabs :model-value="draft.themeKey" @change="() => undefined" />
              </section>
              <section class="preview-block">
                <div class="preview-block__head">
                  <h3>今日精选</h3>
                  <p>文章主舞台 + 书籍辅舞台。</p>
                </div>
                <div class="preview-stage">
                  <ArticleFeatureCard v-if="previewFeaturedArticle" :article="previewFeaturedArticle" :show-action="false" />
                  <BookShelfCard v-if="previewFeaturedBook" :book="previewFeaturedBook" dense :show-action="false" />
                </div>
              </section>
              <section v-if="previewSecondaryArticles.length || previewSecondaryBooks.length" class="preview-block">
                <div class="preview-block__head">
                  <h3>继续阅读</h3>
                  <p>补充内容按照当前草稿顺序展示。</p>
                </div>
                <div class="preview-list">
                  <ArticleFeatureCard v-for="item in previewSecondaryArticles" :key="`preview-secondary-article-${item.id}`" :article="item" dense :show-action="false" />
                  <BookShelfCard v-for="item in previewSecondaryBooks" :key="`preview-secondary-book-${item.id}`" :book="item" dense :show-action="false" />
                </div>
              </section>
              <section class="preview-block">
                <div class="preview-block__head">
                  <h3>往期归档</h3>
                  <p>预览里只还原结构和已选日期。</p>
                </div>
                <ArchiveCalendar :dates="previewArchiveDates" :selected-date="draft.scheduleDate" :today-date="draft.scheduleDate" @select="() => undefined" />
              </section>
            </div>
          </div>
        </aside>
      </section>

      <section class="schedule-table content-stage">
        <div class="panel-head">
          <div>
            <p class="panel-head__eyebrow">历史排期列表</p>
            <h2>下方管理</h2>
          </div>
          <span>{{ scheduleRows.length }} 条排期</span>
        </div>
        <EmptyState v-if="scheduleRows.length === 0" title="当前筛选下没有排期" description="可以直接在上面的工作台新建一条，也可以清除日期筛选查看全部。" action-text="新建空白排期" @action="attemptNewDraft" />
        <el-table v-else :data="scheduleRows" border :row-class-name="scheduleRowClassName">
          <el-table-column prop="scheduleDate" label="日期" width="140" />
          <el-table-column prop="themeTitle" label="主题标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="themeKey" label="主题分类" width="120">
            <template #default="scope">{{ ARTICLE_CATEGORY_LABELS[scope.row.themeKey] || scope.row.themeKey }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '草稿' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="语录" min-width="200" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.quoteContent || '-' }}</template>
          </el-table-column>
          <el-table-column label="文章 / 书籍" width="130">
            <template #default="scope">
              {{ `${countScheduleItems(scope.row, 'ARTICLE')} / ${countScheduleItems(scope.row, 'BOOK')}` }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="attemptOpenSchedule(scope.row)">载入工作台</el-button>
              <el-button link @click="attemptFilterDateChange(scope.row.scheduleDate)">筛选此日</el-button>
              <el-button link type="danger" @click="removeScheduleRow(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>

    <el-dialog v-model="leaveDialogVisible" title="当前草稿还有未保存变更" width="460px" :close-on-click-modal="false" :close-on-press-escape="false">
      <p class="leave-dialog__lead">你正准备{{ pendingAction?.label || '切换页面操作' }}。如果现在离开，工作台里的修改会丢失。</p>
      <p class="leave-dialog__sub">你可以放弃变更、继续编辑，或者先保存；只有保存成功后，原操作才会继续执行。</p>
      <template #footer>
        <el-button @click="discardAndContinue">放弃变更</el-button>
        <el-button @click="keepEditing">继续编辑</el-button>
        <el-button type="primary" :loading="saving" @click="saveAndContinue">先保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.schedule-page {
  display: flex;
  flex-direction: column;
  gap: var(--content-gap-3);
}

.content-stage {
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-1);
  box-shadow: var(--content-shadow-1);
}

.schedule-toolbar,
.schedule-dirty,
.workbench-panel,
.schedule-table {
  padding: 22px;
}

.schedule-toolbar,
.schedule-dirty,
.panel-head,
.schedule-toolbar__actions,
.pool-filters,
.pool-card__meta,
.pool-card__actions,
.slot-entity__meta,
.slot-entity__actions,
.sortable-card__actions,
.preview-block__head {
  display: flex;
  gap: var(--content-gap-2);
  flex-wrap: wrap;
}

.schedule-toolbar,
.schedule-dirty,
.panel-head,
.schedule-toolbar__actions,
.preview-block__head {
  justify-content: space-between;
  align-items: flex-start;
}

.schedule-toolbar__summary,
.schedule-toolbar__summary p,
.schedule-toolbar__summary h1,
.panel-head h2,
.panel-head span,
.panel-head__eyebrow,
.preview-note p,
.drop-message,
.leave-dialog__lead,
.leave-dialog__sub {
  margin: 0;
}

.schedule-toolbar__eyebrow,
.panel-head__eyebrow {
  color: #8fc3c8;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.schedule-toolbar__summary h1,
.panel-head h2 {
  color: #f4fbff;
}

.schedule-toolbar__summary h1 {
  margin-top: 6px;
  font-size: 28px;
}

.schedule-toolbar__summary p {
  margin-top: 10px;
  color: #a9c0e3;
  line-height: 1.7;
}

.schedule-toolbar__summary strong {
  color: #f6fbff;
}

.schedule-toolbar__actions {
  justify-content: flex-end;
}

.schedule-alert {
  margin-top: -4px;
}

.schedule-dirty {
  align-items: center;
}

.schedule-dirty p {
  margin: 6px 0 0;
  color: #a9c0e3;
  line-height: 1.6;
}

.schedule-dirty.active {
  border-color: rgba(194, 164, 108, 0.42);
  background:
    linear-gradient(180deg, rgba(194, 164, 108, 0.14), rgba(11, 18, 31, 0.9)),
    var(--content-surface-1);
}

.schedule-workbench {
  display: grid;
  grid-template-columns: minmax(300px, 0.95fr) minmax(460px, 1.25fr) minmax(360px, 0.9fr);
  gap: var(--content-gap-3);
  align-items: start;
}

.workbench-panel {
  display: grid;
  gap: var(--content-gap-3);
  min-width: 0;
}

.panel-head h2 {
  font-size: 22px;
}

.panel-head span,
.preview-note p,
.composer-group__head span,
.sortable-zone__head p,
.drop-message,
.slot-placeholder p,
.slot-entity__content p,
.sortable-card__body p,
.pool-card__body p,
.preview-block__head p {
  color: #a9c0e3;
  line-height: 1.6;
}

.pool-panel,
.canvas-panel {
  max-height: calc(100vh - 180px);
  overflow: auto;
}

.pool-group,
.composer-group,
.preview-block {
  display: grid;
  gap: var(--content-gap-2);
}

.pool-group__head,
.composer-group__head,
.sortable-zone__head {
  display: flex;
  justify-content: space-between;
  gap: var(--content-gap-2);
  align-items: baseline;
}

.pool-group__head h3,
.composer-group__head h3,
.sortable-zone__head h4,
.preview-block__head h3,
.pool-card__body strong,
.slot-entity__content strong,
.sortable-card__body strong {
  margin: 0;
  color: #f5fbff;
}

.pool-group__head span,
.composer-group__head span,
.sortable-zone__head p,
.preview-block__head p {
  margin: 0;
}

.pool-list {
  display: grid;
  gap: var(--content-gap-2);
}

.pool-card,
.sortable-card,
.slot-entity,
.slot-placeholder {
  border-radius: var(--content-radius-1);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-2);
  box-shadow: var(--content-shadow-1);
}

.pool-card {
  display: grid;
  gap: var(--content-gap-2);
  padding: 14px;
  cursor: grab;
}

.pool-card.is-assigned {
  border-color: var(--content-border-3);
}

.pool-card__meta {
  align-items: center;
}

.pool-card__meta span,
.slot-entity__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: var(--content-radius-pill);
  color: #dceafb;
  background: var(--content-chip-muted-surface);
  font-size: 12px;
}

.pool-card__body,
.slot-entity__content,
.sortable-card__body {
  display: grid;
  gap: 8px;
}

.pool-card__body p,
.sortable-card__body p,
.slot-entity__content p {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.canvas-meta {
  padding: 18px;
  border-radius: var(--content-radius-1);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-2);
}

.canvas-meta__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--content-gap-2);
}

.drop-slot,
.sortable-zone {
  display: grid;
  gap: var(--content-gap-2);
  padding: 16px;
  border-radius: var(--content-radius-1);
  border: 1px dashed rgba(168, 186, 214, 0.28);
  background: rgba(10, 16, 28, 0.58);
  min-height: 132px;
  transition:
    border-color var(--content-motion-fast) var(--content-ease-standard),
    background var(--content-motion-fast) var(--content-ease-standard),
    box-shadow var(--content-motion-fast) var(--content-ease-standard);
}

.drop-slot.is-drop-target,
.sortable-zone.is-drop-target {
  border-color: rgba(117, 193, 170, 0.76);
  background: rgba(35, 77, 74, 0.28);
  box-shadow: 0 0 0 1px rgba(117, 193, 170, 0.22) inset;
}

.drop-slot.is-drop-invalid,
.sortable-zone.is-drop-invalid {
  border-color: rgba(214, 109, 109, 0.5);
  background: rgba(91, 25, 25, 0.2);
}

.drop-slot.is-drop-replacing,
.sortable-zone.is-drop-replacing {
  box-shadow: 0 0 0 1px rgba(194, 164, 108, 0.22) inset;
}

.slot-entity,
.sortable-card {
  padding: 14px;
}

.slot-entity {
  display: flex;
  justify-content: space-between;
  gap: var(--content-gap-2);
  align-items: flex-start;
  cursor: grab;
}

.slot-entity--visual {
  background: var(--content-surface-3);
}

.slot-placeholder {
  display: grid;
  align-content: center;
  gap: 8px;
  min-height: 98px;
  border-style: dashed;
  background: rgba(7, 13, 24, 0.45);
}

.slot-placeholder strong,
.sortable-empty strong {
  color: #f4fbff;
}

.sortable-empty {
  display: grid;
  gap: 8px;
  min-height: 72px;
  padding: 14px;
  border-radius: var(--content-radius-1);
  border: 1px dashed rgba(168, 186, 214, 0.24);
  background: rgba(7, 13, 24, 0.32);
}

.insert-line {
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(117, 193, 170, 0.2), rgba(117, 193, 170, 1), rgba(117, 193, 170, 0.2));
}

.preview-panel {
  overflow: hidden;
}

.preview-frame {
  display: flex;
  justify-content: center;
}

.preview-shell {
  width: 430px;
  max-width: 100%;
  display: grid;
  gap: var(--content-gap-3);
  padding: 10px;
  border-radius: calc(var(--content-radius-2) + 2px);
  border: 1px solid var(--content-border-1);
  background:
    linear-gradient(180deg, rgba(8, 15, 26, 0.96), rgba(10, 18, 32, 0.94)),
    #0a1322;
}

.preview-block {
  padding: 16px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: rgba(10, 18, 31, 0.76);
}

.preview-stage,
.preview-list {
  display: grid;
  gap: var(--content-gap-2);
  margin-top: 14px;
}

.preview-stage {
  grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr);
}

.schedule-table {
  display: grid;
  gap: var(--content-gap-3);
}

.leave-dialog__lead,
.leave-dialog__sub {
  color: #3d4a5d;
  line-height: 1.7;
}

.leave-dialog__sub {
  margin-top: 10px;
}

:deep(.schedule-row--active > td) {
  background: rgba(194, 164, 108, 0.08) !important;
}

@media (max-width: 1580px) {
  .schedule-workbench {
    grid-template-columns: minmax(280px, 0.95fr) minmax(440px, 1.2fr);
  }

  .preview-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1180px) {
  .schedule-workbench,
  .preview-stage,
  .canvas-meta__grid {
    grid-template-columns: 1fr;
  }

  .pool-panel,
  .canvas-panel {
    max-height: none;
  }
}

@media (max-width: 860px) {
  .schedule-toolbar,
  .schedule-dirty,
  .panel-head,
  .slot-entity,
  .pool-card__actions,
  .sortable-card__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .schedule-toolbar__actions {
    width: 100%;
  }

  .schedule-toolbar__actions :deep(.el-date-editor) {
    width: 100%;
  }
}
</style>
