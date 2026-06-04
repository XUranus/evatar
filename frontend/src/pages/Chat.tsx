import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Markdown from 'react-markdown';
import axios from 'axios';
import { Paperclip, Bot, Wrench, Zap, X } from 'lucide-react';
import {
  getConversations, getConversation, deleteConversation, getSkills,
  type ChatMessage, type Conversation, type Skill,
} from '../api/client';

export default function ChatPage() {
  const { t } = useTranslation();
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConvId, setActiveConvId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [skills, setSkills] = useState<Skill[]>([]);
  const [activeSkill, setActiveSkill] = useState<Skill | null>(null);
  const [showSidebar, setShowSidebar] = useState(true);
  const [, setLastError] = useState<string | null>(null);
  const [lastFailedInput, setLastFailedInput] = useState<string | null>(null);
  const [attachedFile, setAttachedFile] = useState<File | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    getConversations().then(r => setConversations(r.data.conversations));
    getSkills().then(r => setSkills(r.data.skills));
  }, []);

  useEffect(() => {
    if (activeConvId) getConversation(activeConvId).then(r => setMessages(r.data.messages));
    else setMessages([]);
  }, [activeConvId]);

  useEffect(() => { messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  const doSend = async (text: string, file?: File | null) => {
    setSending(true);
    setLastError(null);
    setLastFailedInput(null);

    const userMsg: ChatMessage = { id: Date.now(), role: 'user', content: text + (file ? ` [attachment] ${file.name}` : ''), created_at: new Date().toISOString() };
    setMessages(prev => [...prev, userMsg]);

    try {
      const formData = new FormData();
      formData.append('message', text);
      if (activeConvId) formData.append('conversation_id', activeConvId);
      if (activeSkill?.id) formData.append('skill_id', activeSkill.id);
      if (file) formData.append('file', file);

      const res = await axios.post('/api/chat/send-with-file', formData, { timeout: 180000 });
      const convId = res.data.conversation_id;
      setActiveConvId(convId);
      setMessages(prev => [...prev, res.data.message as ChatMessage]);
      setActiveSkill(null);
      getConversations().then(r => setConversations(r.data.conversations));
    } catch (err: unknown) {
      let detail = t('chat.unknown_error', 'Unknown error');
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        const body = err.response?.data?.detail || err.message;
        detail = `HTTP ${status || 'N/A'}: ${body}`;
      } else if (err instanceof Error) {
        detail = err.message;
      }
      setLastError(detail);
      setLastFailedInput(text);
      setMessages(prev => [...prev, { id: Date.now() + 1, role: 'assistant', content: `Error: ${t('chat.send_failed', 'Send failed')}\n${detail}`, created_at: new Date().toISOString() }]);
    } finally {
      setSending(false);
    }
  };

  const handleSend = () => {
    const text = input.trim();
    if ((!text && !attachedFile) || sending) return;
    doSend(text || t('chat.sent_attachment'), attachedFile);
    setInput('');
    setAttachedFile(null);
  };

  const handleRetry = () => {
    if (lastFailedInput) doSend(lastFailedInput);
  };

  return (
    <div className="flex h-[calc(100vh-3rem)] bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-100 dark:border-gray-800 overflow-hidden">
      {/* Sidebar */}
      {showSidebar && (
        <div className="w-64 border-r border-gray-200 dark:border-gray-800 flex flex-col flex-shrink-0 bg-gray-50 dark:bg-gray-950">
          <div className="p-3 border-b border-gray-200 dark:border-gray-800">
            <button onClick={() => { setActiveConvId(null); setMessages([]); setActiveSkill(null); }}
              className="w-full px-3 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 text-sm">
              + {t('chat.new_chat', '新对话')}
            </button>
          </div>
          <div className="flex-1 overflow-y-auto">
            {conversations.map(conv => (
              <div key={conv.id}
                className={`group flex items-center px-3 py-2.5 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-800 ${activeConvId === conv.id ? 'bg-blue-50 dark:bg-blue-900/30' : ''}`}
                onClick={() => { setActiveConvId(conv.id); setActiveSkill(null); }}>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium text-gray-700 dark:text-gray-300 truncate">{conv.title}</div>
                  <div className="text-xs text-gray-400 truncate">{conv.last_message}</div>
                </div>
                <button onClick={(e) => {
                  e.stopPropagation();
                  deleteConversation(conv.id).then(() => {
                    setConversations(prev => prev.filter(c => c.id !== conv.id));
                    if (activeConvId === conv.id) { setActiveConvId(null); setMessages([]); }
                  });
                }} className="ml-2 text-gray-300 hover:text-red-500 opacity-0 group-hover:opacity-100"><X size={14} /></button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Chat area */}
      <div className="flex-1 flex flex-col">
        <div className="flex items-center px-4 py-2 border-b border-gray-200 dark:border-gray-800">
          <button onClick={() => setShowSidebar(!showSidebar)} className="mr-3 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">☰</button>
          <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-300 flex-1">
            {activeSkill ? `${activeSkill.icon} ${activeSkill.name}` : activeConvId ? conversations.find(c => c.id === activeConvId)?.title || t('chat.title', 'AI Assistant') : t('chat.new_chat', 'New Chat')}
          </h2>
          {activeSkill && <button onClick={() => setActiveSkill(null)} className="text-xs text-gray-400 hover:text-gray-600 flex items-center gap-1"><X size={14} /> {t('chat.cancel_skill', 'Cancel skill')}</button>}
        </div>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {messages.length === 0 && (
            <div className="flex flex-col items-center justify-center h-full text-gray-400">
              <div className="mb-4 text-gray-400"><Bot size={40} /></div>
              <div className="text-lg font-medium dark:text-gray-300">{t('chat.welcome', 'Evatar AI Assistant')}</div>
              <div className="text-sm mt-2 text-center max-w-md">{t('chat.welcome_desc', 'Search screenshots, search internet, upload images')}</div>
              {skills.length > 0 && (
                <div className="mt-6 w-full max-w-lg">
                  <div className="text-xs text-gray-400 mb-2 text-left flex items-center gap-1"><Zap size={12} /> {t('chat.skills', 'Skills')}</div>
                  <div className="grid grid-cols-2 gap-2">
                    {skills.map(skill => (
                      <button key={skill.id} onClick={() => setActiveSkill(skill)}
                        className="flex items-start gap-2 p-3 bg-gray-50 dark:bg-gray-800 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-left">
                        <span className="text-xl">{skill.icon}</span>
                        <div><div className="text-sm font-medium text-gray-700 dark:text-gray-300">{skill.name}</div><div className="text-xs text-gray-400">{skill.description}</div></div>
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {messages.map((msg) => <MessageBubble key={msg.id} message={msg} />)}

          {sending && (
            <div className="flex justify-start">
              <div className="bg-gray-100 dark:bg-gray-800 rounded-2xl rounded-tl-sm px-4 py-3">
                <div className="flex space-x-1">
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                </div>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Retry bar */}
        {lastFailedInput && !sending && (
          <div className="mx-4 mb-2 px-3 py-2 bg-red-50 dark:bg-red-900/30 rounded-lg flex items-center text-sm">
            <span className="text-red-600 dark:text-red-400 flex-1">{t('chat.send_failed', 'Send failed')}</span>
            <button onClick={handleRetry} className="px-3 py-1 bg-red-500 text-white rounded text-xs hover:bg-red-600">{t('chat.retry', 'Retry')}</button>
            <button onClick={() => { setLastFailedInput(null); setLastError(null); }} className="ml-2 px-3 py-1 text-gray-500 text-xs">{t('chat.cancel', 'Cancel')}</button>
          </div>
        )}

        {/* Attached file preview */}
        {attachedFile && (
          <div className="mx-4 mb-2 px-3 py-2 bg-blue-50 dark:bg-blue-900/30 rounded-lg flex items-center text-sm">
            <span className="text-blue-600 dark:text-blue-400 flex-1 flex items-center gap-1"><Paperclip size={14} /> {attachedFile.name} ({(attachedFile.size / 1024).toFixed(0)}KB)</span>
            <button onClick={() => setAttachedFile(null)} className="text-gray-400 hover:text-gray-600"><X size={14} /></button>
          </div>
        )}

        {/* Input */}
        <div className="border-t border-gray-200 dark:border-gray-800 p-4">
          {activeSkill && (
            <div className="mb-2 px-3 py-1.5 bg-blue-50 dark:bg-blue-900/30 rounded-lg text-xs text-blue-600 dark:text-blue-400 inline-block">
              {activeSkill.icon} {t('chat.skill_label', 'Skill')}: {activeSkill.name}
            </div>
          )}
          <div className="flex gap-2">
            <input type="file" ref={fileInputRef} className="hidden" accept="image/*,.pdf,.txt,.csv,.json" onChange={e => setAttachedFile(e.target.files?.[0] || null)} />
            <button onClick={() => fileInputRef.current?.click()}
              className="px-2 py-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300" title={t('chat.upload_attachment', 'Upload attachment')}><Paperclip size={16} /></button>
            <textarea value={input} onChange={e => setInput(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); } }}
              placeholder={activeSkill ? t('chat.skill_placeholder', { name: activeSkill.name, defaultValue: `Using "${activeSkill.name}"...` }) : t('chat.placeholder', 'Type a message...')}
              rows={1}
              className="flex-1 px-4 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-xl resize-none focus:ring-2 focus:ring-blue-500 focus:border-transparent text-sm"
            />
            <button onClick={handleSend} disabled={(!input.trim() && !attachedFile) || sending}
              className="px-4 py-2 bg-blue-500 text-white rounded-xl hover:bg-blue-600 disabled:opacity-40 text-sm">
              {t('chat.send', 'Send')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function MessageBubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === 'user';
  const isTool = message.role === 'tool';

  if (isTool) {
    let toolData: { tool?: string; results?: Record<string, unknown>[] } = {};
    try { toolData = JSON.parse(message.content); } catch { /* */ }
    return (
      <details className="mx-auto max-w-2xl">
        <summary className="text-xs text-gray-400 cursor-pointer hover:text-gray-600 flex items-center gap-1">
          <Wrench size={12} /> {message.tool_name || 'tool'} {toolData.results ? `(${toolData.results.length})` : ''}
        </summary>
        <div className="mt-1 bg-gray-50 dark:bg-gray-800 rounded-lg p-3 text-xs text-gray-600 dark:text-gray-400 font-mono max-h-40 overflow-y-auto">
          {((toolData.results || []) as { title?: string; snippet?: string; summary?: string }[]).map((r, i) => (
            <div key={i} className="mb-1"><strong>{r.title || r.summary || ''}</strong>{r.snippet && <div className="text-gray-400">{String(r.snippet).slice(0, 100)}</div>}</div>
          ))}
        </div>
      </details>
    );
  }

  let toolCalls: { function: { name: string } }[] = [];
  if (message.tool_calls) { try { toolCalls = JSON.parse(message.tool_calls); } catch { /* */ } }

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div className={`max-w-[80%] rounded-2xl px-4 py-3 ${
        isUser
          ? 'bg-blue-500 text-white rounded-tr-sm'
          : 'bg-gray-100 dark:bg-gray-800 text-gray-800 dark:text-gray-200 rounded-tl-sm'
      }`}>
        {toolCalls.length > 0 && (
          <div className="mb-2 flex flex-wrap gap-1">
            {toolCalls.map((tc, i) => (
              <span key={i} className="inline-flex items-center gap-1 px-2 py-0.5 bg-white/20 rounded text-xs"><Wrench size={12} /> {tc.function.name}</span>
            ))}
          </div>
        )}
        {isUser ? (
          <div className="text-sm whitespace-pre-wrap leading-relaxed">{message.content}</div>
        ) : (
          <div className="text-sm leading-relaxed chat-markdown">
            <Markdown>{message.content}</Markdown>
          </div>
        )}
      </div>
    </div>
  );
}
