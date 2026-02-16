<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type UploadProps } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  cancelUploadSession,
  completeUploadSession,
  initUploadSession,
  uploadSessionChunk,
} from '@/api/audio'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const CHUNK_SIZE = 512 * 1024

const router = useRouter()
const uploading = ref(false)
const hasUploaded = ref(false)
const uploadPercent = ref(0)
const uploadHint = ref('Waiting for upload')
const currentUploadId = ref<string | null>(null)
const errorState = ref<ErrorStatePayload | null>(null)

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const isAudio = file.type.startsWith('audio/')
  if (!isAudio) {
    ElMessage.warning('Only audio files are supported')
  }
  return isAudio
}

const uploadInChunks = async (file: File) => {
  const totalChunks = Math.max(1, Math.ceil(file.size / CHUNK_SIZE))

  uploadHint.value = 'Initializing upload session'
  const init = await initUploadSession({
    fileName: file.name,
    contentType: file.type,
    fileSize: file.size,
    totalChunks,
  })

  currentUploadId.value = init.uploadId
  uploadPercent.value = 0

  for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
    const start = chunkIndex * CHUNK_SIZE
    const end = Math.min(file.size, start + CHUNK_SIZE)
    const chunk = file.slice(start, end)

    uploadHint.value = `Uploading chunk ${chunkIndex + 1}/${totalChunks}`

    await uploadSessionChunk({
      uploadId: init.uploadId,
      chunkIndex,
      chunk,
      onProgress: (chunkPercent) => {
        const doneRatio = chunkIndex / totalChunks
        const currentChunkRatio = (chunkPercent / 100) * (1 / totalChunks)
        uploadPercent.value = Math.min(99, Math.round((doneRatio + currentChunkRatio) * 100))
      },
    })
  }

  uploadHint.value = 'Merging chunks and creating task'
  const complete = await completeUploadSession(init.uploadId, true)
  uploadPercent.value = 100
  uploadHint.value = 'Upload completed'
  currentUploadId.value = null
  return complete
}

const onUpload: UploadProps['httpRequest'] = async (options) => {
  uploading.value = true
  errorState.value = null
  hasUploaded.value = false
  uploadPercent.value = 0

  try {
    const result = await uploadInChunks(options.file as File)
    hasUploaded.value = true

    if (result.taskId) {
      ElMessage.success('Upload completed and analysis task started')
      await router.push(`/tasks/${result.taskId}`)
      return
    }

    ElMessage.success('Upload completed')
  } catch (error) {
    errorState.value = parseError(error, 'Chunk upload failed')
  } finally {
    uploading.value = false
  }
}

const cancelCurrentUpload = async () => {
  if (!currentUploadId.value) {
    return
  }
  try {
    await cancelUploadSession(currentUploadId.value)
    currentUploadId.value = null
    uploadHint.value = 'Upload canceled'
    ElMessage.warning('Upload canceled')
  } catch (error) {
    const parsed = parseError(error, 'Cancel upload failed')
    ElMessage.error(parsed.detail)
  }
}
</script>

<template>
  <el-card shadow="hover">
    <template #header>
      <div class="header-row">
        <span>Audio Upload and Analysis</span>
      </div>
    </template>

    <ErrorState
      v-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="errorState = null"
    />
    <template v-else>
      <el-alert
        title="The client uploads chunks to backend, backend merges and starts analysis task automatically"
        type="info"
        show-icon
        :closable="false"
        class="mb-16"
      />

      <LoadingState v-if="uploading && uploadPercent < 5" />

      <el-upload
        drag
        :show-file-list="true"
        :http-request="onUpload"
        :before-upload="beforeUpload"
        accept="audio/*"
        :disabled="uploading"
        :limit="1"
      >
        <div class="el-upload__text">Drop audio file here, or <em>click to upload</em></div>
        <template #tip>
          <div class="el-upload__tip">Supports mp3/wav/m4a. Chunk upload with real-time progress.</div>
        </template>
      </el-upload>

      <el-card class="mt-16" shadow="never">
        <el-progress :percentage="uploadPercent" :stroke-width="14" />
        <p class="hint">{{ uploadHint }}</p>
        <el-button v-if="currentUploadId" type="danger" text @click="cancelCurrentUpload">
          Cancel Current Upload
        </el-button>
      </el-card>

      <EmptyState
        v-if="!hasUploaded"
        title="No upload yet"
        description="Select an audio file to start chunk upload and analysis workflow."
        action-text="Refresh"
        @action="$router.go(0)"
      />

      <div class="actions">
        <el-button @click="$router.back()">Back</el-button>
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.mb-16 {
  margin-bottom: 16px;
}

.mt-16 {
  margin-top: 16px;
}

.hint {
  margin: 10px 0 0;
  color: #475569;
}

.actions {
  margin-top: 16px;
}
</style>
