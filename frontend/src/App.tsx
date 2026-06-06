import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutDashboard, Image, MessageSquare, Newspaper, Settings, Sparkles, Sun, Moon, Languages } from 'lucide-react';
import Dashboard from './pages/Dashboard';
import Photos from './pages/Photos';
import ChatPage from './pages/Chat';
import DynamicsPage from './pages/Dynamics';
import SettingsPage from './pages/Settings';
import ErrorBoundary from './components/ErrorBoundary';
import './index.css';

type Page = 'dashboard' | 'photos' | 'chat' | 'dynamics' | 'settings';

export default function App() {
  const { t, i18n } = useTranslation();
  const [page, setPage] = useState<Page>('dashboard');
  const [dark, setDark] = useState(() => {
    const saved = localStorage.getItem('evatar-theme');
    if (saved) return saved === 'dark';
    return true; // Dark mode default for Observatory aesthetic
  });

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
    document.documentElement.classList.toggle('light', !dark);
    localStorage.setItem('evatar-theme', dark ? 'dark' : 'light');
  }, [dark]);

  const toggleLang = () => {
    const next = i18n.language === 'zh-CN' ? 'en' : 'zh-CN';
    i18n.changeLanguage(next);
  };

  const navItems: { key: Page; icon: React.ReactNode; label: string }[] = [
    { key: 'dashboard', icon: <LayoutDashboard size={16} />, label: t('nav.dashboard', 'Dashboard') },
    { key: 'photos', icon: <Image size={16} />, label: t('nav.photos', 'Photos') },
    { key: 'chat', icon: <MessageSquare size={16} />, label: t('nav.chat', 'Chat') },
    { key: 'dynamics', icon: <Newspaper size={16} />, label: t('nav.dynamics', 'Dynamics') },
    { key: 'settings', icon: <Settings size={16} />, label: t('nav.settings', 'Settings') },
  ];

  return (
    <div className="noise flex h-screen overflow-hidden" style={{ background: 'var(--bg-void)' }}>
      {/* ── Sidebar ── */}
      <aside
        className="glass-strong flex flex-col flex-shrink-0 animate-fade-in"
        style={{
          width: '220px',
          borderRight: '1px solid var(--border)',
        }}
      >
        {/* Logo */}
        <div className="px-6 py-6" style={{ borderBottom: '1px solid var(--border)' }}>
          <div className="flex items-center gap-3">
            <div
              className="flex items-center justify-center"
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '10px',
                background: 'var(--amber-dim)',
                border: '1px solid var(--border-accent)',
                boxShadow: 'var(--shadow-glow)',
              }}
            >
              <Sparkles size={16} style={{ color: 'var(--amber)' }} />
            </div>
            <div>
              <h1
                style={{
                  fontFamily: 'var(--font-display)',
                  fontSize: '1.3rem',
                  color: 'var(--text-primary)',
                  lineHeight: 1.1,
                }}
              >
                Evatar
              </h1>
              <p className="label" style={{ marginTop: '2px' }}>
                {t('app.subtitle', 'Screenshot AI')}
              </p>
            </div>
          </div>
        </div>

        {/* Nav */}
        <nav className="flex-1 px-3 py-4" style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
          {navItems.map((item, i) => {
            const active = page === item.key;
            return (
              <button
                key={item.key}
                onClick={() => setPage(item.key)}
                className="animate-fade-up"
                style={{
                  animationDelay: `${i * 50}ms`,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '10px 14px',
                  borderRadius: 'var(--radius-md)',
                  border: 'none',
                  cursor: 'pointer',
                  fontFamily: 'var(--font-body)',
                  fontSize: '0.85rem',
                  fontWeight: active ? 600 : 400,
                  color: active ? 'var(--amber)' : 'var(--text-secondary)',
                  background: active ? 'var(--amber-dim)' : 'transparent',
                  transition: 'all 0.2s ease',
                  textAlign: 'left',
                  width: '100%',
                }}
                onMouseEnter={e => {
                  if (!active) {
                    e.currentTarget.style.background = 'var(--glass-highlight)';
                    e.currentTarget.style.color = 'var(--text-primary)';
                  }
                }}
                onMouseLeave={e => {
                  if (!active) {
                    e.currentTarget.style.background = 'transparent';
                    e.currentTarget.style.color = 'var(--text-secondary)';
                  }
                }}
              >
                <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', width: '20px', opacity: active ? 1 : 0.6 }}>
                  {item.icon}
                </span>
                {item.label}
                {active && (
                  <div
                    style={{
                      marginLeft: 'auto',
                      width: '4px',
                      height: '4px',
                      borderRadius: '50%',
                      background: 'var(--amber)',
                      boxShadow: '0 0 8px var(--amber-glow)',
                    }}
                  />
                )}
              </button>
            );
          })}
        </nav>

        {/* Footer controls */}
        <div
          className="px-4 py-4 flex items-center justify-between"
          style={{ borderTop: '1px solid var(--border)' }}
        >
          <span className="label" style={{ fontSize: '0.65rem' }}>v0.3.0</span>
          <div style={{ display: 'flex', gap: '6px' }}>
            <button
              onClick={toggleLang}
              className="btn-ghost"
              style={{ padding: '4px 10px', fontSize: '0.7rem', borderRadius: '6px' }}
            >
              <Languages size={14} />
            </button>
            <button
              onClick={() => setDark(!dark)}
              className="btn-ghost"
              style={{ padding: '4px 10px', fontSize: '0.7rem', borderRadius: '6px' }}
            >
              {dark ? <Sun size={14} /> : <Moon size={14} />}
            </button>
          </div>
        </div>
      </aside>

      {/* ── Main content ── */}
      <main
        className="mesh-bg"
        style={{
          flex: 1,
          overflow: 'auto',
          padding: page === 'chat' ? '0' : '32px',
        }}
      >
        <ErrorBoundary>
          <div className="animate-fade-up">
            {page === 'dashboard' && <Dashboard />}
            {page === 'photos' && <Photos />}
            {page === 'chat' && <ChatPage />}
            {page === 'dynamics' && <DynamicsPage />}
            {page === 'settings' && <SettingsPage />}
          </div>
        </ErrorBoundary>
      </main>
    </div>
  );
}
