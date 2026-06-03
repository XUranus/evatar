import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getStats, type Stats } from '../api/client';

export default function Dashboard() {
  const { t } = useTranslation();
  const [stats, setStats] = useState<Stats | null>(null);

  useEffect(() => {
    const load = () => {
      getStats().then(r => setStats(r.data)).catch(() => {});
    };
    load();
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
  }, []);

  if (!stats) {
    return <div className="flex items-center justify-center h-64 text-gray-400">{t('photos.loading')}</div>;
  }

  const statCards = [
    { key: 'total_photos', value: stats.total_photos, color: 'bg-blue-500' },
    { key: 'analyzed', value: stats.done, color: 'bg-green-500' },
    { key: 'pending', value: stats.pending, color: 'bg-yellow-500' },
    { key: 'processing', value: stats.processing, color: 'bg-indigo-500' },
    { key: 'errors', value: stats.errors, color: 'bg-red-500' },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-800">{t('dashboard.title')}</h1>

      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        {statCards.map(card => (
          <div key={card.key} className="bg-white rounded-xl shadow-sm p-5 border border-gray-100">
            <div className={`w-2 h-2 rounded-full ${card.color} mb-2`} />
            <div className="text-3xl font-bold text-gray-800">{card.value}</div>
            <div className="text-sm text-gray-500 mt-1">{t(`dashboard.${card.key}`)}</div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <DistributionChart
          title={t('dashboard.intent_distribution')}
          data={stats.intent_distribution}
          color="bg-blue-500"
          t={t}
          ns="intents"
        />
        <DistributionChart
          title={t('dashboard.category_distribution')}
          data={stats.category_distribution}
          color="bg-green-500"
          t={t}
          ns="categories"
        />
      </div>
    </div>
  );
}

function DistributionChart({
  title, data, color, t, ns,
}: {
  title: string;
  data: Record<string, number>;
  color: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  t: any;
  ns: string;
}) {
  const total = Object.values(data).reduce((a, b) => a + b, 0);

  return (
    <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
      <h2 className="text-lg font-semibold text-gray-700 mb-4">{title}</h2>
      {total === 0 ? (
        <div className="text-gray-400 text-sm">{t('dashboard.no_data')}</div>
      ) : (
        <div className="space-y-3">
          {Object.entries(data)
            .sort((a, b) => b[1] - a[1])
            .map(([key, count]) => {
              const pct = (count / total) * 100;
              return (
                <div key={key}>
                  <div className="flex justify-between text-sm mb-1">
                    <span className="text-gray-600">{t(`${ns}.${key}`, { defaultValue: key })}</span>
                    <span className="text-gray-400">{count} ({pct.toFixed(0)}%)</span>
                  </div>
                  <div className="w-full bg-gray-100 rounded-full h-2">
                    <div className={`${color} h-2 rounded-full transition-all duration-500`} style={{ width: `${pct}%` }} />
                  </div>
                </div>
              );
            })}
        </div>
      )}
    </div>
  );
}
