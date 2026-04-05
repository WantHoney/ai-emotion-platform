export const ARTICLE_CATEGORY_OPTIONS = [
  { label: '压力管理', value: 'stress' },
  { label: '睡眠卫生', value: 'sleep' },
  { label: '焦虑识别', value: 'anxiety' },
  { label: '情绪调节', value: 'emotion' },
  { label: '求助指引', value: 'help-seeking' },
  { label: '沟通支持', value: 'communication' },
] as const

export const ARTICLE_CATEGORY_LABELS: Record<string, string> = {
  stress: '压力管理',
  sleep: '睡眠卫生',
  anxiety: '焦虑识别',
  emotion: '情绪调节',
  'help-seeking': '求助指引',
  communication: '沟通支持',
}

export const ARTICLE_DIFFICULTY_OPTIONS = [
  { label: '入门', value: 'beginner' },
  { label: '实操', value: 'practical' },
  { label: '进阶', value: 'advanced' },
] as const

export const ARTICLE_DIFFICULTY_LABELS: Record<string, string> = {
  beginner: '入门',
  practical: '实操',
  advanced: '进阶',
}

export const SOURCE_LEVEL_LABELS: Record<string, string> = {
  official: '官方来源',
  gov_directory: '政务目录',
  trusted_reference: '可信参考',
}

export const PSY_CENTER_CITY_OPTIONS = [
  { label: '上海', value: '310100' },
  { label: '北京', value: '110100' },
  { label: '杭州', value: '330100' },
  { label: '福州', value: '350100' },
  { label: '广州', value: '440100' },
  { label: '深圳', value: '440300' },
] as const

export const PSY_CENTER_CITY_REFERENCES = [
  { label: '上海', value: '310100', latitude: 31.2304, longitude: 121.4737 },
  { label: '北京', value: '110100', latitude: 39.9042, longitude: 116.4074 },
  { label: '杭州', value: '330100', latitude: 30.2741, longitude: 120.1551 },
  { label: '福州', value: '350100', latitude: 26.0745, longitude: 119.2965 },
  { label: '广州', value: '440100', latitude: 23.1291, longitude: 113.2644 },
  { label: '深圳', value: '440300', latitude: 22.5431, longitude: 114.0579 },
] as const

export const DATA_SOURCE_LABELS: Record<string, string> = {
  seed: '默认种子',
  manual: '人工维护',
}

export const CONTENT_STATE_TABS = [
  { value: 'stress', label: '压力', description: '先降噪，找回身体和思路的稳定感。' },
  { value: 'sleep', label: '睡眠', description: '从节律、光线和休息环境开始修复。' },
  { value: 'anxiety', label: '焦虑', description: '先识别信号，再决定要不要进一步求助。' },
  { value: 'emotion', label: '情绪', description: '把复杂感受拆开，看见它们的名字。' },
  { value: 'help-seeking', label: '求助', description: '把“找谁、何时找”变成明确入口。' },
  { value: 'communication', label: '沟通', description: '把支持重新接回生活和关系。' },
] as const
