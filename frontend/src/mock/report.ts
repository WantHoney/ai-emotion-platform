import type { AnalysisTaskResult } from '@/api/task'

export const mockResult: AnalysisTaskResult = {
  overall: 'neutral',
  confidence: 0.82,
  risk_score: 58,
  risk_level: 'MEDIUM',
  advice_text: '建议结合近期语音样本进行持续观察。',
  segments: [
    { start: 0, end: 8, emotion: 'neutral', confidence: 0.81 },
    { start: 8, end: 15, emotion: 'sad', confidence: 0.72 },
    { start: 15, end: 25, emotion: 'angry', confidence: 0.67 },
    { start: 25, end: 34, emotion: 'neutral', confidence: 0.86 },
    { start: 34, end: 42, emotion: 'happy', confidence: 0.71 },
  ],
}
