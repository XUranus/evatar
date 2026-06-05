import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Settings, Lock, Bot, Plug, Wrench, Trash2, Smartphone } from 'lucide-react';
import {
  getLLMConfig, updateLLMConfig, getLLMPresets, applyLLMPreset,
  getMCPServers, createMCPServer, deleteMCPServer,
  getDataStats, getRetentionDays, setRetentionDays, clearAllData, exportData,
  getPushConfig, setPushConfig, sendTestNotification,
  getExcludedApps,
  getDevices, removeDevice, sendTestPush,
  type LLMConfig as LLMConfigType, type LLMPreset, type MCPServer,
  type DataStats, type Device,
} from '../api/client';

type SettingsTab = 'general' | 'privacy' | 'llm' | 'mcp' | 'devices' | 'advanced';

const PROVIDER_COLORS: Record<string, string> = {
  mimo: 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400 border-orange-300 dark:border-orange-700',
  qwen: 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400 border-purple-300 dark:border-purple-700',
  openai: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 border-green-300 dark:border-green-700',
  claude: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400 border-amber-300 dark:border-amber-700',
  glm: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 border-blue-300 dark:border-blue-700',
  kimi: 'bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-400 border-indigo-300 dark:border-indigo-700',
  deepseek: 'bg-cyan-100 dark:bg-cyan-900/30 text-cyan-700 dark:text-cyan-400 border-cyan-300 dark:border-cyan-700',
};

const tabItems: { key: SettingsTab; icon: React.ReactNode; labelKey: string }[] = [
  { key: 'general', icon: <Settings size={14} />, labelKey: 'settings.tab_general' },
  { key: 'privacy', icon: <Lock size={14} />, labelKey: 'settings.tab_privacy' },
  { key: 'llm', icon: <Bot size={14} />, labelKey: 'settings.tab_llm' },
  { key: 'mcp', icon: <Plug size={14} />, labelKey: 'settings.tab_mcp' },
  { key: 'devices', icon: <Smartphone size={14} />, labelKey: 'settings.tab_devices' },
  { key: 'advanced', icon: <Wrench size={14} />, labelKey: 'settings.tab_advanced' },
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
            <span className="flex items-center">{item.icon}</span>
            <span className="hidden sm:inline">{t(item.labelKey)}</span>
          </button>
        ))}
      </div>

      {tab === 'general' && <GeneralSettings t={t} />}
      {tab === 'privacy' && <PrivacySettings t={t} />}
      {tab === 'llm' && <LLMSettings t={t} />}
      {tab === 'mcp' && <MCPSettings />}
      {tab === 'devices' && <DevicesSettings />}
      {tab === 'advanced' && <AdvancedSettings />}
    </div>
  );
}

function Card({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <div className={`bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-100 dark:border-gray-800 p-5 ${className}`}>{children}</div>;
}

function GeneralSettings(// eslint-disable-next-line @typescript-eslint/no-explicit-any
{ t }: { t: any }) {
  const [pushEnabled, setPushEnabled] = useState(false);
  const [excludedApps, setExcludedApps] = useState<string[]>([]);
  const [testMsg, setTestMsg] = useState('');

  useEffect(() => {
    getPushConfig().then(r => setPushEnabled(r.data.enabled)).catch(() => {});
    getExcludedApps().then(r => setExcludedApps(r.data.apps)).catch(() => {});
  }, []);

  const handleTogglePush = useCallback(() => {
    const next = !pushEnabled;
    setPushEnabled(next);
    setPushConfig(next).catch(() => setPushEnabled(!next));
  }, [pushEnabled]);

  const handleTestPush = useCallback(() => {
    sendTestNotification().then(() => {
      setTestMsg(t('settings.push_test_sent'));
      setTimeout(() => setTestMsg(''), 3000);
    });
  }, [t]);

  return (
    <div className="space-y-4">
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">{t('settings.app_info')}</h2>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div><span className="text-gray-400">{t('settings.version')}</span><div className="font-medium dark:text-gray-200">0.1.0</div></div>
          <div><span className="text-gray-400">{t('settings.backend')}</span><div className="font-medium dark:text-gray-200">FastAPI + SQLite</div></div>
          <div><span className="text-gray-400">{t('settings.frontend')}</span><div className="font-medium dark:text-gray-200">React + Vite + Tailwind</div></div>
          <div><span className="text-gray-400">Android</span><div className="font-medium dark:text-gray-200">Kotlin + Jetpack Compose</div></div>
        </div>
      </Card>

      {/* Push Notifications */}
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">{t('settings.push_enabled', '推送通知')}</h2>
        <div className="flex items-center justify-between">
          <span className="text-sm dark:text-gray-300">{t('settings.push_enabled', '启用推送通知')}</span>
          <button
            onClick={handleTogglePush}
            className={`relative w-11 h-6 rounded-full transition-colors ${pushEnabled ? 'bg-blue-500' : 'bg-gray-300 dark:bg-gray-600'}`}
          >
            <span className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${pushEnabled ? 'translate-x-5' : ''}`} />
          </button>
        </div>
        {pushEnabled && (
          <div className="mt-3 flex items-center gap-3">
            <button
              onClick={handleTestPush}
              className="px-4 py-2 bg-gray-100 dark:bg-gray-800 text-sm rounded-lg hover:bg-gray-200 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300"
            >
              {t('settings.push_test', '发送测试通知')}
            </button>
            {testMsg && <span className="text-green-600 dark:text-green-400 text-sm">{testMsg}</span>}
          </div>
        )}
      </Card>

      {/* Excluded Apps */}
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-1">{t('settings.excluded_apps', '排除的应用')}</h2>
        <p className="text-xs text-gray-400 mb-3">{t('settings.excluded_apps_desc', '排除列表在手机端设置')}</p>
        {excludedApps.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {excludedApps.map(app => (
              <span key={app} className="px-3 py-1 bg-gray-100 dark:bg-gray-800 text-sm rounded-full text-gray-600 dark:text-gray-400">
                {app}
              </span>
            ))}
          </div>
        ) : (
          <p className="text-sm text-gray-400">{t('settings.no_excluded_apps', '暂无排除的应用')}</p>
        )}
      </Card>

      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">{t('settings.language')}</h2>
        <p className="text-sm text-gray-600 dark:text-gray-400">{t('settings.language_desc')}</p>
      </Card>
    </div>
  );
}

const RETENTION_OPTIONS = [7, 14, 30, 90, 365];

function PrivacySettings(// eslint-disable-next-line @typescript-eslint/no-explicit-any
{ t }: { t: any }) {
  const [stats, setStats] = useState<DataStats | null>(null);
  const [retentionDays, setRetentionDaysState] = useState(30);
  const [clearing, setClearing] = useState(false);
  const [clearMsg, setClearMsg] = useState('');

  useEffect(() => {
    getDataStats().then(r => setStats(r.data)).catch(() => {});
    getRetentionDays().then(r => setRetentionDaysState(r.data.retention_days)).catch(() => {});
  }, []);

  const handleRetentionChange = useCallback((days: number) => {
    setRetentionDaysState(days);
    setRetentionDays(days).catch(() => {});
  }, []);

  const handleClearData = useCallback(() => {
    if (!confirm(t('settings.clear_data_confirm', '确定要清除所有数据吗？此操作不可撤销！'))) return;
    setClearing(true);
    clearAllData().then(() => {
      setClearMsg(t('settings.clear_data_success', '所有数据已清除'));
      setTimeout(() => setClearMsg(''), 3000);
      getDataStats().then(r => setStats(r.data)).catch(() => {});
    }).finally(() => setClearing(false));
  }, [t]);

  const handleExport = useCallback(() => {
    exportData().then(response => {
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const a = document.createElement('a');
      a.href = url;
      a.download = `evatar-export-${new Date().toISOString().slice(0, 10)}.zip`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    });
  }, []);

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  return (
    <div className="space-y-4">
      {/* Data Stats */}
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">{t('settings.data_stats', '数据统计')}</h2>
        <div className="grid grid-cols-3 gap-4 text-sm">
          <div>
            <span className="text-gray-400">{t('settings.total_photos', '总照片数')}</span>
            <div className="text-2xl font-bold dark:text-gray-200">{stats?.total_photos ?? '-'}</div>
          </div>
          <div>
            <span className="text-gray-400">{t('settings.total_memories', '总记忆数')}</span>
            <div className="text-2xl font-bold dark:text-gray-200">{stats?.total_memories ?? '-'}</div>
          </div>
          <div>
            <span className="text-gray-400">{t('settings.storage_used', '存储占用')}</span>
            <div className="text-2xl font-bold dark:text-gray-200">{stats ? formatBytes(stats.storage_used_bytes) : '-'}</div>
          </div>
        </div>
      </Card>

      {/* Data Retention */}
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-1">{t('settings.retention', '数据保留天数')}</h2>
        <p className="text-xs text-gray-400 mb-4">{t('settings.retention_desc', '超过保留期的照片和分析数据将被自动清理')}</p>
        <div className="flex items-center gap-2">
          {RETENTION_OPTIONS.map(days => (
            <button
              key={days}
              onClick={() => handleRetentionChange(days)}
              className={`flex-1 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                retentionDays === days
                  ? 'bg-blue-500 text-white'
                  : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700'
              }`}
            >
              {t('settings.retention_days', { count: days })}
            </button>
          ))}
        </div>
      </Card>

      {/* Export */}
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-1">{t('settings.export_data', '导出数据')}</h2>
        <p className="text-xs text-gray-400 mb-3">{t('settings.export_data_desc', '导出所有照片、分析结果和记忆数据')}</p>
        <button
          onClick={handleExport}
          className="w-full text-left px-4 py-3 bg-gray-50 dark:bg-gray-800 rounded-lg text-sm hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2"
        >
          📥 {t('settings.export_data', '导出数据')}
        </button>
      </Card>

      {/* Clear Data */}
      <Card>
        <h2 className="text-sm font-semibold text-red-500 dark:text-red-400 mb-1">{t('settings.clear_data', '清除所有数据')}</h2>
        <p className="text-xs text-gray-400 mb-3">{t('settings.clear_data_confirm', '确定要清除所有数据吗？此操作不可撤销！')}</p>
        <button
          onClick={handleClearData}
          disabled={clearing}
          className="w-full text-left px-4 py-3 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 rounded-lg text-sm hover:bg-red-100 dark:hover:bg-red-900/40 disabled:opacity-40"
        >
          ⚠️ {clearing ? '...' : t('settings.clear_data', '清除所有数据')}
        </button>
        {clearMsg && <p className="text-green-600 dark:text-green-400 text-sm mt-2">{clearMsg}</p>}
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
            <div><span className="text-gray-400">API Key</span><div className="font-medium dark:text-gray-200">{config.api_key_set ? t('settings.api_key_set_display') : t('settings.api_key_not_set_display')}</div></div>
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
  const { t } = useTranslation();
  const [servers, setServers] = useState<MCPServer[]>([]);
  const [form, setForm] = useState({ id: '', name: '', url: '', description: '' });

  useEffect(() => { getMCPServers().then(r => setServers(r.data.servers)); }, []);

  return (
    <div className="space-y-4">
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-1">{t('settings.mcp_servers', 'MCP Servers')}</h2>
        <p className="text-xs text-gray-400 mb-4">{t('settings.mcp_desc', 'Model Context Protocol — connect external tool servers to extend AI capabilities')}</p>
        {servers.length > 0 && (
          <div className="space-y-2 mb-4">
            {servers.map(s => (
              <div key={s.id} className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-800 rounded-lg">
                <div><div className="text-sm font-medium dark:text-gray-200">{s.name}</div><div className="text-xs text-gray-400">{s.url}</div>{s.description && <div className="text-xs text-gray-400 mt-0.5">{s.description}</div>}</div>
                <button onClick={() => deleteMCPServer(s.id).then(() => setServers(prev => prev.filter(x => x.id !== s.id)))} className="text-red-400 hover:text-red-600 text-xs">{t('common.delete', 'Delete')}</button>
              </div>
            ))}
          </div>
        )}
        <div className="grid grid-cols-2 gap-2">
          <input value={form.id} onChange={e => setForm(f => ({ ...f, id: e.target.value }))} placeholder="ID" className="px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" />
          <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder={t('settings.name_placeholder', 'Name')} className="px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" />
          <input value={form.url} onChange={e => setForm(f => ({ ...f, url: e.target.value }))} placeholder="URL" className="px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm col-span-2" />
          <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder={t('settings.description_placeholder', 'Description')} className="px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm col-span-2" />
        </div>
        <button onClick={() => { if (!form.id || !form.name || !form.url) return; createMCPServer(form as Omit<MCPServer, 'enabled'>).then(() => { getMCPServers().then(r => setServers(r.data.servers)); setForm({ id: '', name: '', url: '', description: '' }); }); }}
          className="mt-3 px-4 py-2 bg-blue-500 text-white rounded-lg text-sm hover:bg-blue-600">{t('common.add', 'Add')}</button>
      </Card>
    </div>
  );
}

function AdvancedSettings() {
  const { t } = useTranslation();
  return (
    <div className="space-y-4">
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">{t('settings.search_api', 'Search API')}</h2>
        <p className="text-xs text-gray-400 mb-3">{t('settings.search_api_desc', 'Configure web search API for AI internet search. Supports Tavily or Brave Search.')}</p>
        <div className="space-y-3">
          <div><label className="block text-xs text-gray-400 mb-1">Tavily API Key</label><input type="password" placeholder="tvly-..." className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" /></div>
          <div><label className="block text-xs text-gray-400 mb-1">Brave Search API Key</label><input type="password" placeholder="BSA..." className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-lg text-sm" /></div>
        </div>
      </Card>
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">{t('settings.data_management', 'Data Management')}</h2>
        <div className="space-y-2">
          <button className="w-full text-left px-4 py-2 bg-gray-50 dark:bg-gray-800 rounded-lg text-sm hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2"><Trash2 size={14} /> {t('settings.clear_analysis_cache', 'Clear analysis cache')}</button>
          <button className="w-full text-left px-4 py-2 bg-gray-50 dark:bg-gray-800 rounded-lg text-sm hover:bg-gray-100 dark:hover:bg-gray-700">{t('settings.export_all_data', 'Export all data')}</button>
          <button className="w-full text-left px-4 py-2 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 rounded-lg text-sm hover:bg-red-100 dark:hover:bg-red-900/40">{t('settings.reset_database', 'Reset database')}</button>
        </div>
      </Card>
    </div>
  );
}

function DevicesSettings() {
  const { t } = useTranslation();
  const [devices, setDevices] = useState<Device[]>([]);

  useEffect(() => {
    getDevices().then(r => setDevices(r.data.devices)).catch(() => {});
  }, []);

  return (
    <div className="space-y-4">
      <Card>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-1">{t('settings.registered_devices', 'Registered Devices')}</h2>
        <p className="text-xs text-gray-400 mb-4">{t('settings.devices_desc', 'Devices that have connected and can receive push notifications.')}</p>

        {devices.length === 0 ? (
          <div className="text-sm text-gray-400 py-8 text-center">
            {t('settings.no_devices', 'No devices registered yet. Open the Android app to register.')}
          </div>
        ) : (
          <div className="space-y-3">
            {devices.map(device => (
              <div key={device.device_id} className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-800 rounded-lg">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 bg-blue-100 dark:bg-blue-900/30 rounded-full flex items-center justify-center text-lg">
                    {device.platform === 'android' ? '🤖' : '📱'}
                  </div>
                  <div>
                    <div className="text-sm font-medium dark:text-gray-200">{device.device_name}</div>
                    <div className="text-xs text-gray-400">{device.device_model} · {device.platform}</div>
                    <div className="text-xs text-gray-400">
                      {t('settings.last_seen', 'Last seen')}: {device.last_seen ? new Date(device.last_seen).toLocaleString() : '-'}
                    </div>
                  </div>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => sendTestPush(device.device_id).then(() => alert('测试通知已发送'))}
                    className="px-3 py-1 text-xs bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded hover:bg-blue-100"
                  >
                    {t('settings.send_test', 'Test')}
                  </button>
                  <button
                    onClick={() => {
                      if (confirm(t('settings.confirm_remove_device', 'Remove this device?'))) {
                        removeDevice(device.device_id).then(() => {
                          setDevices(prev => prev.filter(d => d.device_id !== device.device_id));
                        });
                      }
                    }}
                    className="px-3 py-1 text-xs bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400 rounded hover:bg-red-100"
                  >
                    {t('common.delete', 'Delete')}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
