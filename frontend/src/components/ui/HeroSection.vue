<script setup lang="ts">
defineProps<{
  eyebrow?: string
  title: string
  subtitle?: string
  primaryText?: string
  secondaryText?: string
}>()

const emit = defineEmits<{
  primary: []
  secondary: []
}>()
</script>

<template>
  <section class="hero" v-motion :initial="{ opacity: 0, y: 36 }" :visibleOnce="{ opacity: 1, y: 0 }">
    <div class="hero-layer"></div>
    <div class="hero-content">
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <h1 class="title">{{ title }}</h1>
      <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
      <div class="hero-actions">
        <el-button v-if="primaryText" type="primary" size="large" @click="emit('primary')">
          {{ primaryText }}
        </el-button>
        <el-button v-if="secondaryText" size="large" @click="emit('secondary')">
          {{ secondaryText }}
        </el-button>
      </div>
      <slot />
    </div>
  </section>
</template>

<style scoped>
.hero {
  position: relative;
  overflow: hidden;
  border-radius: 24px;
  min-height: min(76vh, 680px);
  border: 1px solid rgba(165, 179, 217, 0.18);
  background:
    linear-gradient(135deg, rgba(9, 14, 24, 0.88), rgba(11, 24, 42, 0.54)),
    radial-gradient(circle at 80% 26%, rgba(101, 191, 210, 0.26), transparent 42%),
    radial-gradient(circle at 20% 14%, rgba(206, 176, 125, 0.26), transparent 44%),
    linear-gradient(180deg, #0a111f, #090f1b 64%, #08101f);
  box-shadow: 0 30px 80px rgba(2, 6, 23, 0.5);
}

.hero-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image: linear-gradient(transparent 96%, rgba(255, 255, 255, 0.03) 97%);
  background-size: 100% 22px;
  opacity: 0.2;
}

.hero-content {
  max-width: 680px;
  padding: clamp(28px, 7vw, 72px);
}

.eyebrow {
  margin: 0;
  color: #9bb7de;
  letter-spacing: 0.13em;
  text-transform: uppercase;
  font-size: 12px;
}

.title {
  margin: 14px 0 0;
  font-size: clamp(32px, 5.8vw, 62px);
  line-height: 1.06;
  color: #f8fafc;
  letter-spacing: 0.01em;
  font-family: var(--font-display);
}

.subtitle {
  margin: 18px 0 0;
  color: #d3deef;
  line-height: 1.72;
  font-size: clamp(14px, 2.1vw, 18px);
  max-width: 600px;
}

.hero-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

:deep(.el-button--primary) {
  --el-button-bg-color: #c3a26e;
  --el-button-border-color: #c3a26e;
  --el-button-hover-bg-color: #d3b786;
  --el-button-hover-border-color: #d3b786;
  --el-button-active-bg-color: #b8935d;
  --el-button-active-border-color: #b8935d;
  --el-button-text-color: #0f172a;
  font-weight: 700;
}

@media (max-width: 768px) {
  .hero {
    min-height: 520px;
  }
}
</style>
