import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getPhotos, getPhotoUrl, getThumbnailUrl, deletePhoto, reprocessPhoto,
  type Photo, type PhotoDetail, getPhoto,
} from '../api/client';

const statusColors: Record<string, string> = {
  done: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400',
  pending: 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400',
  processing: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400',
  error: 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400',
};

export default function Photos() {
  const { t } = useTranslation();
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [filter, setFilter] = useState<string>('');
  const [selected, setSelected] = useState<PhotoDetail | null>(null);
  const [loading, setLoading] = useState(false);

  const load = (p: number, status: string) => {
    setLoading(true);
    getPhotos(p, 20, status || undefined)
      .then(r => {
        setPhotos(r.data.items);
        setTotal(r.data.total);
      })
      .catch(() => { setPhotos([]); setTotal(0); })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load(page, filter);
    const interval = setInterval(() => load(page, filter), 5000);
    return () => clearInterval(interval);
  }, [page, filter]);

  const openDetail = (id: number) => {
    getPhoto(id).then(r => setSelected(r.data)).catch(() => {});
  };

  const handleDelete = (id: number) => {
    if (!confirm(t('photos.confirm_delete'))) return;
    deletePhoto(id).then(() => {
      setSelected(null);
      load(page, filter);
    }).catch(() => {});
  };

  const handleReprocess = (id: number) => {
    reprocessPhoto(id).then(() => {
      load(page, filter);
      if (selected?.id === id) openDetail(id);
    }).catch(() => {});
  };

  const totalPages = Math.ceil(total / 20);
  const filters = ['', 'pending', 'processing', 'done', 'error'];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-gray-100">{t('photos.title')}</h1>
        <div className="flex gap-2">
          {filters.map(s => (
            <button
              key={s}
              onClick={() => { setFilter(s); setPage(1); }}
              className={`px-3 py-1 rounded-full text-sm border transition-colors ${
                filter === s
                  ? 'bg-blue-500 text-white border-blue-500'
                  : 'bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-400 border-gray-300 dark:border-gray-700 hover:border-blue-400'
              }`}
            >
              {s ? t(`photos.${s}`) : t('photos.all')}
            </button>
          ))}
        </div>
      </div>

      {loading && photos.length === 0 ? (
        <div className="text-center text-gray-400 dark:text-gray-500 py-12">{t('photos.loading')}</div>
      ) : photos.length === 0 ? (
        <div className="text-center text-gray-400 dark:text-gray-500 py-12">{t('photos.empty')}</div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {photos.map(photo => (
            <div
              key={photo.id}
              onClick={() => openDetail(photo.id)}
              className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-100 dark:border-gray-800 overflow-hidden cursor-pointer hover:shadow-md transition-shadow"
            >
              <div className="aspect-square bg-gray-100 dark:bg-gray-800 relative">
                <img
                  src={getThumbnailUrl(photo.id)}
                  alt={photo.filename}
                  className="w-full h-full object-cover"
                  loading="lazy"
                />
                <span className={`absolute top-2 right-2 px-2 py-0.5 rounded-full text-xs font-medium ${statusColors[photo.analysis_status] || 'bg-gray-100'}`}>
                  {t(`photos.${photo.analysis_status}`, { defaultValue: photo.analysis_status })}
                </span>
              </div>
              <div className="p-2">
                <div className="text-xs text-gray-500 dark:text-gray-400 truncate">{photo.filename}</div>
                {photo.intent && photo.intent !== 'ignore' && (
                  <div className="mt-1">
                    <span className="inline-block px-1.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded text-xs">
                      {t(`intents.${photo.intent}`, { defaultValue: photo.intent })}
                    </span>
                  </div>
                )}
                {photo.summary && (
                  <div className="text-xs text-gray-400 dark:text-gray-500 mt-1 line-clamp-2">{photo.summary}</div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex justify-center gap-2 pt-4">
          <button
            onClick={() => setPage(p => Math.max(1, p - 1))}
            disabled={page === 1}
            className="px-3 py-1 rounded border border-gray-300 dark:border-gray-700 text-sm disabled:opacity-40"
          >
            {t('photos.prev_page')}
          </button>
          <span className="px-3 py-1 text-sm text-gray-500 dark:text-gray-400">{page} / {totalPages}</span>
          <button
            onClick={() => setPage(p => Math.min(totalPages, p + 1))}
            disabled={page === totalPages}
            className="px-3 py-1 rounded border border-gray-300 dark:border-gray-700 text-sm disabled:opacity-40"
          >
            {t('photos.next_page')}
          </button>
        </div>
      )}

      {selected && (
        <div
          className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4"
          onClick={() => setSelected(null)}
        >
          <div
            className="bg-white dark:bg-gray-900 rounded-2xl max-w-4xl w-full max-h-[90vh] overflow-y-auto shadow-2xl"
            onClick={e => e.stopPropagation()}
          >
            <div className="flex flex-col md:flex-row">
              <div className="md:w-1/2 bg-gray-100 dark:bg-gray-800 flex items-center justify-center p-4">
                <img
                  src={getPhotoUrl(selected.id)}
                  alt={selected.filename}
                  className="max-w-full max-h-[70vh] object-contain rounded"
                />
              </div>
              <div className="md:w-1/2 p-6 space-y-4">
                <div className="flex items-center justify-between">
                  <h2 className="text-lg font-bold text-gray-800 dark:text-gray-100 truncate">{selected.filename}</h2>
                  <button onClick={() => setSelected(null)} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 text-xl">✕</button>
                </div>

                {selected.analysis ? (
                  <>
                    <div className="grid grid-cols-2 gap-3">
                      <InfoField label={t('photos.status')} value={t(`photos.${selected.analysis.status}`)} />
                      <InfoField label={t('photos.app')} value={selected.analysis.app_name} />
                      <InfoField label={t('photos.category')} value={t(`categories.${selected.analysis.content_category}`, { defaultValue: selected.analysis.content_category })} />
                      <InfoField label={t('photos.intent')} value={t(`intents.${selected.analysis.intent}`, { defaultValue: selected.analysis.intent })} />
                      <InfoField label={t('photos.confidence')} value={selected.analysis.confidence ? `${(selected.analysis.confidence * 100).toFixed(0)}%` : '-'} />
                      <InfoField label={t('photos.process_time')} value={selected.analysis.completed_at ? new Date(selected.analysis.completed_at).toLocaleString() : '-'} />
                    </div>

                    {selected.analysis.summary && (
                      <div>
                        <div className="text-xs text-gray-400 mb-1">{t('photos.summary')}</div>
                        <div className="text-sm text-gray-700 dark:text-gray-300 bg-gray-50 dark:bg-gray-800 rounded-lg p-3">{selected.analysis.summary}</div>
                      </div>
                    )}

                    {selected.analysis.entities && selected.analysis.entities !== '[]' && (
                      <div>
                        <div className="text-xs text-gray-400 mb-1">{t('photos.entities')}</div>
                        <div className="text-sm text-gray-700 dark:text-gray-300 bg-gray-50 dark:bg-gray-800 rounded-lg p-3 font-mono text-xs">
                          {selected.analysis.entities}
                        </div>
                      </div>
                    )}

                    {selected.analysis.error_message && (
                      <div>
                        <div className="text-xs text-red-400 mb-1">{t('photos.error_msg')}</div>
                        <div className="text-sm text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 rounded-lg p-3">{selected.analysis.error_message}</div>
                      </div>
                    )}
                  </>
                ) : (
                  <div className="text-gray-400 dark:text-gray-500 text-sm">{t('photos.no_analysis')}</div>
                )}

                <div className="flex gap-2 pt-2">
                  <button
                    onClick={() => handleReprocess(selected.id)}
                    className="flex-1 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 text-sm"
                  >
                    {t('photos.reprocess')}
                  </button>
                  <button
                    onClick={() => handleDelete(selected.id)}
                    className="px-4 py-2 border border-red-300 dark:border-red-700 text-red-500 dark:text-red-400 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 text-sm"
                  >
                    {t('photos.delete')}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function InfoField({ label, value }: { label: string; value: string | undefined | null }) {
  return (
    <div>
      <div className="text-xs text-gray-400">{label}</div>
      <div className="text-sm text-gray-700 dark:text-gray-300 font-medium">{value || '-'}</div>
    </div>
  );
}
