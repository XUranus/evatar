import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getLLMConfig, updateLLMConfig, getLLMPresets, applyLLMPreset,
  getMCPServers, createMCPServer, deleteMCPServer,
  type LLMConfig as LLMConfigType, type LLMPreset, type MCPServer,
} from '../api/client';

type SettingsTab = 'general' | 'llm' | 'mcp' | 'advanced';

const PROVIDER_COLORS: Record<string, string> = {
  mimo: 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400 border-orange-300 dark:border-orange-700',
  qwen: 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400 border-purple-300 dark:border-purple-700',
  openai: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 border-green-300 dark:border-green-700',
  claude: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400 border-amber-300 dark:border-amber-700',
  glm: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 border-blue-300 dark:border-blue-700',
  kimi: 'bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-400 border-indigo-300 dark:border-indigo-700',
  deepseek: 'bg-cyan-100 dark:bg-cyan-900/30 text-cyan-700 dark:text-cyan-400 border-cyan-300 dark:border-cyan-700',
};

const tabItems: { key: SettingsTab; icon: string; label: string }[] = [
  { key: 'general', icon: '⚙️', label: '通用' },
  { key: 'llm', icon: '🤖', label: 'LLM 设置' },
  { key: 'mcp', icon: '🔌', label: 'MCP 服务器' },
  { key: 'advanced', icon: '🛠️', label: '高级' },
];

export default function SettingsPage() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<SettingsTab>('general');

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-800 dark:text-gray-100">{t('settings.title', '设置')}</h1>

      {/* Tab bar */}
      <div className="flex gap-1 p-1 bg-gray-100 dark:bg-gray-800 rounded-xl">
        {tabItems.map(item => (
          <button
            key={item.key}
            onClick={() => setTab(item.key)}
            className={`flex-1 flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors ${
              tab === item.key
                ? 'bg-white dark:bg-gray-700 shadow-sm font-medium text-gray-800 dark:text-gray-100'
                : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
            }`}
          >
            <span>{item.icon}</span>
            <span className="hidden sm:inline">{item.label}</span>
          </button>
        ))}
      </div>

      {tab === 'general' && <GeneralSettings />}
      {tab === 'llm' && <LLMSettings t={t} />}
      {tab === 'mcp' && <MCPSettings />}
      {tab === 'advanced' && <AdvancedSettings />}
    </div>
  );
}

function Card({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <div className={`bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-100 dark:border-gray-800 p-5 ${className}`}>{children}</div>;
}

function GeneralSettings() {
  return (
    <div className="space-y-4">
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">应用信息</h2>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div><span className="text-gray-400">版本</span><div className="font-medium dark:text-gray-200">0.1.0</div></div>
          <div><span className="text-gray-400">后端</span><div className="font-medium dark:text-gray-200">FastAPI + SQLite</div></div>
          <div><span className="text-gray-400">前端</span><div className="font-medium dark:text-gray-200">React + Vite + Tailwind</div></div>
          <div><span className="text-gray-400">Android</span><div className="font-medium dark:text-gray-200">Kotlin + Jetpack Compose</div></div>
        </div>
      </Card>
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">语言</h2>
        <p className="text-sm text-gray-600 dark:text-gray-400">使用左下角的语言切换按钮更改界面语言。</p>
      </Card>
    </div>
  );
}

function LLMSettings({ t }: { t: (key: string, opts?: Record<string, unknown>) => string }) {
  const [config, setConfig] = useState<LLMConfigType | null>(null);
  const [presets, setPresets] = useState<Record<string, LLMPreset>>({});
  const [form, setForm] = useState({ provider: '', base_url: '', api_key: '', model: '', max_context_tokens: 1048576, temperature: 0.1 });
  const [apiKeyInput, setApiKeyInput] = useState('');
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState('');

  useEffect(() => {
    Promise.all([getLLMConfig(), getLLMPresets()]).then(([cfgRes, presetRes]) => {
      setConfig(cfgRes.data);
      setPresets(presetRes.data.presets);
      setForm({ provider: cfgRes.data.provider, base_url: cfgRes.data.base_url, api_key: '', model: cfgRes.data.model, max_context_tokens: cfgRes.data.max_context_tokens, temperature: cfgRes.data.temperature });
    });
  }, []);

  const handleSave = () => {
    setSaving(true);
    const payload: Record<string, unknown> = { ...form };
    if (!payload.api_key) delete payload.api_key;
    updateLLMConfig(payload as Partial<LLMConfigType>).then(() => {
      setMsg(t('settings.saved')); setTimeout(() => setMsg(''), 3000);
      getLLMConfig().then(r => setConfig(r.data));
    }).finally(() => setSaving(false));
  };

  return (
    <div className="space-y-4">
      {/* Current config */}
      {config && (
        <Card>
          <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">{t('settings.current_config')}</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div><span className="text-gray-400">{t('settings.provider')}</span><div className="font-medium dark:text-gray-200">{config.provider}</div></div>
            <div><span className="text-gray-400">{t('settings.model')}</span><div className="font-medium dark:text-gray-200">{config.model}</div></div>
            <div><span className="text-gray-400">{t('settings.max_context')}</span><div className="font-medium dark:text-gray-200">{(config.max_context_tokens / 1024).toFixed(0)}K</div></div>
            <div><span className="text-gray-400">API Key</span><div className="font-medium dark:text-gray-200">{config.api_key_set ? '✅ 已设置' : '❌ 未设置'}</div></div>
          </div>
        </Card>
      )}

      {/* Presets */}
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">{t('settings.presets')}</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {Object.entries(presets).map(([name, preset]) => (
            <button key={name} onClick={() => setForm(f => ({ ...f, provider: preset.provider, base_url: preset.base_url, model: preset.model, max_context_tokens: preset.max_context_tokens }))}
              className={`p-3 rounded-lg border text-left hover:shadow-md transition-all ${form.provider === preset.provider ? PROVIDER_COLORS[name] || 'bg-blue-50 dark:bg-blue-900/30 border-blue-300' : 'bg-gray-50 dark:bg-gray-800 border-gray-200 dark:border-gray-700 hover:border-gray-400'}`}>
              <div className="font-medium text-sm">{t(`presets.${name}`, { defaultValue: name })}</div>
              <div className="text-xs opacity-60 mt-1">{preset.model}</div>
            </button>
          ))}
        </div>
        <div className="mt-4 flex gap-2">
          <input type="password" value={apiKeyInput} onChange={e => setApiKeyInput(e.target.value)} placeholder={t('settings.api_key_placeholder')} className="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" />
          <button onClick={() => applyLLMPreset(form.provider, apiKeyInput).then(() => { getLLMConfig().then(r => { setConfig(r.data); setForm(f => ({ ...f, provider: r.data.provider, base_url: r.data.base_url, model: r.data.model, max_context_tokens: r.data.max_context_tokens })); }); setApiKeyInput(''); })}
            disabled={!form.provider || !apiKeyInput} className="px-4 py-2 bg-blue-500 text-white rounded-lg text-sm hover:bg-blue-600 disabled:opacity-40">{t('settings.apply')}</button>
        </div>
      </Card>

      {/* Manual config */}
      <Card className="space-y-4">
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400">{t('settings.custom')}</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div><label className="block text-xs text-gray-400 mb-1">{t('settings.provider')}</label><input value={form.provider} onChange={e => setForm(f => ({ ...f, provider: e.target.value }))} className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" /></div>
          <div><label className="block text-xs text-gray-400 mb-1">{t('settings.model')}</label><input value={form.model} onChange={e => setForm(f => ({ ...f, model: e.target.value }))} className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" /></div>
        </div>
        <div><label className="block text-xs text-gray-400 mb-1">{t('settings.base_url')}</label><input value={form.base_url} onChange={e => setForm(f => ({ ...f, base_url: e.target.value }))} className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" /></div>
        <div><label className="block text-xs text-gray-400 mb-1">{t('settings.api_key')}</label><input type="password" value={form.api_key} onChange={e => setForm(f => ({ ...f, api_key: e.target.value }))} placeholder={t('settings.api_key_placeholder')} className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" /></div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div><label className="block text-xs text-gray-400 mb-1">{t('settings.max_context')}</label><input type="number" value={form.max_context_tokens} onChange={e => setForm(f => ({ ...f, max_context_tokens: Number(e.target.value) }))} className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" /></div>
          <div><label className="block text-xs text-gray-400 mb-1">{t('settings.temperature')}</label><input type="number" step="0.1" min="0" max="2" value={form.temperature} onChange={e => setForm(f => ({ ...f, temperature: Number(e.target.value) }))} className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" /></div>
        </div>
        <div className="flex items-center gap-4 pt-2">
          <button onClick={handleSave} disabled={saving} className="px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-40 text-sm font-medium">{saving ? '...' : t('settings.save')}</button>
          {msg && <span className="text-green-600 dark:text-green-400 text-sm">{msg}</span>}
        </div>
      </Card>
    </div>
  );
}

function MCPSettings() {
  const [servers, setServers] = useState<MCPServer[]>([]);
  const [form, setForm] = useState({ id: '', name: '', url: '', description: '' });

  useEffect(() => { getMCPServers().then(r => setServers(r.data.servers)); }, []);

  return (
    <div className="space-y-4">
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-1">MCP 服务器</h2>
        <p className="text-xs text-gray-400 mb-4">Model Context Protocol — 连接外部工具服务器扩展 AI 能力</p>
        {servers.length > 0 && (
          <div className="space-y-2 mb-4">
            {servers.map(s => (
              <div key={s.id} className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-800 rounded-lg">
                <div><div className="text-sm font-medium dark:text-gray-200">{s.name}</div><div className="text-xs text-gray-400">{s.url}</div>{s.description && <div className="text-xs text-gray-400 mt-0.5">{s.description}</div>}</div>
                <button onClick={() => deleteMCPServer(s.id).then(() => setServers(prev => prev.filter(x => x.id !== s.id)))} className="text-red-400 hover:text-red-600 text-xs">删除</button>
              </div>
            ))}
          </div>
        )}
        <div className="grid grid-cols-2 gap-2">
          <input value={form.id} onChange={e => setForm(f => ({ ...f, id: e.target.value }))} placeholder="ID" className="px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" />
          <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="名称" className="px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" />
          <input value={form.url} onChange={e => setForm(f => ({ ...f, url: e.target.value }))} placeholder="URL" className="px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm col-span-2" />
          <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="描述" className="px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm col-span-2" />
        </div>
        <button onClick={() => { if (!form.id || !form.name || !form.url) return; createMCPServer(form as Omit<MCPServer, 'enabled'>).then(() => { getMCPServers().then(r => setServers(r.data.servers)); setForm({ id: '', name: '', url: '', description: '' }); }); }}
          className="mt-3 px-4 py-2 bg-blue-500 text-white rounded-lg text-sm hover:bg-blue-600">添加</button>
      </Card>
    </div>
  );
}

function AdvancedSettings() {
  return (
    <div className="space-y-4">
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">搜索 API</h2>
        <p className="text-xs text-gray-400 mb-3">配置网络搜索 API 以启用 AI 互联网搜索功能。支持 Tavily 或 Brave Search。</p>
        <div className="space-y-3">
          <div><label className="block text-xs text-gray-400 mb-1">Tavily API Key</label><input type="password" placeholder="tvly-..." className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" /></div>
          <div><label className="block text-xs text-gray-400 mb-1">Brave Search API Key</label><input type="password" placeholder="BSA..." className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" /></div>
        </div>
      </Card>
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">数据管理</h2>
        <div className="space-y-2">
          <button className="w-full text-left px-4 py-2 bg-gray-50 dark:bg-gray-800 rounded-lg text-sm hover:bg-gray-100 dark:hover:bg-gray-700">🗑️ 清除所有分析缓存</button>
          <button className="w-full text-left px-4 py-2 bg-gray-50 dark:bg-gray-800 rounded-lg text-sm hover:bg-gray-100 dark:hover:bg-gray-700">📥 导出所有数据</button>
          <button className="w-full text-left px-4 py-2 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 rounded-lg text-sm hover:bg-red-100 dark:hover:bg-red-900/40">⚠️ 重置数据库</button>
        </div>
      </Card>
    </div>
  );
}
