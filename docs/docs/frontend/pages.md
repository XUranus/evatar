---
sidebar_position: 2
title: 页面说明
description: Web 前端各页面功能详细说明
---

# 页面说明

前端采用单页应用 (SPA) 架构，主要包含以下 5 个页面：仪表盘、照片、聊天、动态和设置。

---

## 仪表盘 (Dashboard)

### 功能说明

仪表盘是应用的首页，展示系统的全局统计数据和数据分布概览。

### 数据获取

调用 `GET /api/stats` 获取统计数据：

```typescript
export const getStats = () => api.get<Stats>('/stats');

export interface Stats {
  total_photos: number;
  total_analyses: number;
  done: number;
  pending: number;
  processing: number;
  errors: number;
  intent_distribution: Record<string, number>;
  category_distribution: Record<string, number>;
}
```

### 页面内容

```
┌─────────────────────────────────────────────────┐
│  仪表盘                                          │
│                                                   │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐   │
│  │ 总照片  │ │ 已分析  │ │ 待处理  │ │ 错误    │   │
│  │  150   │ │  140   │ │   5    │ │   3    │   │
│  └────────┘ └────────┘ └────────┘ └────────┘   │
│                                                   │
│  ┌─────────────────────┐ ┌──────────────────┐   │
│  │  意图分布            │ │  内容分类分布      │   │
│  │  reference: 60      │ │  chat: 50        │   │
│  │  reminder: 20       │ │  webpage: 30     │   │
│  │  research: 30       │ │  finance: 20     │   │
│  │  note: 20           │ │  notification:15 │   │
│  └─────────────────────┘ └──────────────────┘   │
└─────────────────────────────────────────────────┘
```

### 关键组件

- **统计卡片**：展示总照片数、已分析、待处理、错误数量，使用 `stat-number` 样式类
- **意图分布图表**：展示 `intent_distribution` 数据
- **分类分布图表**：展示 `category_distribution` 数据

### 设计要点

- 统计数字使用 Display 字体 (`Instrument Serif`)
- 不同指标使用不同颜色：amber (总数)、teal (已完成)、coral (错误)
- 卡片带有 hover 动画效果

---

## 照片 (Photos)

### 功能说明

照片页面展示已同步的截图列表，支持按分析状态筛选、查看详情、触发重新分析和删除操作。

### 数据获取

列表数据通过分页 API 获取：

```typescript
export const getPhotos = (page = 1, pageSize = 20, status?: string) =>
  api.get<PaginatedResponse<Photo>>('/photos', { params: { page, page_size: pageSize, status } });

export const getPhoto = (id: number) => api.get<PhotoDetail>(`/photos/${id}`);
export const deletePhoto = (id: number) => api.delete(`/photos/${id}`);
export const reprocessPhoto = (id: number) => api.post(`/analysis/${id}/reprocess`);
```

### 页面内容

```
┌─────────────────────────────────────────────────┐
│  照片列表                            [上一页][下一页] │
│                                                   │
│  [全部] [待处理] [分析中] [已完成] [错误]           │
│                                                   │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐            │
│  │ 缩略图   │ │ 缩略图   │ │ 缩略图   │            │
│  │ 微信     │ │ 支付宝   │ │ 12306   │            │
│  │ chat     │ │ finance │ │ tool    │            │
│  │ ✓ done  │ │ ✓ done  │ │ ⏳ pend │            │
│  └─────────┘ └─────────┘ └─────────┘            │
│                                                   │
│  ┌─ 照片详情弹窗 ─────────────────────────┐      │
│  │  [原图预览]                              │      │
│  │  应用: 微信                              │      │
│  │  分类: chat                              │      │
│  │  意图: reference                         │      │
│  │  置信度: 0.9                             │      │
│  │  摘要: 微信聊天记录，关于会议安排          │      │
│  │  实体: 张三, 明天下午2点                  │      │
│  │  [重新分析] [删除] [关闭]                │      │
│  └─────────────────────────────────────────┘      │
└─────────────────────────────────────────────────┘
```

### 关键模式

```typescript
// 缩略图 URL 直接使用后端端点
export const getThumbnailUrl = (id: number) => `/api/photos/${id}/thumbnail`;
export const getPhotoUrl = (id: number) => `/api/photos/${id}/image`;

// 状态筛选 Tab
const statusTabs = [
  { key: undefined, label: t('photos.all') },
  { key: 'pending', label: t('photos.pending') },
  { key: 'processing', label: t('photos.processing') },
  { key: 'done', label: t('photos.done') },
  { key: 'error', label: t('photos.error') },
];
```

### 交互功能

- **状态筛选**：点击 Tab 切换筛选条件，重新加载列表
- **分页**：上一页/下一页按钮
- **详情弹窗**：点击缩略图查看完整分析结果
- **重新分析**：调用 `POST /api/analysis/{photo_id}/reprocess`
- **删除**：二次确认后调用 `DELETE /api/photos/{photo_id}`
- **图片预览**：通过 `/api/photos/{id}/image` 加载原图

---

## AI 助手 (Chat)

### 功能说明

聊天页面提供 AI 对话界面，支持文本消息、图片附件、技能选择和 Markdown 渲染。

### 数据获取

```typescript
// 获取对话列表
export const getConversations = (deviceId?: string) =>
  api.get<{ conversations: Conversation[] }>('/chat/conversations', { ... });

// 获取对话详情（含消息）
export const getConversation = (id: string) =>
  api.get<{ id: string; title: string; messages: ChatMessage[] }>(`/chat/conversations/${id}`);

// 发送消息（通过 fetch 或 axios 直接调用）
// POST /api/chat/send { message, conversation_id, skill_id }
// POST /api/chat/send-with-file (multipart/form-data)
```

### 页面内容

```
┌──────────────────────────────────────────────────────┐
│  AI 助手                                              │
│                                                        │
│  ┌─ 侧边栏 ──┐  ┌─ 对话区域 ──────────────────────┐ │
│  │ [+ 新对话] │  │                                    │ │
│  │            │  │  👤 帮我搜索最近的火车票截图        │ │
│  │ 对话 1     │  │                                    │ │
│  │ 对话 2     │  │  🔍 [search_knowledge: "火车票"]   │ │
│  │ 对话 3     │  │                                    │ │
│  │            │  │  🤖 根据你的截图记录，找到了以下    │ │
│  │            │  │  火车票信息：                       │ │
│  │            │  │  | 日期 | 车次 | 出发-到达 |       │ │
│  │            │  │  | 01-15 | G1234 | 北京-上海 |    │ │
│  │            │  │                                    │ │
│  │            │  │  ┌──────────────────────────────┐ │ │
│  │            │  │  │ [📝 总结] [⏰ 提醒] [🔬 研究] │ │ │
│  │            │  │  ├──────────────────────────────┤ │ │
│  │            │  │  │ 输入消息...        [📎] [➤] │ │ │
│  │            │  │  └──────────────────────────────┘ │ │
│  └────────────┘  └────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
```

### 关键模式

```typescript
// 技能选择
export const getSkills = () => api.get<{ skills: Skill[] }>('/skills');

// 发送消息时可指定 skill_id
interface SendMessageRequest {
  message: string;
  conversation_id?: string;
  device_id?: string;
  skill_id?: string;  // 可选：使用特定技能
}

// 图片附件通过 multipart/form-data 发送
const formData = new FormData();
formData.append('message', message);
formData.append('file', file);
formData.append('conversation_id', conversationId);
```

### 交互功能

- **对话管理**：左侧栏显示对话列表，点击切换，支持删除
- **新对话**：点击"新对话"创建空白对话
- **消息发送**：输入框支持回车发送，支持图片附件上传
- **技能选择**：点击技能按钮自动填充系统提示词
- **工具调用展示**：显示 Agent 的工具调用过程（search_knowledge、web_search 等）
- **Markdown 渲染**：使用 `react-markdown` 渲染助手回复中的表格、列表、代码块
- **对话标题**：自动使用第一条用户消息作为对话标题

---

## 动态 (Dynamics)

### 动态页面提供后台意图推理生成的文章/笔记浏览，支持游标分页、分类筛选、已读标记和置顶功能。

### 数据获取

```typescript
// 游标分页加载
export const getDynamics = (cursor = 0, limit = 30, category?: string) =>
  api.get<{ items: DynamicItem[]; next_cursor: number | null; has_more: boolean }>('/dynamics', {
    params: { cursor, limit, ...(category ? { category } : {}) },
  });

// 获取详情
export const getDynamic = (id: number) => api.get<DynamicItem>(`/dynamics/${id}`);

// 操作
export const markDynamicRead = (id: number) => api.put(`/dynamics/${id}/read`);
export const toggleDynamicPin = (id: number) => api.put(`/dynamics/${id}/pin`);
export const deleteDynamic = (id: number) => api.delete(`/dynamics/${id}`);
export const triggerReasoning = () => api.post('/dynamics/trigger');
```

### 页面内容

```
┌──────────────────────────────────────────────────────┐
│  动态                           [全部已读] [生成]      │
│                                                        │
│  [全部] [洞察] [提醒] [报告] [笔记]                   │
│                                                        │
│  ┌────────────────────────────────────────────────┐  │
│  │ 📌 近期出行计划整理                    [insight] │  │
│  │    根据截图分析，你近期有多个出行相关的安排        │  │
│  │    2024-01-15 12:00                    [置顶][删除]│  │
│  └────────────────────────────────────────────────┘  │
│                                                        │
│  ┌────────────────────────────────────────────────┐  │
│  │ ⏰ 提醒事项汇总                       [reminder]│  │
│  │    从截图中提取了 5 个需要关注的时间节点          │  │
│  │    2024-01-15 10:00                    [置顶][删除]│  │
│  └────────────────────────────────────────────────┘  │
│                                                        │
│  ┌─ 文章详情弹窗 ────────────────────────────────┐  │
│  │  # 近期出行计划整理                              │  │
│  │                                                  │  │
│  │  根据你的截图记录，整理了以下出行安排：           │  │
│  │  - 1月20日：北京→上海 (G1234)                   │  │
│  │  - 1月25日：上海→杭州 (D5678)                   │  │
│  │                                                  │  │
│  │  [关闭]                                          │  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

### 关键模式

```typescript
// 游标分页：首次 cursor=0，后续使用 next_cursor
const loadMore = async () => {
  const res = await getDynamics(nextCursor, 30, category);
  setItems(prev => [...prev, ...res.data.items]);
  setNextCursor(res.data.next_cursor);
  setHasMore(res.data.has_more);
};

// 无限滚动：滚动到底部自动加载
// 使用 IntersectionObserver 或 onScroll 事件

// 分类筛选
const categories = [
  { key: undefined, label: t('dynamic.all') },
  { key: 'insight', label: t('dynamic.categories.insight') },
  { key: 'reminder', label: t('dynamic.categories.reminder') },
  { key: 'report', label: t('dynamic.categories.report') },
  { key: 'note', label: t('dynamic.categories.note') },
];
```

### 交互功能

- **游标分页**：使用 `cursor` 参数实现高效分页，适合无限滚动
- **分类筛选**：按 insight/reminder/report/note 筛选
- **展开阅读**：点击卡片调用 `GET /api/dynamics/{id}` 获取完整 Markdown 内容，同时自动标记已读
- **置顶/取消置顶**：调用 `PUT /api/dynamics/{id}/pin`，置顶项始终排在最前
- **全部已读**：调用 `PUT /api/dynamics/read-all`
- **手动生成**：调用 `POST /api/dynamics/trigger` 触发意图推理
- **删除**：二次确认后删除

---

## 设置 (Settings)

### 功能说明

设置页面集中管理 LLM 配置、MCP 服务器、设备管理和系统信息。

### 数据获取

```typescript
// LLM 配置
export const getLLMConfig = () => api.get<LLMConfig>('/config/llm');
export const updateLLMConfig = (config: Partial<LLMConfig>) => api.put('/config/llm', config);
export const getLLMPresets = () => api.get<{ presets: Record<string, LLMPreset> }>('/config/llm/presets');
export const applyLLMPreset = (name: string, apiKey: string) =>
  api.post(`/config/llm/presets/${name}/apply`, { api_key: apiKey });

// MCP 服务器
export const getMCPServers = () => api.get<{ servers: MCPServer[] }>('/mcp-servers');
export const createMCPServer = (server) => api.post('/mcp-servers', server);
export const deleteMCPServer = (id: string) => api.delete(`/mcp-servers/${id}`);

// 设备
export const getDevices = () => api.get<{ devices: Device[] }>('/push/devices');
export const removeDevice = (deviceId: string) => api.delete(`/push/devices/${deviceId}`);
export const sendTestPush = (deviceId: string) => api.post('/push/test', { device_id: deviceId });
```

### 页面内容

```
┌──────────────────────────────────────────────────────┐
│  设置                                                  │
│                                                        │
│  [通用] [LLM 设置] [MCP 服务器] [设备管理] [高级]     │
│                                                        │
│  ┌─ LLM 设置 ──────────────────────────────────────┐ │
│  │                                                    │ │
│  │  快速选择:                                         │ │
│  │  [小米 MiMo] [通义千问] [OpenAI] [Claude]        │ │
│  │  [智谱 GLM] [Kimi] [DeepSeek]                    │ │
│  │                                                    │ │
│  │  当前配置:                                         │ │
│  │  服务商: mimo                                      │ │
│  │  API 地址: https://token-plan-cn.xiaomimimo.com/v1│ │
│  │  模型: mimo-v2.5                                   │ │
│  │  API Key: ✅ 已设置                                │ │
│  │  温度: 0.1                                         │ │
│  │  最大上下文: 1048576                               │ │
│  │                                                    │ │
│  │  [保存配置]                                        │ │
│  └────────────────────────────────────────────────────┘ │
│                                                        │
│  ┌─ MCP 服务器 ────────────────────────────────────┐ │
│  │  Model Context Protocol — 连接外部工具服务器       │ │
│  │                                                    │ │
│  │  [名称] [URL] [描述]  [添加]                     │ │
│  │                                                    │ │
│  │  天气服务  https://weather-mcp.example.com  [删除]│ │
│  └────────────────────────────────────────────────────┘ │
│                                                        │
│  ┌─ 设备管理 ──────────────────────────────────────┐ │
│  │  已连接并可接收推送通知的设备                       │ │
│  │                                                    │ │
│  │  Xiaomi 14  android  v1.0.0  最后在线: 10:30     │ │
│  │            [测试推送] [移除]                       │ │
│  └────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
```

### Tab 结构

设置页面使用 Tab 分组：

| Tab | 内容 |
|-----|------|
| **通用** | 应用信息、版本、语言说明 |
| **LLM 设置** | 预设方案选择、API Key 配置、模型参数 |
| **MCP 服务器** | 添加/删除 MCP 服务器 |
| **设备管理** | 已注册设备列表、测试推送、移除设备 |
| **高级** | 数据保留天数、清除数据、导出数据 |

### LLM 配置模式

```typescript
// 一键应用预设
const handleApplyPreset = async (presetName: string) => {
  await applyLLMPreset(presetName, apiKey);
  // 重新加载配置
  const res = await getLLMConfig();
  setConfig(res.data);
};

// 手动配置
const handleSave = async () => {
  await updateLLMConfig({
    provider: 'custom',
    base_url: 'https://api.example.com/v1',
    api_key: 'sk-xxx',
    model: 'my-model',
    temperature: 0.2,
  });
};
```

### 交互功能

- **预设方案一键切换**：点击预设按钮自动填充 base_url、model 等字段
- **API Key 安全显示**：仅显示是否已设置 (`api_key_set`)，不显示实际值
- **MCP 服务器管理**：添加时验证 URL（SSRF 防护），支持删除
- **设备管理**：查看已注册设备、发送测试推送、移除设备
- **数据管理**：查看存储统计、设置保留天数、清除数据
