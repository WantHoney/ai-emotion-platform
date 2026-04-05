# 内容与素材来源清单

核验日期：`2026-04-04`

## 1. 文章封面与链接

| seedKey | 本地文件 | 页面展示 | 来源名称 | 来源 URL | 说明 |
| --- | --- | --- | --- | --- | --- |
| `seed_article_stress_001` | `frontend/public/assets/articles/stress-reset.svg` | 压力重启 | WHO | `https://www.who.int/publications-detail-redirect/9789240003927` | 封面为本地 SVG 设计稿，正文链接使用官方压力自助材料。 |
| `seed_article_sleep_001` | `frontend/public/assets/articles/sleep-rhythm.svg` | 睡眠节律 | 福建省卫健委 | `https://wjw.fj.gov.cn/xxgk/gzdt/wsjsyw/202503/t20250321_6786045.htm` | 封面为本地 SVG，正文链接使用官方睡眠健康科普。 |
| `seed_article_anxiety_001` | `frontend/public/assets/articles/anxiety-signals.svg` | 焦虑信号 | CDC | `https://www.cdc.gov/howrightnow/emotion/worry/index.html` | 封面为本地 SVG，正文链接使用官方焦虑与担忧页面。 |
| `seed_article_emotion_001` | `frontend/public/assets/articles/emotion-toolbox.svg` | 情绪工具箱 | CDC | `https://www.cdc.gov/emotional-well-being/managing-difficult-emotions/index.html` | 封面为本地 SVG，正文链接使用官方情绪管理页面。 |
| `seed_article_help_001` | `frontend/public/assets/articles/help-hotline.svg` | 求助入口 | 国家卫生健康委 | `https://www.nhc.gov.cn/yzygj/c100068/202412/49a1a65386cd4be582d4702fd0926ee8.shtml` | 封面为本地 SVG，正文链接使用心理援助热线通知。 |
| `seed_article_communication_001` | `frontend/public/assets/articles/communication-bridge.svg` | 连接支持 | CDC | `https://www.cdc.gov/mental-health/about-data/community-connection.html` | 封面为本地 SVG，正文链接使用社会连接与心理健康页面。 |

统一兜底图：

| 用途 | 本地文件 | 说明 |
| --- | --- | --- |
| article fallback | `frontend/public/assets/articles/article-fallback.svg` | 文章图片缺失时统一使用。 |
| psy hero | `frontend/public/assets/illustrations/psy-center-hero.png` | 心理中心页面头图，使用本地 Stable Diffusion 3.5 生成并落地为位图。 |
| psy fallback | `frontend/public/assets/illustrations/psy-center-fallback.svg` | 心理中心页面插图兜底。 |

## 2. 书籍封面

| seedKey | 本地文件 | 图书 | 来源名称 | 来源 URL | 说明 |
| --- | --- | --- | --- | --- | --- |
| `seed_book_toad_001` | `frontend/public/assets/books/toad-counseling.jpg` | 蛤蟆先生去看心理医生 | 豆瓣图书封面 | `https://img1.doubanio.com/view/subject/l/public/s33941998.jpg` | 运行时只依赖本地文件。 |
| `seed_book_talk_001` | `frontend/public/assets/books/talk-to-someone.jpg` | 也许你该找个人聊聊 | 豆瓣图书封面 | `https://img3.doubanio.com/view/subject/l/public/s33944153.jpg` | 运行时只依赖本地文件。 |
| `seed_book_courage_001` | `frontend/public/assets/books/courage-to-be-disliked.jpg` | 被讨厌的勇气 | 豆瓣图书封面 | `https://img2.doubanio.com/view/subject/l/public/s34348664.jpg` | 运行时只依赖本地文件。 |
| `seed_book_firstaid_001` | `frontend/public/assets/books/emotional-first-aid.jpg` | 情绪急救 | 豆瓣图书封面 | `https://img1.doubanio.com/view/subject/l/public/s28140853.jpg` | 运行时只依赖本地文件。 |
| `seed_book_nvc_001` | `frontend/public/assets/books/nonviolent-communication.jpg` | 非暴力沟通 | 豆瓣图书封面 | `https://img1.doubanio.com/view/subject/l/public/s33828853.jpg` | 运行时只依赖本地文件。 |
| `seed_book_inferiority_001` | `frontend/public/assets/books/inferiority-and-beyond.jpg` | 自卑与超越 | 豆瓣图书封面 | `https://img3.doubanio.com/view/subject/l/public/s29714854.jpg` | 运行时只依赖本地文件。 |

统一兜底图：

| 用途 | 本地文件 | 说明 |
| --- | --- | --- |
| book fallback | `frontend/public/assets/books/book-fallback.svg` | 书封缺失时统一使用。 |

## 3. 心理中心来源

| seedKey | 机构 | 来源等级 | 来源 URL | 说明 |
| --- | --- | --- | --- | --- |
| `seed_psy_bj_001` | 北京大学第六医院 | `official` | `https://www.pkuh6.cn/yygk/lxwm.htm` | 官方联系方式页。 |
| `seed_psy_bj_002` | 首都医科大学附属北京安定医院 | `official` | `https://bjad.com.cn/Html/Hospitals/Schedulings/OPIndex0_103.html` | 官方挂号 / 联系页。 |
| `seed_psy_sh_001` | 上海市精神卫生中心 | `official` | `https://www.smhc.org.cn/` | 官方主页。 |
| `seed_psy_sh_002` | 虹口区精神卫生中心 | `gov_directory` | `https://www.shhk.gov.cn/bmff/004001/004001004/004001004002/20250705/da150272-c4a2-4021-8b05-e72f525d53f3.html` | 虹口区政务目录页。 |
| `seed_psy_hz_001` | 杭州市第七人民医院 | `official` | `https://www.hz7hospital.com/index/about.html` | 官方医院介绍页。 |
| `seed_psy_hz_002` | 浙江省立同德医院精神卫生科 | `trusted_reference` | `https://hao123.xywy.com/hospital/zjsltdyy/lxfs/` | 可信就医信息页，后续建议继续核对官方科室页。 |
| `seed_psy_fz_001` | 福州市第四医院 | `trusted_reference` | `https://www.114gh.com/yy/fj/3807.html` | 可信挂号页。 |
| `seed_psy_fz_002` | 福建省立医院睡眠与心理支持门诊 | `trusted_reference` | `https://h.bohe.cn/hospital_871/keshi/297056.html` | 可信科室信息页。 |
| `seed_psy_gz_001` | 广州医科大学附属脑科医院 | `trusted_reference` | `https://www.wendaifu.com/hospitals/gzsnkyy/` | 可信就医信息页。 |
| `seed_psy_gz_002` | 广东三九脑科医院 | `official` | `https://wjw.gz.gov.cn/fwcx/yycx/content/post_9514933.html` | 广州市卫健委医院查询页。 |
| `seed_psy_sz_001` | 深圳市康宁医院 | `official` | `https://www.szknyy.com/` | 官方主页。 |
| `seed_psy_sz_002` | 北京大学深圳医院特诊心理科 | `gov_directory` | `https://www.sz.gov.cn/attachment/1/1424/1424869/11185619.pdf` | 深圳市精神卫生医疗机构转诊转介目录。 |

## 4. 种子数据文件

默认内容位于：

- `backend/src/main/resources/seeds/quotes.json`
- `backend/src/main/resources/seeds/articles.json`
- `backend/src/main/resources/seeds/books.json`
- `backend/src/main/resources/seeds/psy_centers.json`

这些文件通过 `seedKey + dataSource + isActive` 进行治理，运行时不会依赖远程图片直链。

补充说明：
- `frontend/public/assets/psy-centers/<seedKey>.svg` 为 2026-04-04 重做后的心理中心本地封面资源，当前覆盖北京、上海、杭州、福州、广州、深圳六城的精神卫生中心、精神专科医院和明确心理专科支持机构。
- `V10__repair_psy_center_seed_data.sql` 会按 `seedKey` 修复已落库的乱码心理中心记录，并移除前一轮误混入的普通综合医院心理门诊数据。
- 心理中心卡片封面当前采用“SD 生成动漫母版 + 本地 SVG 精准文字叠加”的方案，不让模型直接写中文，从而保证机构名称、城市和来源标签可控且准确。

### 4.1 心理中心卡片动漫母版

以下图片由本地 Stable Diffusion 3.5 生成，并作为心理中心卡片封面的动漫母版使用：

| 本地文件 | 来源名称 | 来源 URL | 说明 |
| --- | --- | --- | --- |
| `frontend/public/assets/psy-centers/anime/center-campus.png` | 本地 SD3.5 生成 | `scripts/stable_diffusion_smoke_test.py --generate` | 精神卫生中心类卡片母版。 |
| `frontend/public/assets/psy-centers/anime/hospital-specialty.png` | 本地 SD3.5 生成 | `scripts/stable_diffusion_smoke_test.py --generate` | 精神专科医院 / 脑科专科类卡片母版。 |
| `frontend/public/assets/psy-centers/anime/counsel-room.png` | 本地 SD3.5 生成 | `scripts/stable_diffusion_smoke_test.py --generate` | 心理服务 / 诊疗中心类卡片母版。 |
| `frontend/public/assets/psy-centers/anime/recovery-garden.png` | 本地 SD3.5 生成 | `scripts/stable_diffusion_smoke_test.py --generate` | 康复支持 / 疗养院类卡片母版。 |
| `frontend/public/assets/psy-centers/anime/brain-clinic.png` | 本地 SD3.5 生成 | `scripts/stable_diffusion_smoke_test.py --generate` | 脑科专科补充母版，目前保留在素材库中。 |

说明：
- 当前 27 张机构封面都由 `scripts/generate_psy_center_posters.py` 基于上述动漫母版统一生成。
- 卡片中的机构名称、城市、来源标签由本地 SVG 精准叠加，不依赖模型直接写中文。

### 4.2 心理中心首页图生成记录

以下首页图由本地 Stable Diffusion 3.5 独立脚本生成，并已落地到前端资源目录：

| 本地文件 | 生成入口 | 模型 | 说明 |
| --- | --- | --- | --- |
| `frontend/public/assets/illustrations/psy-center-hero.png` | `scripts/stable_diffusion_smoke_test.py --generate` | `stabilityai/stable-diffusion-3.5-medium` | 心理中心首页 hero 图，当前页面正式使用版本。 |

提示词摘要：
- `A premium mental health support center reception and counseling lounge in a Chinese city, calm and trustworthy space, curved seating, warm indirect lighting, subtle blue green accents, natural wood, indoor plants, private consultation atmosphere, no people, no readable text, photorealistic interior design photography, wide composition`

负向提示词摘要：
- `people, crowd, hospital ward, surgery, emergency room, readable text, letters, watermark, logo, blur, low resolution, clutter, deformed furniture`
