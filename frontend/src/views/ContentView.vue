<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getHomeContent, type HomePayload } from '@/api/home'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import LoreCard from '@/components/ui/LoreCard.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const content = ref<HomePayload | null>(null)

const openUrl = (url?: string) => {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

const loadContent = async () => {
  loading.value = true
  errorState.value = null
  try {
    content.value = await getHomeContent()
  } catch (error) {
    errorState.value = parseError(error, 'Failed to load content atlas')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadContent()
})
</script>

<template>
  <div class="content-page user-layout">
    <SectionBlock eyebrow="Atlas" title="Content Archive" description="Curated knowledge cards for emotional self-help and mental awareness.">
      <LoadingState v-if="loading" />
      <ErrorState
        v-else-if="errorState"
        :title="errorState.title"
        :detail="errorState.detail"
        :trace-id="errorState.traceId"
        @retry="loadContent"
      />
      <EmptyState
        v-else-if="!content"
        title="No content data"
        description="Please retry later."
        action-text="Reload"
        @action="loadContent"
      />
      <template v-else>
        <div class="columns">
          <div>
            <h3 class="title">Featured Articles</h3>
            <div class="grid">
              <LoreCard
                v-for="item in content.recommendedArticles"
                :key="`a-${item.id}`"
                :title="item.title"
                :subtitle="item.summary || 'Read article detail'"
                interactive
                @click="openUrl(item.contentUrl)"
              />
            </div>
          </div>
          <div>
            <h3 class="title">Recommended Books</h3>
            <div class="grid">
              <LoreCard
                v-for="item in content.recommendedBooks"
                :key="`b-${item.id}`"
                :title="item.title"
                :subtitle="item.author || 'Open detail page'"
                interactive
                @click="openUrl(item.purchaseUrl)"
              >
                {{ item.description || 'Book recommendation from content operations.' }}
              </LoreCard>
            </div>
          </div>
        </div>
      </template>
    </SectionBlock>
  </div>
</template>

<style scoped>
.content-page {
  display: flex;
  flex-direction: column;
}

.columns {
  display: grid;
  gap: 20px;
}

.title {
  margin: 0 0 10px;
  color: #eef4ff;
  font-family: var(--font-display);
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
