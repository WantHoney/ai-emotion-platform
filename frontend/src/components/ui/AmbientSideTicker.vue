<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const TOP_OFFSET = 74
const ROW_SPACING_MIN = 132
const ROW_SPACING_MAX = 246
const MESSAGE_TRACK_LENGTH_MIN = 7
const MESSAGE_TRACK_LENGTH_MAX = 11
const RIGHT_BOUNDARY_TRIM = 10
const EMOTICON_INSERT_RATE = 0.12

type TickerRow = {
  id: string
  top: string
  duration: number
  delay: number
  offset: string
  opacity: number
  scale: number
  items: Array<{
    id: string
    text: string
  }>
}

const route = useRoute()

const rows = ref<TickerRow[]>([])
const sideBounds = ref({
  left: 280,
  right: 280,
})
const layoutHeight = ref(1600)

let frameHandle = 0
let generationCounter = 0
let pageMutationObserver: MutationObserver | null = null
let layoutResizeObserver: ResizeObserver | null = null

const createRng = (seed: number) => {
  let value = seed >>> 0
  return () => {
    value += 0x6d2b79f5
    let mixed = Math.imul(value ^ (value >>> 15), value | 1)
    mixed ^= mixed + Math.imul(mixed ^ (mixed >>> 7), mixed | 61)
    return ((mixed ^ (mixed >>> 14)) >>> 0) / 4294967296
  }
}

const shuffle = <T,>(source: T[], rng: () => number) => {
  const next = [...source]
  for (let index = next.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(rng() * (index + 1))
    const current = next[index]
    next[index] = next[swapIndex] as T
    next[swapIndex] = current as T
  }
  return next
}

const unique = <T,>(source: T[]) => Array.from(new Set(source))

const hashText = (value: string) => {
  let hash = 2166136261
  for (const char of value) {
    hash ^= char.charCodeAt(0)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

const buildVariantMessages = (
  seed: number,
  count: number,
  intros: string[],
  middles: string[],
  closings: string[],
) => {
  const rng = createRng(seed)
  const results = new Set<string>()

  while (results.size < count && intros.length > 0 && middles.length > 0 && closings.length > 0) {
    const intro = intros[Math.floor(rng() * intros.length)] as string
    const middle = middles[Math.floor(rng() * middles.length)] as string
    const closing = closings[Math.floor(rng() * closings.length)] as string
    results.add(`${intro}${middle}${closing}`)
  }

  return [...results]
}

const GENERAL_CORE_MESSAGES = [
  '慢一点也行，今天不用把自己赶得太紧。',
  '没关系，脑子很吵的时候，先活过这几分钟就很好。',
  '有点乱，也不代表你做得不够好。',
  '答案来晚一点，并不耽误你好好生活。',
  '肩膀放松一点，心也会慢一点。',
  '累了就歇一会儿，不用把每一秒都撑满。',
  '把目光收回眼前，事情会小一点。',
  '如果今天有点重，就只顾好今天。',
  '事情多的时候，更要记得照顾自己。',
  '世界不需要你这一刻就想明白。',
  '允许自己卡一下，也是一种呼吸。',
  '你不是在拖延，你只是在消化很多东西。',
  '今天能走到这里，已经很不容易。',
  '把力气留给现在，比急着证明自己更重要。',
  '有些难受，先被承认就会松一点。',
  '不用把每件事都解释得很清楚。',
  '这会儿只顾呼吸，也算在认真过日子。',
  '如果还没准备好，就先别催自己。',
  '把速度降下来，世界会重新变得可读。',
  '哪怕只往前半步，也是在往前。',
  '今天的你，不需要表现得很完整。',
  '不想逞强的时候，就别硬撑着体面。',
  '把情绪放在桌上，总比一直攥在心里轻一点。',
  '心里有风的时候，步子慢一点很正常。',
  '你可以暂时不解决，只是陪自己待一会儿。',
  '给自己留白，不算浪费。',
  '如果一下子太多，就只看眼前这一格。',
  '没做完也没关系，人不是流程图。',
  '世界很吵的时候，把自己先照顾好。',
  '今天先把自己放在事情前面。',
  '有些感受不需要立刻变得正确。',
  '如果说不清楚，也可以先不说清楚。',
  '把手心摊开一点，整个人都会软一点。',
  '不舒服的时候，允许自己变慢。',
  '不是每一种沉默都需要解释。',
  '你没有落后，你只是在走自己的速度。',
  '这段时间辛苦了，不必装作没事。',
  '让脑子休息一下，事情未必会更糟。',
  '把今天过稳，比把今天过满更重要。',
  '此刻不求漂亮，只求舒服一点。',
  '今天如果只能顾好一件事，就先顾好自己。',
  '被看见之前，先试着看见自己。',
  '不想硬扛的时候，也可以承认自己累了。',
  '世界可以先放一放，你先回到自己这里。',
  '有些答案，需要在松一点的时候才会出现。',
  '让心落地，比让事情立刻结束更重要。',
  '不用总是表现得很会处理。',
  '这会儿不必勇敢得那么完整。',
  '把难受放轻一点，不代表它不重要。',
  '你不需要一边难受，一边表现得很厉害。',
  '慢慢来，不会让你变差。',
  '把这一分钟过好，就很有力量。',
  '你值得被温柔对待，哪怕只是被自己。',
  '今天没有满分任务，只有照顾自己的任务。',
  '不必跟别人的节奏比呼吸。',
  '如果世界太满，就把心收回来一点。',
  '正在调整，不等于停住。',
  '很多事情本来就需要慢慢懂。',
  '先把自己接住，别的再说。',
  '有事也可以慢慢讲，不急。',
]

const GENERAL_VARIANT_MESSAGES = buildVariantMessages(
  20260412,
  96,
  [
    '如果今天有点重，',
    '这会儿不想太快也行，',
    '把注意力收回眼前，',
    '让节奏慢一点，',
    '今天不想逞强的话，',
    '当脑子很吵的时候，',
    '如果情绪有点满，',
    '把速度还给自己，',
    '世界显得太响的时候，',
    '哪怕只前进一点点，',
    '此刻先顾好身体，',
    '如果答案还没来，',
    '这一阵子辛苦了，',
    '当心里有点乱时，',
    '把肩膀放松一点，',
    '不想把自己绷太紧的话，',
    '当你只剩一点力气时，',
    '如果这一天很长，',
  ],
  [
    '也已经算在照顾自己了',
    '不用马上想明白',
    '会一点点松开',
    '可以把力气留给现在',
    '就已经很好',
    '也算认真生活',
    '不算停在原地',
    '会慢慢清楚起来',
    '可以只做这一小步',
    '不必一次处理所有事',
    '也没必要现在就回答全部问题',
    '值得被温柔对待',
    '说明你已经很努力了',
    '会比想象中更稳一点',
    '不需要证明自己很能扛',
  ],
  [
    '。',
    '，慢一点也没关系。',
    '，今天做到这里也可以。',
    '，不需要一次把所有事做完。',
    '，答案会在后面慢慢出现。',
    '，这已经很不容易了。',
    '，让事情一件一件来就好。',
  ],
)

const HOME_MESSAGES = [
  '今天先把生活过顺一点，不用过得很满。',
  '回到这里，本身就算一种自我照顾。',
  '把支持重新连回来，事情会没那么硬。',
  '首页不是起跑线，更像一个缓冲区。',
  '你可以从最轻的那一步开始。',
  '今天不用急着把自己整理好。',
  '如果想停一下，这里可以先坐一会儿。',
  '把今天过稳，比过猛更重要。',
  '来这里不是为了表现好，是为了轻一点。',
  '从一个小动作开始，也会带来变化。',
  '让今天先有一点余地，再谈效率。',
  '把自己安顿好，就是很重要的事。',
]

const CONTENT_MESSAGES = [
  '读到有感觉的地方，停一下也很好。',
  '一段文字托住一点情绪，就已经有意义。',
  '读不快的时候，不用勉强自己往前赶。',
  '有共鸣的句子，可以留给今天多待一会儿。',
  '不是所有内容都要立刻消化完。',
  '如果某一句刺中了你，也可以先合上页面。',
  '把真正有感觉的那一句留下来就够了。',
  '阅读不是任务，更像给心留出空间。',
  '看懂一点点，也算开始理解自己。',
  '不需要每篇都读完，读到有帮助就很好。',
  '有些句子会在晚一点的时候再发光。',
  '如果想慢慢读，这里本来就适合慢一点。',
  '内容不一定要全记住，有感觉就够了。',
  '读到想呼吸一下的时候，就先呼吸一下。',
]

const UPLOAD_MESSAGES = [
  '这次上传不需要完美，只需要开始。',
  '声音有停顿，也是一部分真实。',
  '说到哪里就到哪里，不必一次说完。',
  '卡住不是失败，卡住只是卡住。',
  '如果一时想不好怎么讲，就先讲眼前这一点。',
  '上传这一小步，本身就很勇敢。',
  '说不顺也没关系，真实比流畅更重要。',
  '有些难讲的话，交给录音也可以。',
  '开始记录，比反复犹豫更温柔一点。',
  '这不是表演，不需要一句句都漂亮。',
  '愿意开口，本身就是往前。',
  '哪怕只有几句话，也值得被认真听见。',
]

const TASK_MESSAGES = [
  '处理中不等于被搁下。',
  '等待的时候，也可以先把自己放松一点。',
  '进度慢一点，不代表它没有在走。',
  '别一直盯着状态条，先让眼睛休息一下。',
  '任务在跑的时候，你不用一直绷着。',
  '处理需要时间，不说明你哪里做错了。',
  '看进度的时候，也别忘了看自己。',
  '有些事在后台进行，你也可以先松一口气。',
  '不是每一分钟都要盯着结果。',
  '等待并不空白，它也是过程的一部分。',
]

const REPORT_MESSAGES = [
  '报告是线索，不是判决。',
  '看见波动，不等于给自己下定义。',
  '每一条结果，都只是帮助你多理解一点自己。',
  '读到重的地方，可以停一下再看。',
  '数据在描述状态，不是在评价你。',
  '有感觉的时候，先感受，不急着分析。',
  '读报告不是审判自己，是慢慢理解自己。',
  '结果只是一面镜子，不是结论书。',
  '曲线和分数都不代表你的全部。',
  '如果某一段让你心里发紧，就先休息一下。',
  '看懂一点，就已经很有帮助。',
  '报告可以慢慢读，不必一口气读完。',
]

const TRENDS_MESSAGES = [
  '趋势是在看变化，不是在给你定性。',
  '波动说明你在经历，不说明你失败。',
  '把曲线当作天气，会比当作宣判轻一点。',
  '看见模式，是为了更温柔地照顾自己。',
  '趋势页不是成绩单，它更像回顾。',
  '高低起伏都只是状态，不是标签。',
  '变化本身就说明你不是被困住的。',
  '有些波动值得理解，不必急着纠正。',
  '图表只是提醒你发生过什么，不是替你下结论。',
  '从模式里看见自己，本来就需要一点耐心。',
]

const PSY_CENTER_MESSAGES = [
  '求助不是添麻烦，是认真对待自己。',
  '愿意联系支持，本身就很勇敢。',
  '把手伸出去，也是一种力量。',
  '有人接住你，并不丢脸。',
  '支持资源不是最后一步，也可以是现在这一步。',
  '不必熬到完全扛不住，才允许自己求助。',
  '愿意找人一起面对，是成熟，不是软弱。',
  '把求助这件事说出来，也值得被肯定。',
  '靠近支持，本来就是照顾自己的一部分。',
  '你不用单独把所有事情扛完。',
]

const PROFILE_MESSAGES = [
  '个人中心不是汇报厅，可以松一点。',
  '把自己的节奏排进生活里，也很重要。',
  '留给自己的空间，也算一种安排。',
  '照顾自己这件事，值得被放进日程。',
  '你可以按自己的速度整理生活。',
  '不是只有高效的时候，人才值得被喜欢。',
  '你的节奏，可以由你自己来决定。',
  '把自己放在日程里，不是自私。',
  '生活不是只有完成，还有安顿。',
  '照顾自己的部分，也算在进步里。',
]

const EMOTICON_LINES = ['^_^', ':)', '(*^_^*)', '( ´ ▽ ` )ﾉ', '(｡･ω･｡)', '(*ˉ︶ˉ*)']

const GENERAL_MESSAGES = unique([...GENERAL_CORE_MESSAGES, ...GENERAL_VARIANT_MESSAGES])

const resolveThemeMessages = (path: string) => {
  if (path.startsWith('/app/content/articles/') || path.startsWith('/app/content/books/') || path === '/app/content') {
    return CONTENT_MESSAGES
  }

  if (path.startsWith('/app/upload')) {
    return UPLOAD_MESSAGES
  }

  if (path.startsWith('/app/tasks')) {
    return TASK_MESSAGES
  }

  if (path.startsWith('/app/reports')) {
    return REPORT_MESSAGES
  }

  if (path.startsWith('/app/trends')) {
    return TRENDS_MESSAGES
  }

  if (path.startsWith('/app/psy-centers')) {
    return PSY_CENTER_MESSAGES
  }

  if (path.startsWith('/app/profile')) {
    return PROFILE_MESSAGES
  }

  return HOME_MESSAGES
}

const activeMessagePool = computed(() => unique([...GENERAL_MESSAGES, ...resolveThemeMessages(route.path)]))

const buildItems = (rowId: string, messages: string[]) =>
  messages.map((message, index) => ({
    id: `${rowId}-${index}`,
    text: message,
  }))

const buildRows = (pageHeight: number, seed: number, pool: string[]) => {
  if (pool.length === 0) {
    return []
  }

  const rng = createRng(seed || 1)
  let messageDeck = shuffle(pool, rng)
  let deckIndex = 0

  const takeMessages = (count: number) => {
    const chunk: string[] = []

    while (chunk.length < count) {
      if (deckIndex >= messageDeck.length) {
        messageDeck = shuffle(pool, rng)
        deckIndex = 0
      }

      const candidate = messageDeck[deckIndex] as string
      deckIndex += 1

      if (chunk.length > 0 && chunk[chunk.length - 1] === candidate) {
        continue
      }

      chunk.push(candidate)
    }

    if (rng() < EMOTICON_INSERT_RATE) {
      const emoticon = EMOTICON_LINES[Math.floor(rng() * EMOTICON_LINES.length)] as string
      const insertIndex = Math.min(chunk.length, 1 + Math.floor(rng() * Math.max(chunk.length - 1, 1)))
      chunk.splice(insertIndex, 0, emoticon)
    }

    return chunk
  }

  const nextRows: TickerRow[] = []
  let top = 88 + rng() * 54
  let rowIndex = 0

  while (top < pageHeight - 48) {
    const duration = 82 + rng() * 32
    const delay = -rng() * duration
    const offset = -(5 + rng() * 8)
    const opacity = 0.2 + rng() * 0.16
    const scale = 0.88 + rng() * 0.12
    const messageCount =
      MESSAGE_TRACK_LENGTH_MIN + Math.floor(rng() * (MESSAGE_TRACK_LENGTH_MAX - MESSAGE_TRACK_LENGTH_MIN + 1))
    const messages = takeMessages(messageCount)
    const rowId = `ticker-row-${seed}-${rowIndex + 1}`

    nextRows.push({
      id: rowId,
      top: `${top.toFixed(2)}px`,
      duration,
      delay,
      offset: `${offset.toFixed(2)}%`,
      opacity,
      scale,
      items: buildItems(rowId, messages),
    })

    top += ROW_SPACING_MIN + rng() * (ROW_SPACING_MAX - ROW_SPACING_MIN)
    rowIndex += 1
  }

  return nextRows
}

const rowStyle = (row: TickerRow): Record<string, string> => ({
  '--ticker-top': row.top,
  '--ticker-duration': `${row.duration.toFixed(2)}s`,
  '--ticker-delay': `${row.delay.toFixed(2)}s`,
  '--ticker-offset': row.offset,
  '--ticker-row-opacity': row.opacity.toFixed(3),
  '--ticker-row-scale': row.scale.toFixed(3),
})

const tickerStyle = computed<Record<string, string>>(() => ({
  '--ticker-top-offset': `${TOP_OFFSET}px`,
  '--ticker-left-width': `${sideBounds.value.left.toFixed(2)}px`,
  '--ticker-right-width': `${sideBounds.value.right.toFixed(2)}px`,
  '--ticker-layout-height': `${layoutHeight.value.toFixed(2)}px`,
}))

const findPageBody = () => document.querySelector('.page-shell.user .page-body')

const findTickerShell = () => document.querySelector('.user-layout__shell')

const findFocusElement = () => {
  const pageBody = findPageBody()
  if (!(pageBody instanceof HTMLElement)) {
    return null
  }

  const focus = pageBody.querySelector<HTMLElement>(
    '[data-ticker-focus], .hero, .content-stage-elevated, .content-stage, .content-entry-hero, .content-entry-stage',
  )

  return focus ?? pageBody
}

const regenerateRows = (pageHeight: number) => {
  generationCounter += 1
  const seed = (hashText(route.fullPath) ^ Math.floor(pageHeight) ^ (generationCounter * 2654435761)) >>> 0
  rows.value = buildRows(pageHeight, seed, activeMessagePool.value)
}

const measureTickerLayout = () => {
  frameHandle = 0

  if (window.innerWidth < 1180) {
    return
  }

  const focusElement = findFocusElement()
  if (!focusElement) {
    return
  }

  const rect = focusElement.getBoundingClientRect()
  sideBounds.value = {
    left: Math.max(0, Math.min(rect.left, window.innerWidth)),
    right: Math.max(0, Math.min(window.innerWidth - rect.right - RIGHT_BOUNDARY_TRIM, window.innerWidth)),
  }

  const shell = findTickerShell()
  let nextHeight = Math.max(document.documentElement.scrollHeight - TOP_OFFSET, 0)

  if (shell instanceof HTMLElement) {
    const shellRect = shell.getBoundingClientRect()
    const footer = shell.querySelector('.shell-footer')

    if (footer instanceof HTMLElement) {
      const footerRect = footer.getBoundingClientRect()
      nextHeight = Math.max(footerRect.top - shellRect.top - TOP_OFFSET, 0)
    } else {
      nextHeight = Math.max(shell.scrollHeight - TOP_OFFSET, 0)
    }
  }

  if (Math.abs(nextHeight - layoutHeight.value) > 32 || rows.value.length === 0) {
    layoutHeight.value = nextHeight
    regenerateRows(nextHeight)
  }
}

const scheduleMeasure = () => {
  if (frameHandle) {
    cancelAnimationFrame(frameHandle)
  }

  frameHandle = window.requestAnimationFrame(measureTickerLayout)
}

const reconnectObservers = () => {
  pageMutationObserver?.disconnect()
  layoutResizeObserver?.disconnect()

  const pageBody = findPageBody()
  if (pageBody instanceof HTMLElement) {
    pageMutationObserver = new MutationObserver(() => {
      scheduleMeasure()
    })

    pageMutationObserver.observe(pageBody, {
      childList: true,
      subtree: true,
    })
  }

  const shell = findTickerShell()
  if (shell instanceof HTMLElement) {
    layoutResizeObserver = new ResizeObserver(() => {
      scheduleMeasure()
    })

    layoutResizeObserver.observe(shell)
  }
}

watch(
  () => route.fullPath,
  async () => {
    await nextTick()
    rows.value = []
    reconnectObservers()
    scheduleMeasure()
  },
)

watch(activeMessagePool, () => {
  rows.value = []
  scheduleMeasure()
})

onMounted(async () => {
  await nextTick()
  reconnectObservers()
  scheduleMeasure()
  window.addEventListener('resize', scheduleMeasure, { passive: true })
})

onBeforeUnmount(() => {
  if (frameHandle) {
    cancelAnimationFrame(frameHandle)
  }

  pageMutationObserver?.disconnect()
  layoutResizeObserver?.disconnect()
  window.removeEventListener('resize', scheduleMeasure)
})
</script>

<template>
  <div class="ambient-side-ticker" aria-hidden="true" :style="tickerStyle">
    <div class="ambient-side-ticker__viewport ambient-side-ticker__viewport--left">
      <div
        v-for="row in rows"
        :key="`${row.id}-left`"
        class="ambient-side-ticker__row ambient-side-ticker__row--left"
        :style="rowStyle(row)"
      >
        <div class="ambient-side-ticker__marquee">
          <div class="ambient-side-ticker__track">
            <span v-for="item in row.items" :key="`${item.id}-left`" class="ambient-side-ticker__bubble">
              {{ item.text }}
            </span>
          </div>
          <div class="ambient-side-ticker__track" aria-hidden="true">
            <span v-for="item in row.items" :key="`${item.id}-left-clone`" class="ambient-side-ticker__bubble">
              {{ item.text }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="ambient-side-ticker__viewport ambient-side-ticker__viewport--right">
      <div
        v-for="row in rows"
        :key="`${row.id}-right`"
        class="ambient-side-ticker__row ambient-side-ticker__row--right"
        :style="rowStyle(row)"
      >
        <div class="ambient-side-ticker__marquee ambient-side-ticker__marquee--right">
          <div class="ambient-side-ticker__track">
            <span v-for="item in row.items" :key="`${item.id}-right`" class="ambient-side-ticker__bubble">
              {{ item.text }}
            </span>
          </div>
          <div class="ambient-side-ticker__track" aria-hidden="true">
            <span v-for="item in row.items" :key="`${item.id}-right-clone`" class="ambient-side-ticker__bubble">
              {{ item.text }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ambient-side-ticker {
  position: absolute;
  top: var(--ticker-top-offset);
  left: 0;
  right: 0;
  height: var(--ticker-layout-height);
  z-index: 2;
  overflow: hidden;
  pointer-events: none;
  user-select: none;
  --ticker-left-width: clamp(200px, 18vw, 320px);
  --ticker-right-width: clamp(200px, 18vw, 320px);
}

.ambient-side-ticker__viewport {
  position: absolute;
  top: 0;
  bottom: 0;
  overflow: hidden;
}

.ambient-side-ticker__viewport--left {
  left: 0;
  width: var(--ticker-left-width);
  max-width: 44vw;
  min-width: 0;
  -webkit-mask-image: linear-gradient(to right, rgba(0, 0, 0, 0.92) 0, rgba(0, 0, 0, 1) 100%);
  mask-image: linear-gradient(to right, rgba(0, 0, 0, 0.92) 0, rgba(0, 0, 0, 1) 100%);
}

.ambient-side-ticker__viewport--right {
  right: 0;
  width: var(--ticker-right-width);
  max-width: 44vw;
  min-width: 0;
  -webkit-mask-image: linear-gradient(to left, rgba(0, 0, 0, 0.92) 0, rgba(0, 0, 0, 1) 100%);
  mask-image: linear-gradient(to left, rgba(0, 0, 0, 0.92) 0, rgba(0, 0, 0, 1) 100%);
}

.ambient-side-ticker__row {
  position: absolute;
  top: var(--ticker-top);
  width: 100vw;
  opacity: var(--ticker-row-opacity);
  transform: translateY(-50%) scale(var(--ticker-row-scale));
  pointer-events: none;
}

.ambient-side-ticker__row--left {
  left: 0;
  transform-origin: left center;
}

.ambient-side-ticker__row--right {
  right: 0;
  width: var(--ticker-right-width);
  transform-origin: right center;
}

.ambient-side-ticker__marquee {
  display: flex;
  gap: 24px;
  width: max-content;
  animation: ticker-slide-left var(--ticker-duration) linear infinite;
  animation-delay: var(--ticker-delay);
}

.ambient-side-ticker__marquee--right {
  animation-name: ticker-slide-right-gutter;
}

.ambient-side-ticker__track {
  display: flex;
  align-items: center;
  gap: 24px;
  padding-right: 24px;
  pointer-events: none;
}

.ambient-side-ticker__bubble {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(175, 201, 237, 0.16);
  background: linear-gradient(180deg, rgba(24, 38, 62, 0.28), rgba(11, 18, 32, 0.1));
  color: rgba(239, 245, 255, 0.72);
  font-size: 12px;
  letter-spacing: 0.03em;
  white-space: nowrap;
  box-shadow: 0 10px 22px rgba(4, 10, 22, 0.1);
  backdrop-filter: blur(10px);
  pointer-events: auto;
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    background 180ms ease,
    color 180ms ease,
    box-shadow 180ms ease;
}

.ambient-side-ticker__bubble:nth-child(4n) {
  color: rgba(255, 239, 205, 0.76);
  border-color: rgba(201, 174, 130, 0.2);
}

.ambient-side-ticker__row:has(.ambient-side-ticker__bubble:hover) .ambient-side-ticker__marquee {
  animation-play-state: paused;
}

.ambient-side-ticker__bubble:hover {
  transform: translateY(-1px) scale(1.065);
  color: rgba(255, 255, 255, 1);
  border-color: rgba(224, 239, 255, 0.68);
  background: linear-gradient(180deg, rgba(82, 118, 176, 0.92), rgba(34, 52, 80, 0.54));
  box-shadow: 0 18px 36px rgba(4, 10, 22, 0.3);
}

@keyframes ticker-slide-left {
  from {
    transform: translate3d(var(--ticker-offset), 0, 0);
  }

  to {
    transform: translate3d(calc(-50% + var(--ticker-offset)), 0, 0);
  }
}

@keyframes ticker-slide-right-gutter {
  from {
    transform: translate3d(calc(var(--ticker-right-width) + var(--ticker-offset)), 0, 0);
  }

  to {
    transform: translate3d(calc(-50% + var(--ticker-offset)), 0, 0);
  }
}

@media (max-width: 1179px) {
  .ambient-side-ticker {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ambient-side-ticker__marquee {
    animation-duration: calc(var(--ticker-duration) * 2.6);
  }
}
</style>
