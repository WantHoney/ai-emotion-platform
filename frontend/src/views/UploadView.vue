<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type UploadProps } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import LoreCard from '@/components/ui/LoreCard.vue'
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

const isRecording = ref(false)
const recordingSeconds = ref(0)
const recorderMimeType = ref('audio/webm')
const recordedFile = ref<File | null>(null)
const recordingError = ref<string | null>(null)

let mediaStream: MediaStream | null = null
let mediaRecorder: MediaRecorder | null = null
let timerHandle: number | null = null
let recorderChunks: Blob[] = []

const recorderSupported = computed(() => {
  return typeof window !== 'undefined' && 'MediaRecorder' in window && !!navigator.mediaDevices
})

const releaseRecorderResources = () => {
  if (timerHandle != null) {
    window.clearInterval(timerHandle)
    timerHandle = null
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach((track) => track.stop())
    mediaStream = null
  }
  mediaRecorder = null
  isRecording.value = false
}

const pickRecorderMimeType = () => {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4']
  for (const candidate of candidates) {
    if (MediaRecorder.isTypeSupported(candidate)) {
      return candidate
    }
  }
  return ''
}

const buildRecordFile = (blob: Blob) => {
  const mime = blob.type || 'audio/webm'
  let ext = '.webm'
  if (mime.includes('ogg')) ext = '.ogg'
  else if (mime.includes('mp4') || mime.includes('m4a')) ext = '.m4a'

  return new File([blob], `recording-${Date.now()}${ext}`, { type: mime })
}

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const type = file.type?.toLowerCase() ?? ''
  const name = file.name?.toLowerCase() ?? ''
  const isAudioType = type.startsWith('audio/')
  const isAudioName = /\.(wav|mp3|m4a|ogg|webm|aac|flac)$/i.test(name)
  if (!isAudioType && !isAudioName) {
    ElMessage.warning('Only audio files are supported')
    return false
  }
  return true
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

const submitFile = async (file: File) => {
  uploading.value = true
  errorState.value = null
  hasUploaded.value = false
  uploadPercent.value = 0

  try {
    const result = await uploadInChunks(file)
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

const onUpload: UploadProps['httpRequest'] = async (options) => {
  await submitFile(options.file as File)
}

const startRecording = async () => {
  if (uploading.value || isRecording.value) {
    return
  }
  if (!recorderSupported.value) {
    recordingError.value = 'Current browser does not support in-browser recording'
    return
  }

  recordingError.value = null
  recordedFile.value = null
  recorderChunks = []

  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const mimeType = pickRecorderMimeType()
    recorderMimeType.value = mimeType || 'audio/webm'
    mediaRecorder = mimeType
      ? new MediaRecorder(mediaStream, { mimeType })
      : new MediaRecorder(mediaStream)

    mediaRecorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        recorderChunks.push(event.data)
      }
    }

    mediaRecorder.onstop = () => {
      const blob = new Blob(recorderChunks, {
        type: mediaRecorder?.mimeType || recorderMimeType.value,
      })
      recordedFile.value = buildRecordFile(blob)
      uploadHint.value = 'Recording finished, ready to upload'
      releaseRecorderResources()
    }

    mediaRecorder.start(1000)
    isRecording.value = true
    recordingSeconds.value = 0
    uploadHint.value = 'Recording in progress'

    timerHandle = window.setInterval(() => {
      recordingSeconds.value += 1
    }, 1000)
  } catch (error) {
    recordingError.value = parseError(error, 'Failed to start recording').detail
    releaseRecorderResources()
  }
}

const stopRecording = () => {
  if (!mediaRecorder || mediaRecorder.state === 'inactive') {
    return
  }
  mediaRecorder.stop()
}

const uploadRecorded = async () => {
  if (!recordedFile.value) {
    ElMessage.warning('No recorded audio found')
    return
  }
  await submitFile(recordedFile.value)
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

onBeforeUnmount(() => {
  releaseRecorderResources()
})
</script>

<template>
  <div class="upload-page user-layout">
    <SectionBlock
      eyebrow="Acquisition"
      title="Voice Upload Studio"
      description="Record in-browser or upload local audio. Large files are uploaded in chunks with session progress."
    >
      <ErrorState
        v-if="errorState"
        :title="errorState.title"
        :detail="errorState.detail"
        :trace-id="errorState.traceId"
        @retry="errorState = null"
      />
      <template v-else>
        <div class="layout-grid">
          <LoreCard title="Realtime Recording" subtitle="Capture voice directly from browser microphone.">
            <el-alert
              v-if="!recorderSupported"
              title="Current browser does not support MediaRecorder. Use file upload mode."
              type="warning"
              :closable="false"
              show-icon
              class="mb-12"
            />
            <el-alert
              v-if="recordingError"
              :title="recordingError"
              type="error"
              :closable="false"
              show-icon
              class="mb-12"
            />

            <div class="recording-row">
              <el-tag :type="isRecording ? 'danger' : 'info'">
                {{ isRecording ? `Recording ${recordingSeconds}s` : 'Not recording' }}
              </el-tag>
              <div class="recording-actions">
                <el-button
                  type="danger"
                  :disabled="!recorderSupported || isRecording || uploading"
                  @click="startRecording"
                >
                  Start
                </el-button>
                <el-button type="warning" :disabled="!isRecording" @click="stopRecording">Stop</el-button>
                <el-button type="primary" :disabled="!recordedFile || uploading" @click="uploadRecorded">
                  Upload Record
                </el-button>
              </div>
            </div>
            <p v-if="recordedFile" class="recording-file">
              Ready: {{ recordedFile.name }} ({{ recordedFile.type || 'audio/*' }})
            </p>
          </LoreCard>

          <LoreCard title="File Upload" subtitle="Drag and drop audio files (mp3/wav/m4a/webm).">
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
                <div class="el-upload__tip">Chunk upload with real-time progress and cancel support.</div>
              </template>
            </el-upload>
          </LoreCard>
        </div>

        <LoreCard title="Upload Session Progress">
          <el-progress :percentage="uploadPercent" :stroke-width="14" />
          <p class="hint">{{ uploadHint }}</p>
          <el-button v-if="currentUploadId" type="danger" text @click="cancelCurrentUpload">
            Cancel Current Upload
          </el-button>
        </LoreCard>

        <EmptyState
          v-if="!hasUploaded"
          title="No upload yet"
          description="Record or select an audio file to start chunk upload and analysis workflow."
          action-text="Refresh"
          @action="$router.go(0)"
        />

        <div class="actions">
          <el-button @click="$router.back()">Back</el-button>
        </div>
      </template>
    </SectionBlock>
  </div>
</template>

<style scoped>
.upload-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.layout-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.mb-12 {
  margin-bottom: 12px;
}

.recording-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.recording-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.recording-file {
  margin: 12px 0 0;
  color: #cddbf2;
  font-size: 13px;
}

.hint {
  margin: 10px 0 0;
  color: #b8ccec;
}

.actions {
  margin-top: 10px;
}

@media (max-width: 980px) {
  .layout-grid {
    grid-template-columns: 1fr;
  }
}
</style>
