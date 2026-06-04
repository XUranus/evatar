import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

// ── Types ──

export interface Photo {
  id: number;
  filename: string;
  file_size: number;
  width: number;
  height: number;
  mime_type: string;
  source_type: string;
  device_id: string;
  device_name: string;
  original_timestamp: string | null;
  created_at: string;
  analysis_status: string;
  intent: string;
  summary: string;
}

export interface PhotoDetail extends Photo {
  original_path: string;
  thumbnail_path: string;
  analysis: Analysis | null;
}

export interface Analysis {
  id: number;
  status: string;
  app_name: string;
  content_category: string;
  intent: string;
  summary: string;
  entities: string;
  confidence: number;
  llm_response: string;
  error_message: string;
  created_at: string;
  completed_at: string;
}

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

export interface PaginatedResponse<T> {
  total: number;
  page: number;
  page_size: number;
  items: T[];
}

export interface ChatMessage {
  id: number;
  role: 'user' | 'assistant' | 'system' | 'tool';
  content: string;
  tool_name?: string;
  tool_calls?: string;
  created_at: string;
}

export interface Conversation {
  id: string;
  title: string;
  device_id: string | null;
  created_at: string;
  updated_at: string;
  message_count: number;
  last_message: string;
}

export interface LLMConfig {
  provider: string;
  base_url: string;
  api_key_set: boolean;
  model: string;
  max_context_tokens: number;
  temperature: number;
}

export interface LLMPreset {
  provider: string;
  base_url: string;
  model: string;
  max_context_tokens: number;
}

export interface Skill {
  id: string;
  name: string;
  description: string;
  icon: string;
  system_prompt?: string;
}

export interface MCPServer {
  id: string;
  name: string;
  url: string;
  description: string;
  enabled: number;
}

// ── Photos ──

export const getPhotos = (page = 1, pageSize = 20, status?: string) =>
  api.get<PaginatedResponse<Photo>>('/photos', { params: { page, page_size: pageSize, status } });

export const getPhoto = (id: number) => api.get<PhotoDetail>(`/photos/${id}`);
export const getPhotoUrl = (id: number) => `/api/photos/${id}/image`;
export const getThumbnailUrl = (id: number) => `/api/photos/${id}/thumbnail`;
export const deletePhoto = (id: number) => api.delete(`/photos/${id}`);
export const reprocessPhoto = (id: number) => api.post(`/analysis/${id}/reprocess`);
export const getStats = () => api.get<Stats>('/stats');

// ── LLM Config ──

export const getLLMConfig = () => api.get<LLMConfig>('/config/llm');
export const updateLLMConfig = (config: Partial<LLMConfig>) => api.put('/config/llm', config);
export const getLLMPresets = () => api.get<{ presets: Record<string, LLMPreset> }>('/config/llm/presets');
export const applyLLMPreset = (name: string, apiKey: string) =>
  api.post(`/config/llm/presets/${name}/apply`, null, { params: { api_key: apiKey } });

// ── Skills ──

export const getSkills = () => api.get<{ skills: Skill[] }>('/skills');
export const getSkill = (id: string) => api.get<Skill>(`/skills/${id}`);

// ── Chat ──

export const getConversations = (deviceId?: string) =>
  api.get<{ conversations: Conversation[] }>('/chat/conversations', {
    params: deviceId ? { device_id: deviceId } : {},
  });

export const getConversation = (id: string) =>
  api.get<{ id: string; title: string; messages: ChatMessage[] }>(`/chat/conversations/${id}`);

export const deleteConversation = (id: string) => api.delete(`/chat/conversations/${id}`);

// ── MCP Servers ──

export const getMCPServers = () => api.get<{ servers: MCPServer[] }>('/mcp-servers');
export const createMCPServer = (server: Omit<MCPServer, 'enabled'>) => api.post('/mcp-servers', server);
export const deleteMCPServer = (id: string) => api.delete(`/mcp-servers/${id}`);

// ── Dynamics ──

export interface DynamicItem {
  id: number;
  title: string;
  summary: string;
  content: string;
  category: string;
  confidence: number;
  is_read: boolean;
  is_pinned: boolean;
  device_id: string | null;
  created_at: string;
}

export const getDynamics = (page = 1, pageSize = 20, category?: string) =>
  api.get<{ total: number; items: DynamicItem[] }>('/dynamics', {
    params: { page, page_size: pageSize, ...(category ? { category } : {}) },
  });

export const getDynamic = (id: number) => api.get<DynamicItem>(`/dynamics/${id}`);
export const markDynamicRead = (id: number) => api.put(`/dynamics/${id}/read`);
export const toggleDynamicPin = (id: number) => api.put(`/dynamics/${id}/pin`);
export const deleteDynamic = (id: number) => api.delete(`/dynamics/${id}`);
export const triggerReasoning = () => api.post('/dynamics/trigger');

// ── Memories ──

export interface MemoryItem {
  id: number;
  content: string;
  memory_type: string;
  source_type: string;
  category: string;
  importance: number;
  access_count: number;
  created_at: string;
  expires_at: string | null;
}

export const getMemories = (page = 1, pageSize = 50, memoryType?: string) =>
  api.get<{ total: number; items: MemoryItem[] }>('/memories', {
    params: { page, page_size: pageSize, ...(memoryType ? { memory_type: memoryType } : {}) },
  });

export const getMemoryStats = () =>
  api.get<{ total: number; short_term: number; long_term: number; categories: Record<string, number> }>('/memories/stats');

// ── Privacy & Data ──

export interface DataStats {
  total_photos: number;
  total_memories: number;
  storage_used_bytes: number;
}

export interface RetentionConfig {
  retention_days: number;
}

export const getDataStats = () => api.get<DataStats>('/data/stats');
export const getRetentionDays = () => api.get<RetentionConfig>('/data/retention');
export const setRetentionDays = (days: number) => api.put('/data/retention', { retention_days: days });
export const clearAllData = () => api.post('/data/clear');
export const exportData = () => api.get('/data/export', { responseType: 'blob' });

// ── Push Notifications ──

export interface PushConfig {
  enabled: boolean;
}

export const getPushConfig = () => api.get<PushConfig>('/push/config');
export const setPushConfig = (enabled: boolean) => api.put('/push/config', { enabled });
export const registerPushToken = (token: string, platform: string) =>
  api.post('/push/register', { token, platform });
export const sendTestNotification = () => api.post('/push/test');

// ── Dynamics extras ──

export const markAllDynamicsRead = () => api.put('/dynamics/read-all');
export const getDynamicsUnreadCount = () => api.get<{ unread_count: number }>('/dynamics/unread-count');

// ── Excluded Apps ──

export const getExcludedApps = () => api.get<{ apps: string[] }>('/config/excluded-apps');
