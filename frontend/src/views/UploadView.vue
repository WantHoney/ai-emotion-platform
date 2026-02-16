<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type UploadProps } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { uploadAudio } from '@/api/audio'
import { startAnalysis } from '@/api/analysis'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const router = useRouter()
const uploading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const hasUploaded = ref(false)

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const isAudio = file.type.startsWith('audio/')
  if (!isAudio) {
    ElMessage.warning('仅支持音频文件上传。')
  }

  return isAudio
}

const onUpload: UploadProps['httpRequest'] = async (options) => {
  uploading.value = true
  errorState.value = null
  try {
    const { data: uploadData } = await uploadAudio(options.file as File)
    const { data: analysisData } = await startAnalysis(uploadData.audioId)
    hasUploaded.value = true

    ElMessage.success('上传成功，分析任务已启动。')
    await router.push(`/tasks/${analysisData.taskId}`)
  } catch (error) {
    errorState.value = parseError(error, '上传或任务创建失败')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <el-card shadow="hover">
    <template #header>
      <div class="header-row">
        <span>上传音频并启动分析</span>
      </div>
    </template>

    <LoadingState v-if="uploading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="errorState = null"
    />
    <template v-else>
      <el-alert
        title="系统将自动发起分析任务并跳转到任务中心详情页"
        type="info"
        show-icon
        :closable="false"
        class="mb-16"
      />

      <el-upload
        drag
        :show-file-list="true"
        :http-request="onUpload"
        :before-upload="beforeUpload"
        accept="audio/*"
        :disabled="uploading"
        :limit="1"
      >
        <div class="el-upload__text">拖拽音频文件到此，或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 mp3/wav/m4a，单文件上传后自动分析。</div>
        </template>
      </el-upload>

      <EmptyState
        v-if="!hasUploaded"
        title="等待上传"
        description="请选择一个音频文件开始分析流程。"
        action-text="刷新页面"
        @action="$router.go(0)"
      />

      <div class="actions">
        <el-button @click="$router.back()">返回</el-button>
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.mb-16 {
  margin-bottom: 16px;
}

.actions {
  margin-top: 16px;
}
</style>
