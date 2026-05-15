# 情绪识别模型服务（FastAPI）

本目录是后端调用的本地模型服务，主要负责音频分析链路中的模型推理部分。

## 1. 服务职责

- 语音转文字：基于 faster-whisper 完成 ASR 转写。
- 语音情绪识别：基于 wav2vec2 相关模型识别音频情绪。
- 文本情感分析：对转写文本进行中文或英文情感倾向判断。
- 多模态融合：结合声音特征和文本线索，输出综合风险判断。
- 模型服务接口：向 Spring Boot 后端提供 HTTP 接口。

## 2. 本地启动

```bash
cd backend/ser-service
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8001
```

启动后访问：

```bash
curl http://127.0.0.1:8001/health
```

## 3. 主要接口

- `POST /ser/analyze`
  - 表单参数：`file=@audio.wav`
  - 可选参数：`segment_ms`、`overlap_ms`、`language_hint`
  - 返回：语音情绪摘要、文本特征和融合结果。
- `POST /asr/transcribe`
  - 表单参数：`file=@audio.wav`
  - 支持格式：`wav`、`mp3`、`m4a`、`flac`、`ogg`、`webm`
  - 返回：转写文本、语言、分段和元数据。
- `POST /text/sentiment`
  - JSON 参数：`{"text":"...", "language":"zh|en"}`
  - 返回：负向分数和分类概率。

## 4. 当前工程默认链路

当前演示环境采用中文优先的本地推理链路：

- 语音模型：`ser_multilingual_xlsr_stageB_exp04_fast`
- 英文语音模型：`ser_multilingual_4class_exp02`
- 中文文本模型：`zh_sentiment_exp03`
- 融合模型：`fusion_exp04_gated`
- ASR：`faster-whisper`

说明：

- 中文请求默认走中文音频模型和中文文本模型。
- 当文本特征不足时，融合模型会跳过不支持的输入，避免生成不稳定结果。
- 本地模型是主链路，外部大模型只承担可配置增强或兜底角色。

## 5. 常用环境变量

- `SER_ENGINE`：语音情绪识别引擎，默认 `hf_wav2vec2`。
- `SER_HF_ROUTING`：模型路由方式，默认 `language`。
- `SER_HF_MODEL_DIR_EN`：英文语音模型目录。
- `SER_HF_MODEL_DIR_ZH`：中文语音模型目录。
- `SER_HF_DEFAULT_LANGUAGE`：默认语言，建议为 `zh`。
- `SER_HF_DEVICE`：推理设备，可设为 `cpu`、`cuda` 或 `auto`。
- `TEXT_ENGINE`：文本情感引擎，默认 `hf`。
- `TEXT_HF_MODEL_ZH`：中文文本模型目录。
- `TEXT_HF_DEVICE`：文本模型推理设备。
- `FUSION_ENABLED`：是否启用多模态融合，默认 `true`。
- `FUSION_MODEL_DIR`：融合模型目录。
- `FUSION_DEVICE`：融合模型推理设备。
- `WHISPER_MODEL`：Whisper 模型规格，默认 `small`。
- `WHISPER_DEVICE`：ASR 推理设备。
- `MAX_ASR_DURATION_MS`：单次转写最大音频时长。

## 6. 配置示例

```bash
export SER_ENGINE=hf_wav2vec2
export SER_HF_ROUTING=language
export SER_HF_MODEL_DIR_EN=./training/checkpoints/ser_multilingual_4class_exp02/best_model
export SER_HF_MODEL_DIR_ZH=./training/checkpoints/ser_multilingual_xlsr_stageB_exp04_fast/best_model
export SER_HF_DEFAULT_LANGUAGE=zh
export SER_HF_DEVICE=cuda
export TEXT_ENGINE=hf
export TEXT_HF_ROUTING=language
export TEXT_HF_MODEL_ZH=./training/text_models/zh_sentiment_exp03/best_model
export TEXT_HF_DEVICE=cuda
export FUSION_ENABLED=true
export FUSION_MODEL_DIR=./training/fusion/models/fusion_exp04_gated
export FUSION_DEVICE=cuda
```

## 7. 训练与清单

常用训练脚本：

- `training/build_manifest.py`
- `training/train_wav2vec2_cls.py`
- `training/evaluate_wav2vec2_cls.py`
- `training/build_bias_repair_manifest.py`
- `training/adapt_wav2vec2_head.py`

数据清单目录：

- `training/manifests/casia_4class/`
- `training/manifests/esd_4class/`
- `training/manifests/iemocap_4class/`
- `training/manifests/ravdess_4class/`

文本模型下载脚本：

```bash
python training/download_text_models.py --output-dir text_models
```
