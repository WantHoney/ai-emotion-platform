<template>
  <div class="state-card">
    <el-skeleton animated>
      <template #template>
        <div class="skeleton-line lg"></div>
        <div class="skeleton-line"></div>
        <div class="skeleton-grid">
          <el-skeleton-item v-for="item in 3" :key="item" variant="rect" class="skeleton-block" />
        </div>
      </template>
    </el-skeleton>
  </div>
</template>

<style scoped>
.state-card {
  border: 1px solid rgba(126, 149, 190, 0.38);
  border-radius: var(--radius-lg);
  background: rgba(13, 23, 39, 0.72);
  padding: 20px;
}

.skeleton-line {
  height: 16px;
  border-radius: 8px;
  margin-bottom: 12px;
  background: rgba(103, 143, 201, 0.32);
}

.skeleton-line.lg {
  width: 45%;
  height: 20px;
}

.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 8px;
}

.skeleton-block {
  height: 100px;
  width: 100%;
}

@media (max-width: 900px) {
  .skeleton-grid {
    grid-template-columns: 1fr;
  }
}
</style>