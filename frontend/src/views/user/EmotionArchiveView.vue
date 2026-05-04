<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getContentHub, type ContentHubPayload } from '@/api/content'
import { getReportList, getReportTrend, type ReportSummary, type ReportTrendItem } from '@/api/report'
import { getTaskList, type AnalysisTask } from '@/api/task'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import BadgeTag from '@/components/ui/BadgeTag.vue'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import { parseError, type ErrorStatePayload } from '@/utils/error'
import { formatEmotion, formatRiskLevel, formatTaskStatus } from '@/utils/uiText'

type TimelineEventKind = 'upload' | 'report' | 'trend' | 'support'

type TimelineTone = 'low' | 'medium' | 'high' | 'neutral'

type TimelineChip = {
  text: string
  tone?: TimelineTone
}

type TimelineAction = {
  text: string
  path: string
}

type TimelineEvent = {
  kind: TimelineEventKind
  label: string
  title: string
  description: string
  chips: TimelineChip[]
  actions: TimelineAction[]
}

type TimelineEntry = {
  dateKey: string
  dateLabel: string
  title: string
  narrative: string
  events: TimelineEvent[]
}

const TIMELINE_LIMIT = 6
const TREND_DAYS = 30

const router = useRouter()

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const taskRows = ref<AnalysisTask[]>([])
const reportRows = ref<ReportSummary[]>([])
const totalReports = ref(0)
const trendRows = ref<ReportTrendItem[]>([])
const contentHub = ref<ContentHubPayload | null>(null)

const toDateKey = (value?: string) => {
  if (!value) return ''
  const match = value.match(/\d{4}-\d{2}-\d{2}/)
  if (match) return match[0]

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return ''
  }
  const year = parsed.getFullYear()
  const month = String(parsed.getMonth() + 1).padStart(2, '0')
  const day = String(parsed.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const compareDateDesc = (left?: string, right?: string) => {
  const leftTime = left ? new Date(left).getTime() : 0
  const rightTime = right ? new Date(right).getTime() : 0
  return rightTime - leftTime
}

const formatDateLabel = (value: string) => {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(parsed)
}

const formatDateTimeLabel = (value?: string) => {
  if (!value) return '时间待记录'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(parsed)
}

const toRiskTone = (value?: string): TimelineTone => {
  const normalized = (value ?? '').toLowerCase()
  if (normalized.includes('high')) return 'high'
  if (normalized.includes('medium') || normalized.includes('attention')) return 'medium'
  if (normalized.includes('low') || normalized.includes('normal')) return 'low'
  return 'neutral'
}

const STAGE_STEPS = ['先把感受留下来', '把结果接成线', '慢慢看见变化']

const buildTrendStage = (avgRiskScore?: number, activeDays = 0, reportCount = 0) => {
  if (reportCount <= 1 || activeDays <= 1) {
    return {
      phaseTitle: '开始把表达留下来',
      phaseSummary: '现在最重要的不是一下子看懂全部，而是先把声音、状态和当天的线索留下来，给后面的理解一个起点。',
      stageIndex: 0,
    }
  }

  const score = Number(avgRiskScore ?? 0)
  if (score >= 70) {
    return {
      phaseTitle: '先把自己接住',
      phaseSummary: '近 30 天的曲线显示，风险分整体仍在偏高区间。这一阶段最重要的是继续留下记录，同时把支持内容接进来，不要只盯着结果本身。',
      stageIndex: 0,
    }
  }
  if (score >= 45) {
    return {
      phaseTitle: '在波动里慢慢整理',
      phaseSummary: '最近不是完全平稳，但你已经在把表达、报告和支持内容慢慢接成一条线。这一阶段更像是从零散记录走向可理解的档案。',
      stageIndex: 1,
    }
  }
  return {
    phaseTitle: '开始看见一些回稳',
    phaseSummary: '曲线暂时没有持续抬高，说明这段时间的记录和整理正在帮你把状态一点点放回可理解的范围里，也更容易回头看见变化。',
    stageIndex: 2,
  }
}

const sortedTasks = computed(() =>
  [...taskRows.value].sort((left, right) => compareDateDesc(left.createdAt, right.createdAt)),
)

const sortedReports = computed(() =>
  [...reportRows.value].sort((left, right) => compareDateDesc(left.createdAt, right.createdAt)),
)

const latestRecordTime = computed(
  () => sortedTasks.value[0]?.createdAt ?? sortedReports.value[0]?.createdAt ?? '',
)

const trendStage = computed(() => {
  const recentRows = [...trendRows.value].sort((left, right) => left.date.localeCompare(right.date))
  const tail = recentRows.slice(-7)
  const avg =
    tail.length === 0 ? 0 : tail.reduce((acc, item) => acc + Number(item.avgRiskScore || 0), 0) / tail.length
  const activeDays = trendRows.value.filter((item) => Number(item.reportCount || 0) > 0).length
  const stage = buildTrendStage(avg, activeDays, totalReports.value)
  return {
    ...stage,
    steps: STAGE_STEPS.map((label, index) => ({
      label,
      state: index < stage.stageIndex ? 'past' : index === stage.stageIndex ? 'current' : 'next',
    })),
  }
})

const summaryCards = computed(() => {
  const activeDays = trendRows.value.filter((item) => Number(item.reportCount || 0) > 0).length
  const todaySupport = contentHub.value?.dailyPackage
  const supportTitle =
    todaySupport?.featuredArticle?.title ??
    todaySupport?.featuredBook?.title ??
    (todaySupport?.quote?.content ? '今日语录已接上' : '今天还没有推荐内容')

  return [
    {
      label: '最近一次记录',
      value: latestRecordTime.value ? formatDateTimeLabel(latestRecordTime.value) : '还没有开始',
      helper: '从这里继续接上这条线',
    },
    {
      label: '近 30 天有记录的天数',
      value: String(activeDays),
      helper: '不是一次结果，而是慢慢形成的轨迹',
    },
    {
      label: '已生成报告',
      value: String(totalReports.value),
      helper: '每一份都可以回看，也能被放进长期变化里',
    },
    {
      label: '今日支持内容',
      value: supportTitle,
      helper: todaySupport?.theme?.themeTitle || '当天推荐的语录、文章和书籍会接在这里',
    },
  ]
})

const buildSupportEvent = (dateKey: string): TimelineEvent | null => {
  const payload = contentHub.value
  if (!payload) return null

  const activeDate = payload.selectedDate || payload.todayDate
  if (!activeDate || activeDate !== dateKey || !payload.dailyPackage.hasSchedule) {
    return null
  }

  const themeTitle = payload.dailyPackage.theme?.themeTitle || '今天的支持内容'
  const quote = payload.dailyPackage.quote?.content
  const article = payload.dailyPackage.featuredArticle?.title
  const book = payload.dailyPackage.featuredBook?.title
  const parts = [quote ? `语录：${quote}` : '', article ? `文章：${article}` : '', book ? `书籍：${book}` : ''].filter(Boolean)

  return {
    kind: 'support',
    label: '支持内容',
    title: '当天的语录和阅读内容已经接上',
    description: parts.length > 0 ? parts.join(' · ') : '这一天有一组配套的支持内容，适合从内容专栏继续往下看。',
    chips: [
      { text: themeTitle, tone: 'neutral' },
      ...(article ? [{ text: '文章', tone: 'low' as TimelineTone }] : []),
      ...(book ? [{ text: '书籍', tone: 'low' as TimelineTone }] : []),
    ],
    actions: [{ text: '去内容专栏', path: '/app/content' }],
  }
}

const trendMap = computed(() => {
  const map = new Map<string, ReportTrendItem>()
  trendRows.value.forEach((row) => {
    map.set(row.date, row)
  })
  return map
})

const timelineEntries = computed<TimelineEntry[]>(() => {
  const dayMap = new Map<string, { tasks: AnalysisTask[]; reports: ReportSummary[] }>()

  sortedTasks.value.forEach((task) => {
    const key = toDateKey(task.createdAt)
    if (!key) return
    const bucket = dayMap.get(key) ?? { tasks: [], reports: [] }
    bucket.tasks.push(task)
    dayMap.set(key, bucket)
  })

  sortedReports.value.forEach((report) => {
    const key = toDateKey(report.createdAt)
    if (!key) return
    const bucket = dayMap.get(key) ?? { tasks: [], reports: [] }
    bucket.reports.push(report)
    dayMap.set(key, bucket)
  })

  const supportDateKey = contentHub.value?.selectedDate || contentHub.value?.todayDate
  if (supportDateKey && !dayMap.has(supportDateKey)) {
    dayMap.set(supportDateKey, { tasks: [], reports: [] })
  }

  return [...dayMap.entries()]
    .sort(([left], [right]) => right.localeCompare(left))
    .slice(0, TIMELINE_LIMIT)
    .map(([dateKey, bucket]) => {
      const events: TimelineEvent[] = []
      const leadTask = bucket.tasks[0]
      const leadReport = bucket.reports[0]
      const dayTrend = trendMap.value.get(dateKey)

      if (bucket.tasks.length > 0) {
        const task = leadTask as AnalysisTask
        events.push({
          kind: 'upload',
          label: '上传/录音',
          title:
            bucket.tasks.length > 1
              ? `这一天留下了 ${bucket.tasks.length} 次表达记录`
              : '这一天先把声音和状态留了下来',
          description:
            bucket.tasks.length > 1
              ? `最近的一次任务是 ${task.taskNo || `任务 ${task.id}`}，当前状态 ${formatTaskStatus(task.status)}。`
              : `${task.taskNo || `任务 ${task.id}`} 已创建，当前状态 ${formatTaskStatus(task.status)}。`,
          chips: [
            { text: formatTaskStatus(task.status), tone: 'neutral' },
            ...(task.taskNo ? [{ text: task.taskNo, tone: 'neutral' as TimelineTone }] : []),
          ],
          actions: [{ text: '查看任务', path: `/app/tasks/${task.id}` }],
        })
      }

      if (bucket.reports.length > 0) {
        const report = leadReport as ReportSummary
        events.push({
          kind: 'report',
          label: '分析报告',
          title:
            bucket.reports.length > 1
              ? `这一天生成了 ${bucket.reports.length} 份报告`
              : '这一天的表达被整理成了一份报告',
          description:
            report.overall || report.riskLevel
              ? `主情绪更偏${report.overall ? formatEmotion(report.overall) : '待识别'}，风险等级 ${report.riskLevel ? formatRiskLevel(report.riskLevel) : '待评估'}。`
              : '这份记录已经被系统整理进报告，可以继续回看细节和建议。',
          chips: [
            ...(report.riskLevel
              ? [{ text: formatRiskLevel(report.riskLevel), tone: toRiskTone(report.riskLevel) }]
              : []),
            ...(report.overall
              ? [{ text: formatEmotion(report.overall), tone: 'neutral' as TimelineTone }]
              : []),
            ...(report.reportNo ? [{ text: report.reportNo, tone: 'neutral' as TimelineTone }] : []),
          ],
          actions: [{ text: '查看报告', path: `/app/reports/${report.id}` }],
        })
      }

      if (dayTrend && Number(dayTrend.reportCount || 0) > 0) {
        const score = Number(dayTrend.avgRiskScore || 0)
        events.push({
          kind: 'trend',
          label: '趋势归档',
          title: '这一天也被放进了长期变化曲线里',
          description:
            score >= 70
              ? `当天共 ${dayTrend.reportCount} 份报告，平均风险分 ${score.toFixed(1)}，这一天在近 30 天里属于需要特别留意的高压节点。`
              : score >= 45
                ? `当天共 ${dayTrend.reportCount} 份报告，平均风险分 ${score.toFixed(1)}，说明这一天更像是波动中的整理阶段。`
                : `当天共 ${dayTrend.reportCount} 份报告，平均风险分 ${score.toFixed(1)}，这一天整体更接近相对平稳的区间。`,
          chips: [
            { text: `平均分 ${score.toFixed(1)}`, tone: toRiskTone(score >= 70 ? 'high' : score >= 45 ? 'medium' : 'low') },
            { text: `${dayTrend.reportCount} 份报告`, tone: 'neutral' },
          ],
          actions: [{ text: '查看趋势', path: '/app/trends' }],
        })
      }

      const supportEvent = buildSupportEvent(dateKey)
      if (supportEvent) {
        events.push(supportEvent)
      }

      const narrativeParts = [
        bucket.tasks.length ? `${bucket.tasks.length} 次表达` : '',
        bucket.reports.length ? `${bucket.reports.length} 份报告` : '',
        dayTrend?.reportCount ? '被接进长期趋势' : '',
        supportEvent ? '当天支持内容已接上' : '',
      ].filter(Boolean)

      return {
        dateKey,
        dateLabel: formatDateLabel(dateKey),
        title:
          supportEvent && bucket.tasks.length === 0 && bucket.reports.length === 0
            ? '今天的支持内容先接上了'
            : bucket.reports.length > 0
              ? '这一天留下了一段完整的情绪线索'
              : '这一天先把表达保存了下来',
        narrative:
          narrativeParts.length > 0
            ? `这一天里有 ${narrativeParts.join('、')}，它们不是散开的页面，而是在慢慢组成一份能回看的情绪档案。`
            : '这一天的记录还很轻，但已经可以成为后续轨迹的一部分。',
        events,
      }
    })
    .filter((entry) => entry.events.length > 0)
})

const loadArchive = async () => {
  loading.value = true
  errorState.value = null

  try {
    const [taskResponse, reportResponse, trendResponse, hubResponse] = await Promise.all([
      getTaskList({ page: 1, pageSize: 10, sortBy: 'createdAt', sortOrder: 'desc' }),
      getReportList({ page: 1, pageSize: 10, sortBy: 'createdAt', sortOrder: 'desc' }),
      getReportTrend(TREND_DAYS),
      getContentHub(),
    ])

    taskRows.value = taskResponse.data.items ?? []
    reportRows.value = reportResponse.data.items ?? []
    totalReports.value = Number(reportResponse.data.total ?? 0)
    trendRows.value = trendResponse.items ?? []
    contentHub.value = hubResponse
  } catch (error) {
    errorState.value = parseError(error, '情绪档案时间轴加载失败')
  } finally {
    loading.value = false
  }
}

const openPath = async (path: string) => {
  await router.push(path)
}

onMounted(() => {
  void loadArchive()
})
</script>

<template>
  <div class="archive-page user-layout">
    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadArchive"
    />
    <EmptyState
      v-else-if="timelineEntries.length === 0"
      title="还没有可以串起来的档案线索"
      description="先留下一次语音记录，后面这条时间轴就会把上传、报告、趋势和支持内容慢慢接起来。"
      action-text="先去上传语音"
      @action="openPath('/app/upload')"
    />
    <template v-else>
      <SectionBlock
        eyebrow="长期线索"
        title="情绪档案时间轴"
        description="把上传、报告、趋势和当天支持内容接成一条线，看看最近这段时间是怎么一步步走到现在的。"
      >
        <div class="archive-hero">
          <div class="archive-hero__copy">
            <p class="archive-hero__eyebrow">当前阶段</p>
            <p class="archive-hero__lead">{{ trendStage.phaseTitle }}</p>
            <p class="archive-hero__text">{{ trendStage.phaseSummary }}</p>
          </div>
          <div class="archive-hero__actions">
            <el-button type="primary" @click="openPath('/app/upload')">新增一条记录</el-button>
            <el-button @click="openPath('/app/reports')">去报告中心</el-button>
          </div>
        </div>

        <div class="archive-stage">
          <div
            v-for="step in trendStage.steps"
            :key="step.label"
            class="archive-stage__item"
            :class="`is-${step.state}`"
          >
            <span class="archive-stage__dot" />
            <span class="archive-stage__label">{{ step.label }}</span>
          </div>
        </div>

        <div class="archive-metrics">
          <article v-for="item in summaryCards" :key="item.label" class="metric-card">
            <p class="metric-card__label">{{ item.label }}</p>
            <strong class="metric-card__value">{{ item.value }}</strong>
            <p class="metric-card__helper">{{ item.helper }}</p>
          </article>
        </div>
      </SectionBlock>

      <SectionBlock
        eyebrow="档案主线"
        title="最近这段时间发生了什么"
        description="每个节点都是一次表达被整理、被理解、再被接住的过程。"
      >
        <div class="timeline">
          <article
            v-for="(entry, index) in timelineEntries"
            :key="entry.dateKey"
            class="timeline-entry"
          >
            <div class="timeline-entry__rail">
              <span class="timeline-entry__dot" />
              <span v-if="index < timelineEntries.length - 1" class="timeline-entry__line" />
            </div>

            <div class="timeline-entry__date">
              <p>{{ entry.dateLabel }}</p>
            </div>

            <div class="timeline-entry__body">
              <div class="timeline-entry__head">
                <h3>{{ entry.title }}</h3>
                <p>{{ entry.narrative }}</p>
              </div>

              <div class="timeline-events">
                <article
                  v-for="event in entry.events"
                  :key="`${entry.dateKey}-${event.kind}-${event.title}`"
                  class="timeline-event"
                >
                  <div class="timeline-event__meta">
                    <span class="timeline-event__kind">{{ event.label }}</span>
                    <div class="timeline-event__chips">
                      <BadgeTag
                        v-for="chip in event.chips"
                        :key="`${event.title}-${chip.text}`"
                        :text="chip.text"
                        :tone="chip.tone ?? 'neutral'"
                      />
                    </div>
                  </div>

                  <div class="timeline-event__content">
                    <div>
                      <h4>{{ event.title }}</h4>
                      <p>{{ event.description }}</p>
                    </div>

                    <div class="timeline-event__actions">
                      <el-button
                        v-for="action in event.actions"
                        :key="`${event.title}-${action.text}`"
                        text
                        type="primary"
                        @click="openPath(action.path)"
                      >
                        {{ action.text }}
                      </el-button>
                    </div>
                  </div>
                </article>
              </div>
            </div>
          </article>
        </div>
      </SectionBlock>

      <SectionBlock
        eyebrow="继续补全"
        title="让这条线再往前走一点"
        description="如果你现在想继续补一段记录，可以从上传、趋势或内容专栏往下接。"
      >
        <div class="archive-actions">
          <button type="button" class="archive-action-card" @click="openPath('/app/upload')">
            <span class="archive-action-card__eyebrow">继续记录</span>
            <strong>再留一次语音或状态切片</strong>
            <p>把今天的感受再往前推一步，后面这条线会更完整。</p>
          </button>
          <button type="button" class="archive-action-card" @click="openPath('/app/trends')">
            <span class="archive-action-card__eyebrow">看阶段变化</span>
            <strong>回到趋势页看整体波动</strong>
            <p>如果想从单点切回整体，可以直接去看近 7 天和近 30 天。</p>
          </button>
          <button type="button" class="archive-action-card" @click="openPath('/app/content')">
            <span class="archive-action-card__eyebrow">接上支持</span>
            <strong>从语录、文章和书籍继续往下看</strong>
            <p>让这条时间轴不只停在分析结果，也能连到后续支持内容。</p>
          </button>
        </div>
      </SectionBlock>
    </template>
  </div>
</template>

<style scoped>
.archive-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.archive-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-end;
  padding: 20px 22px;
  border-radius: 18px;
  border: 1px solid var(--content-border-2);
  background: var(--content-card-bg-highlight);
}

.archive-hero__copy {
  max-width: 700px;
}

.archive-hero__eyebrow {
  margin: 0 0 10px;
  color: var(--content-text-eyebrow);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.archive-hero__lead {
  margin: 0;
  color: var(--content-text-primary);
  font-size: clamp(22px, 3.8vw, 34px);
  line-height: 1.2;
  font-family: var(--font-display);
}

.archive-hero__text {
  margin: 12px 0 0;
  color: var(--content-text-tertiary);
  line-height: 1.8;
}

.archive-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.archive-stage {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.archive-stage__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--content-border-1);
  background: var(--content-card-bg-soft);
}

.archive-stage__item::after {
  content: '';
  position: absolute;
  top: 50%;
  right: -12px;
  width: 24px;
  height: 1px;
  background: color-mix(in srgb, var(--content-chart-axis) 45%, transparent);
  transform: translateY(-50%);
}

.archive-stage__item:last-child::after {
  display: none;
}

.archive-stage__dot {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  border: 1px solid var(--content-chart-border);
  background: color-mix(in srgb, var(--content-chart-axis) 28%, transparent);
  flex-shrink: 0;
}

.archive-stage__label {
  color: var(--content-text-secondary);
  font-size: 14px;
  line-height: 1.5;
}

.archive-stage__item.is-current {
  border-color: var(--content-border-3);
  background: var(--content-surface-2);
  box-shadow: var(--content-shadow-2);
}

.archive-stage__item.is-current .archive-stage__dot {
  background: radial-gradient(circle, color-mix(in srgb, var(--user-accent-cyan) 92%, white), var(--user-accent-cyan));
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--user-accent-cyan) 12%, transparent);
}

.archive-stage__item.is-current .archive-stage__label {
  color: var(--content-text-primary);
  font-weight: 600;
}

.archive-stage__item.is-past .archive-stage__dot {
  background: color-mix(in srgb, #68c5a5 68%, transparent);
  border-color: color-mix(in srgb, #68c5a5 82%, transparent);
}

.archive-stage__item.is-next {
  opacity: 0.72;
}

.archive-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  padding: 18px;
  border-radius: 18px;
  border: 1px solid var(--content-border-1);
  background: var(--content-card-bg-plain);
}

.metric-card__label {
  margin: 0;
  color: var(--content-text-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.metric-card__value {
  display: block;
  margin-top: 12px;
  color: var(--content-text-primary);
  font-size: 22px;
  line-height: 1.45;
}

.metric-card__helper {
  margin: 10px 0 0;
  color: var(--content-text-muted);
  line-height: 1.72;
  font-size: 13px;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.timeline-entry {
  display: grid;
  grid-template-columns: 26px 140px minmax(0, 1fr);
  gap: 16px;
  align-items: flex-start;
}

.timeline-entry__rail {
  position: relative;
  display: flex;
  justify-content: center;
  min-height: 100%;
}

.timeline-entry__dot {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  margin-top: 12px;
  border: 1px solid var(--content-chart-border);
  background: radial-gradient(circle, color-mix(in srgb, var(--user-accent-cyan) 90%, white), color-mix(in srgb, var(--user-accent-cyan) 70%, var(--user-accent-gold)));
  box-shadow: 0 0 0 6px color-mix(in srgb, var(--user-accent-cyan) 12%, transparent);
}

.timeline-entry__line {
  position: absolute;
  top: 34px;
  bottom: -24px;
  width: 1px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--content-chart-axis) 70%, transparent), transparent);
}

.timeline-entry__date {
  padding-top: 4px;
}

.timeline-entry__date p {
  margin: 0;
  color: var(--content-text-muted);
  font-size: 13px;
  letter-spacing: 0.05em;
}

.timeline-entry__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.timeline-entry__head h3 {
  margin: 0;
  color: var(--content-text-primary);
  font-size: 24px;
}

.timeline-entry__head p {
  margin: 10px 0 0;
  color: var(--content-text-tertiary);
  line-height: 1.8;
}

.timeline-events {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.timeline-event {
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid var(--content-border-1);
  background: var(--content-card-bg-plain);
}

.timeline-event__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.timeline-event__kind {
  display: inline-flex;
  align-items: center;
  min-width: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--content-chart-axis) 16%, transparent);
  color: var(--content-text-eyebrow);
  font-size: 12px;
  letter-spacing: 0.06em;
}

.timeline-event__chips {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.timeline-event__content {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-top: 14px;
}

.timeline-event__content h4 {
  margin: 0;
  color: var(--content-text-primary);
  font-size: 18px;
}

.timeline-event__content p {
  margin: 8px 0 0;
  color: var(--content-text-tertiary);
  line-height: 1.8;
}

.timeline-event__actions {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  min-width: fit-content;
}

.archive-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.archive-action-card {
  border: 1px solid var(--content-border-1);
  border-radius: 18px;
  padding: 18px;
  text-align: left;
  background: var(--content-card-bg-plain);
  color: inherit;
  cursor: pointer;
  transition: transform 0.24s ease, border-color 0.24s ease, box-shadow 0.24s ease;
}

.archive-action-card:hover {
  transform: translateY(-4px);
  border-color: var(--content-border-3);
  box-shadow: var(--content-shadow-2);
}

.archive-action-card__eyebrow {
  display: block;
  margin-bottom: 10px;
  color: var(--content-text-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.archive-action-card strong {
  display: block;
  color: var(--content-text-primary);
  font-size: 18px;
}

.archive-action-card p {
  margin: 10px 0 0;
  color: var(--content-text-tertiary);
  line-height: 1.75;
}

@media (max-width: 1080px) {
  .archive-stage,
  .archive-metrics,
  .archive-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .timeline-entry {
    grid-template-columns: 26px minmax(0, 1fr);
  }

  .timeline-entry__date {
    display: none;
  }
}

@media (max-width: 820px) {
  .archive-hero,
  .timeline-event__content {
    flex-direction: column;
    align-items: flex-start;
  }

  .archive-stage,
  .archive-metrics,
  .archive-actions {
    grid-template-columns: 1fr;
  }

  .archive-stage__item::after {
    display: none;
  }

  .timeline-entry {
    grid-template-columns: 18px minmax(0, 1fr);
    gap: 12px;
  }

  .timeline-event__meta {
    flex-direction: column;
  }

  .timeline-event__chips {
    justify-content: flex-start;
  }
}
</style>
