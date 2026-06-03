import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Dashboard from './pages/Dashboard';
import Photos from './pages/Photos';
import ChatPage from './pages/Chat';
import SettingsPage from './pages/Settings';
import './index.css';

type Page = 'dashboard' | 'photos' | 'chat' | 'settings';

export default function App() {
  const { t, i18n } = useTranslation();
  const [page, setPage] = useState<Page>('dashboard');
  const [dark, setDark] = useState(() => {
    const saved = localStorage.getItem('evatar-theme');
    if (saved) return saved === 'dark';
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  });

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
    localStorage.setItem('evatar-theme', dark ? 'dark' : 'light');
  }, [dark]);

  const toggleLang = () => {
    const next = i18n.language === 'zh-CN' ? 'en' : 'zh-CN';
    i18n.changeLanguage(next);
  };

  const navItems: { key: Page; icon: string }[] = [
    { key: 'dashboard', icon: '📊' },
    { key: 'photos', icon: '🖼️' },
    { key: 'chat', icon: '💬' },
    { key: 'settings', icon: '⚙️' },
  ];

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-slate-950 flex">
      {/* Sidebar */}
      <aside className="w-56 glass-strong flex flex-col flex-shrink-0 border-r border-gray-200 dark:border-gray-800">
        <div className="p-5 border-b border-gray-200 dark:border-gray-800">
          <h1 className="text-xl font-bold text-gray-800 dark:text-gray-100">📷 {t('app.name')}</h1>
          <p className="text-xs text-gray-400 mt-1">{t('app.subtitle')}</p>
        </div>
        <nav className="flex-1 p-3 space-y-1">
          {navItems.map(item => (
            <button
              key={item.key}
              onClick={() => setPage(item.key)}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${
                page === item.key
                  ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400 font-medium'
                  : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'
              }`}
            >
              <span className="text-base">{item.icon}</span>
              {t(`nav.${item.key}`, item.key)}
            </button>
          ))}
        </nav>
        <div className="p-4 border-t border-gray-200 dark:border-gray-800 flex items-center justify-between">
          <span className="text-xs text-gray-400">v0.1.0</span>
          <div className="flex gap-1">
            <button onClick={toggleLang}
              className="text-xs px-2 py-1 rounded border border-gray-300 dark:border-gray-700 text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800">
              {i18n.language === 'zh-CN' ? 'EN' : '中文'}
            </button>
            <button onClick={() => setDark(!dark)}
              className="text-xs px-2 py-1 rounded border border-gray-300 dark:border-gray-700 text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800">
              {dark ? '☀️' : '🌙'}
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className={`flex-1 overflow-auto ${page === 'chat' ? 'p-0' : 'p-6'}`}>
        {page === 'dashboard' && <Dashboard />}
        {page === 'photos' && <Photos />}
        {page === 'chat' && <ChatPage />}
        {page === 'settings' && <SettingsPage />}
      </main>
    </div>
  );
}
