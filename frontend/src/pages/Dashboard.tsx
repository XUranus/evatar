import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Image, CheckCircle, Clock, Loader, AlertCircle, Sparkles } from 'lucide-react';
import { getStats, type Stats } from '../api/client';

export default function Dashboard() {
  const { t } = useTranslation();
  const [stats, setStats] = useState<Stats | null>(null);

  useEffect(() => {
    const load = () => getStats().then(r => setStats(r.data)).catch(() => {});
    load();
    const interval = setInterval(load, 8000);
    return () => clearInterval(interval);
  }, []);

  if (!stats) {
    return (
      <div className="flex items-center justify-center" style={{ height: '60vh', color: 'var(--text-muted)' }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ marginBottom: '12px', opacity: 0.4 }} className="text-gray-400"><Sparkles size={32} /></div>
          <div className="label">{t('photos.loading', 'Loading...')}</div>
        </div>
      </div>
    );
  }

  const cards = [
    { key: 'total_photos', value: stats.total_photos, color: 'blue', icon: <Image size={16} /> },
    { key: 'analyzed', value: stats.done, color: 'teal', icon: <CheckCircle size={16} /> },
    { key: 'pending', value: stats.pending, color: 'amber', icon: <Clock size={16} /> },
    { key: 'processing', value: stats.processing, color: 'blue', icon: <Loader size={16} /> },
    { key: 'errors', value: stats.errors, color: 'coral', icon: <AlertCircle size={16} /> },
  ];

  return (
    <div style={{ maxWidth: '1100px' }}>
      {/* Header */}
      <div className="animate-fade-up" style={{ marginBottom: '32px' }}>
        <h1 className="heading-xl">{t('dashboard.title', 'Dashboard')}</h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginTop: '4px' }}>
          {t('app.subtitle', 'Screenshot sync & AI analysis')}
        </p>
      </div>

      {/* Stat cards */}
      <div className="stagger" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px', marginBottom: '32px' }}>
        {cards.map(card => (
          <div
            key={card.key}
            className="card animate-fade-up"
            style={{ padding: '20px 24px' }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
              <span className="label">{t(`dashboard.${card.key}`, card.key)}</span>
              <span style={{ color: `var(--${card.color})`, opacity: 0.6, display: 'flex', alignItems: 'center' }}>{card.icon}</span>
            </div>
            <div className={`stat-number ${card.color}`}>{card.value}</div>
          </div>
        ))}
      </div>

      {/* Distribution charts */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
        <DistributionChart
          title={t('dashboard.intent_distribution', 'Intent Distribution')}
          data={stats.intent_distribution}
          color="var(--amber)"
          t={t}
          ns="intents"
        />
        <DistributionChart
          title={t('dashboard.category_distribution', 'Category Distribution')}
          data={stats.category_distribution}
          color="var(--teal)"
          t={t}
          ns="categories"
        />
      </div>
    </div>
  );
}

function DistributionChart({ title, data, color, t, ns }: {
  title: string;
  data: Record<string, number>;
  color: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  t: any;
  ns: string;
}) {
  const total = Object.values(data).reduce((a, b) => a + b, 0);

  return (
    <div className="card animate-fade-up" style={{ padding: '24px' }}>
      <h2 className="heading-md" style={{ marginBottom: '16px' }}>{title}</h2>
      {total === 0 ? (
        <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem', padding: '20px 0', textAlign: 'center' }}>
          {t('dashboard.no_data', 'No data yet')}
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {Object.entries(data)
            .sort((a, b) => b[1] - a[1])
            .map(([key, count]) => {
              const pct = (count / total) * 100;
              return (
                <div key={key}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                    <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                      {t(`${ns}.${key}`, { defaultValue: key })}
                    </span>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      {count} ({pct.toFixed(0)}%)
                    </span>
                  </div>
                  <div
                    style={{
                      width: '100%',
                      height: '4px',
                      borderRadius: '2px',
                      background: 'var(--bg-tertiary)',
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        width: `${pct}%`,
                        height: '100%',
                        borderRadius: '2px',
                        background: color,
                        transition: 'width 0.6s ease',
                        boxShadow: `0 0 8px ${color}40`,
                      }}
                    />
                  </div>
                </div>
              );
            })}
        </div>
      )}
    </div>
  );
}
