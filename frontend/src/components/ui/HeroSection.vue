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
    <div class="hero-shell">
      <div class="hero-content">
        <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
        <h1 class="title">{{ title }}</h1>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
        <div class="hero-actions">
          <el-button v-if="primaryText" class="hero-cta hero-cta--primary" type="primary" size="large" @click="emit('primary')">
            {{ primaryText }}
          </el-button>
          <el-button v-if="secondaryText" class="hero-cta hero-cta--secondary" size="large" @click="emit('secondary')">
            {{ secondaryText }}
          </el-button>
        </div>
        <slot />
      </div>

      <template v-if="$slots.bottom">
        <div class="hero-divider" aria-hidden="true"></div>
        <div class="hero-bottom">
          <slot name="bottom" />
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.hero {
  position: relative;
  overflow: hidden;
  border-radius: 24px;
  border: 1px solid var(--content-border-2);
  background: var(--content-hero-bg);
  box-shadow: var(--content-shadow-3);
}

.hero-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image: linear-gradient(transparent 96%, var(--content-gridline) 97%);
  background-size: 100% 22px;
  opacity: 0.2;
}

.hero-shell {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  padding: clamp(28px, 7vw, 72px);
}

.hero-content {
  max-width: 680px;
}

.eyebrow {
  margin: 0;
  color: var(--content-text-muted);
  letter-spacing: 0.13em;
  text-transform: uppercase;
  font-size: 12px;
}

.title {
  margin: 14px 0 0;
  font-size: clamp(32px, 5.8vw, 62px);
  line-height: 1.06;
  color: var(--content-text-primary);
  letter-spacing: 0.01em;
  font-family: var(--font-display);
}

.subtitle {
  margin: 18px 0 0;
  color: var(--content-text-secondary);
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

.hero-actions :deep(.hero-cta) {
  min-height: 52px;
  padding: 0 24px;
  border-radius: 16px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.01em;
  box-shadow: var(--content-shadow-1);
  transition:
    transform var(--content-motion-fast) var(--content-ease-standard),
    box-shadow var(--content-motion-fast) var(--content-ease-standard),
    border-color var(--content-motion-fast) var(--content-ease-standard);
}

.hero-actions :deep(.hero-cta:hover) {
  transform: translateY(-1px);
  box-shadow: var(--content-shadow-2);
}

.hero-actions :deep(.hero-cta--secondary) {
  --el-button-text-color: var(--content-button-secondary-text);
  --el-button-hover-text-color: var(--content-button-secondary-text);
  --el-button-active-text-color: var(--content-button-secondary-text);
  color: var(--content-button-secondary-text);
  border-color: var(--content-button-secondary-border);
  background: var(--content-button-secondary-bg);
}

.hero-actions :deep(.hero-cta--secondary:hover) {
  color: var(--content-button-secondary-text);
  border-color: var(--content-button-secondary-hover-border);
  background: var(--content-button-secondary-hover-bg);
}

.hero-divider {
  width: 100%;
  height: 1px;
  margin-top: clamp(28px, 4vw, 40px);
  background: var(--content-border-3);
  box-shadow: 0 0 18px color-mix(in srgb, var(--content-border-3) 22%, transparent);
}

.hero-bottom {
  padding-top: clamp(24px, 4vw, 34px);
}

:deep(.el-button--primary) {
  --el-button-bg-color: var(--content-button-primary-bg);
  --el-button-border-color: var(--content-button-primary-border);
  --el-button-hover-bg-color: var(--content-button-primary-hover-bg);
  --el-button-hover-border-color: var(--content-button-primary-hover-border);
  --el-button-active-bg-color: var(--content-button-primary-active-bg);
  --el-button-active-border-color: var(--content-button-primary-active-border);
  --el-button-text-color: var(--content-button-primary-text);
  font-weight: 700;
  box-shadow: var(--content-button-primary-shadow);
}

@media (max-width: 768px) {
  .hero-shell {
    padding: 28px 22px 24px;
  }

  .hero-actions :deep(.hero-cta) {
    width: 100%;
    justify-content: center;
  }
}
</style>
