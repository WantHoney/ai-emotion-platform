<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  dates: string[]
  selectedDate?: string
  todayDate?: string
}>()

defineEmits<{
  select: [date: string]
}>()

const groupedDates = computed(() => {
  const groups = new Map<string, string[]>()
  for (const date of props.dates) {
    const monthKey = date.slice(0, 7)
    const current = groups.get(monthKey) ?? []
    current.push(date)
    groups.set(monthKey, current)
  }
  return Array.from(groups.entries())
})

const formatMonth = (value: string) => value.replace('-', ' 年 ') + ' 月'
</script>

<template>
  <div class="archive-calendar">
    <div v-for="[month, monthDates] in groupedDates" :key="month" class="archive-calendar__month">
      <div class="archive-calendar__month-head">
        <h3>{{ formatMonth(month) }}</h3>
        <p>{{ monthDates.length }} 条每日内容</p>
      </div>

      <div class="archive-calendar__grid">
        <button
          v-for="date in monthDates"
          :key="date"
          type="button"
          class="archive-calendar__day"
          :class="{ active: date === selectedDate, today: date === todayDate }"
          @click="$emit('select', date)"
        >
          <span>{{ date.slice(8, 10) }}</span>
          <small>{{ date === todayDate ? '今天' : '查看' }}</small>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.archive-calendar {
  display: grid;
  gap: var(--content-gap-3);
}

.archive-calendar__month {
  padding: 20px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-1);
  box-shadow: var(--content-shadow-1);
}

.archive-calendar__month-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.archive-calendar__month-head h3,
.archive-calendar__month-head p {
  margin: 0;
}

.archive-calendar__month-head h3 {
  color: #f4f9ff;
  font-size: 18px;
}

.archive-calendar__month-head p {
  color: #9fb4d6;
}

.archive-calendar__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: var(--content-gap-2);
  margin-top: 16px;
}

.archive-calendar__day {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  min-height: 72px;
  border-radius: var(--content-radius-1);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-inset);
  color: #e2edfd;
  cursor: pointer;
  transition:
    border-color var(--content-motion-fast) var(--content-ease-standard),
    background var(--content-motion-fast) var(--content-ease-standard),
    transform var(--content-motion-fast) var(--content-ease-standard);
}

.archive-calendar__day:hover {
  transform: translateY(-2px);
  border-color: var(--content-border-3);
}

.archive-calendar__day span {
  font-size: 20px;
  font-weight: 700;
}

.archive-calendar__day small {
  color: #9db2d5;
}

.archive-calendar__day.active {
  border-color: var(--content-border-3);
  background:
    linear-gradient(180deg, rgba(194, 164, 108, 0.16), rgba(13, 20, 35, 0.74)),
    rgba(194, 164, 108, 0.18);
}

.archive-calendar__day.today {
  box-shadow: 0 0 0 1px rgba(98, 177, 137, 0.52) inset;
}

@media (max-width: 980px) {
  .archive-calendar__grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .archive-calendar__grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
