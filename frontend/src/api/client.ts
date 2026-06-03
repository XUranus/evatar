import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

export interface Photo {
  id: number;
  filename: string;
  file_size: number;
  width: number;
  height: number;
  source_type: string;
  device_id: string;
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

export const getPhotos = (page = 1, pageSize = 20, status?: string) =>
  api.get<PaginatedResponse<Photo>>('/photos', { params: { page, page_size: pageSize, status } });

export const getPhoto = (id: number) =>
  api.get<PhotoDetail>(`/photos/${id}`);

export const getPhotoUrl = (id: number) =>
  `/api/photos/${id}/image`;

export const getThumbnailUrl = (id: number) =>
  `/api/photos/${id}/thumbnail`;

export const deletePhoto = (id: number) =>
  api.delete(`/photos/${id}`);

export const reprocessPhoto = (id: number) =>
  api.post(`/analysis/${id}/reprocess`);

export const getStats = () =>
  api.get<Stats>('/stats');

export const getAnalyses = (page = 1, pageSize = 20, status?: string, intent?: string) =>
  api.get<PaginatedResponse<Analysis & { photo_id: number; photo_filename: string }>>('/analysis', {
    params: { page, page_size: pageSize, status, intent },
  });

// LLM Config
export interface LLMConfig {
  provider: string;
  base_url: string;
  api_key: string;
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

export const getLLMConfig = () => api.get<LLMConfig>('/config/llm');

export const updateLLMConfig = (config: Partial<LLMConfig>) =>
  api.put('/config/llm', config);

export const getLLMPresets = () =>
  api.get<{ presets: Record<string, LLMPreset> }>('/config/llm/presets');

export const applyLLMPreset = (name: string, apiKey: string) =>
  api.post(`/config/llm/presets/${name}/apply`, null, { params: { api_key: apiKey } });

// Chat
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

export const sendMessage = (message: string, conversationId?: string) =>
  api.post<{ conversation_id: string; message: ChatMessage }>('/chat/send', {
    message,
    conversation_id: conversationId,
  });

export const getConversations = (deviceId?: string) =>
  api.get<{ conversations: Conversation[] }>('/chat/conversations', {
    params: deviceId ? { device_id: deviceId } : {},
  });

export const getConversation = (id: string) =>
  api.get<{ id: string; title: string; messages: ChatMessage[] }>(`/chat/conversations/${id}`);

export const deleteConversation = (id: string) =>
  api.delete(`/chat/conversations/${id}`);

// Skills
export interface Skill {
  id: string;
  name: string;
  description: string;
  icon: string;
  system_prompt?: string;
}

export const getSkills = () => api.get<{ skills: Skill[] }>('/skills');
export const getSkill = (id: string) => api.get<Skill>(`/skills/${id}`);

export const sendMessageWithSkill = (message: string, conversationId?: string, skillId?: string) =>
  api.post<{ conversation_id: string; message: ChatMessage }>('/chat/send', {
    message,
    conversation_id: conversationId,
    skill_id: skillId,
  });

// MCP Servers
export interface MCPServer {
  id: string;
  name: string;
  url: string;
  description: string;
  enabled: number;
}

export const getMCPServers = () => api.get<{ servers: MCPServer[] }>('/mcp-servers');
export const createMCPServer = (server: Omit<MCPServer, 'enabled'>) => api.post('/mcp-servers', server);
export const deleteMCPServer = (id: string) => api.delete(`/mcp-servers/${id}`);
