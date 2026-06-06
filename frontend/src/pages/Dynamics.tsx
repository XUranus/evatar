import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Markdown from 'react-markdown';
import { Lightbulb, Clock, BarChart3, FileText, Pin, PinOff, Trash2, RefreshCw, Loader } from 'lucide-react';
import axios from 'axios';
import {
  markDynamicRead, toggleDynamicPin, deleteDynamic,
  type DynamicItem,
} from '../api/client';

const categoryIcons: Record<string, React.ReactNode> = {
  insight: <Lightbulb size={14} />,
  reminder: <Clock size={14} />,
  report: <BarChart3 size={14} />,
  note: <FileText size={14} />,
};

const categoryColors: Record<string, string> = {
  insight: 'bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400',
  reminder: 'bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400',
  report: 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400',
  note: 'bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400',
};

export default function DynamicsPage() {
  const { t } = useTranslation();
  const [items, setItems] = useState<DynamicItem[]>([]);
  const [filter, setFilter] = useState<string>('');
  const [expanded, setExpanded] = useState<number | null>(null);
  const [triggering, setTriggering] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<number>(0);
  const [searchQuery, setSearchQuery] = useState('');
  const observerRef = useRef<HTMLDivElement>(null);

  // Load first page
  const load = () => {
    const params: Record<string, string | number> = { limit: 30, cursor: 0 };
    if (filter) params.category = filter;

    axios.get('/api/dynamics', { params }).then(r => {
      setItems(r.data.items || []);
      setNextCursor(r.data.next_cursor || 0);
      setHasMore(r.data.has_more || false);
    }).catch(() => {});
  };

  useEffect(() => { load(); }, [filter]);

  // Infinite scroll observer
  useEffect(() => {
    if (!observerRef.current || !hasMore || loadingMore) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !loadingMore && nextCursor) {
          setLoadingMore(true);
          const params: Record<string, string | number> = { limit: 30, cursor: nextCursor };
          if (filter) params.category = filter;
          axios.get('/api/dynamics', { params }).then(r => {
            const newItems: DynamicItem[] = r.data.items || [];
            setItems(prev => {
              const existingIds = new Set(prev.map(i => i.id));
              const unique = newItems.filter(i => !existingIds.has(i.id));
              return [...prev, ...unique];
            });
            setNextCursor(r.data.next_cursor || 0);
            setHasMore(r.data.has_more || false);
          }).catch(() => {}).finally(() => setLoadingMore(false));
        }
      },
      { threshold: 0.1 }
    );
    observer.observe(observerRef.current);
    return () => observer.disconnect();
  }, [hasMore, loadingMore, nextCursor, filter]);

  const handleTrigger = async () => {
    setTriggering(true);
    try {
      await axios.post('/api/dynamics/trigger');
      setTimeout(load, 2000);
    } finally {
      setTriggering(false);
    }
  };

  const filteredItems = searchQuery
    ? items.filter(i =>
        i.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        i.summary?.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : items;

  const categories = ['', 'insight', 'reminder', 'report', 'note'] as const;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-gray-100">
          {t('dynamic.title', 'Dynamics')}
        </h1>
        <div className="flex gap-2 items-center flex-wrap">
          {categories.map(c => (
            <button
              key={c}
              onClick={() => setFilter(c)}
              className={`flex items-center gap-1 px-3 py-1 rounded-full text-sm border transition-colors ${
                filter === c
                  ? 'bg-blue-500 text-white border-blue-500'
                  : 'bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-400 border-gray-300 dark:border-gray-700 hover:border-blue-400'
              }`}
            >
              {c && categoryIcons[c]}
              {c ? t(`dynamic.categories.${c}`, c) : t('dynamic.all', 'All')}
            </button>
          ))}
          <button
            onClick={handleTrigger}
            disabled={triggering}
            className="px-3 py-1 bg-blue-500 text-white rounded-full text-sm hover:bg-blue-600 disabled:opacity-40"
          >
            {triggering ? <Loader size={14} className="animate-spin" /> : <RefreshCw size={14} />}
            {' '}{t('dynamic.trigger', 'Generate')}
          </button>
        </div>
      </div>

      {/* Search */}
      <input
        type="text"
        value={searchQuery}
        onChange={e => setSearchQuery(e.target.value)}
        placeholder={t('dynamic.search_placeholder', 'Search dynamics...')}
        className="w-full px-4 py-2 border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
      />

      {filteredItems.length === 0 && !loadingMore ? (
        <div className="flex flex-col items-center justify-center h-64 text-gray-400 dark:text-gray-500">
          <FileText size={48} className="mb-4 opacity-40" />
          <div className="text-lg">{t('dynamic.empty', 'No dynamics yet')}</div>
          <div className="text-sm mt-2">{t('dynamic.empty_desc', 'The background analyzer will generate notes from your screenshots and chats.')}</div>
        </div>
      ) : (
        <div className="space-y-4">
          {filteredItems.map(item => (
            <DynamicCard
              key={item.id}
              item={item}
              expanded={expanded === item.id}
              onToggle={() => {
                const next = expanded === item.id ? null : item.id;
                setExpanded(next);
                if (next && !item.is_read) {
                  markDynamicRead(item.id);
                  setItems(prev => prev.map(i => i.id === item.id ? { ...i, is_read: true } : i));
                }
              }}
              onPin={() => toggleDynamicPin(item.id).then(load)}
              onDelete={() => { if (confirm(t('dynamic.confirm_delete', 'Delete?'))) deleteDynamic(item.id).then(load); }}
            />
          ))}

          {/* Infinite scroll trigger */}
          {hasMore && (
            <div ref={observerRef} className="flex justify-center py-4">
              {loadingMore && (
                <div className="flex items-center gap-2 text-gray-400 text-sm">
                  <Loader size={16} className="animate-spin" />
                  {t('dynamic.loading_more', 'Loading more...')}
                </div>
              )}
            </div>
          )}

          {!hasMore && items.length > 0 && (
            <div className="text-center text-sm text-gray-400 py-4">
              {t('dynamic.all_loaded', 'All loaded')}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function DynamicCard({ item, expanded, onToggle, onPin, onDelete }: {
  item: DynamicItem; expanded: boolean; onToggle: () => void; onPin: () => void; onDelete: () => void;
}) {
  const { t } = useTranslation();
  const icon = categoryIcons[item.category] || <FileText size={14} />;
  const color = categoryColors[item.category] || categoryColors.note;

  return (
    <div
      className={`bg-white dark:bg-gray-900 rounded-xl shadow-sm border overflow-hidden transition-all cursor-pointer ${
        item.is_pinned ? 'border-blue-300 dark:border-blue-700' : 'border-gray-100 dark:border-gray-800'
      } ${!item.is_read ? 'ring-1 ring-blue-200 dark:ring-blue-800' : ''}`}
      onClick={onToggle}
    >
      <div className="p-4">
        <div className="flex items-start gap-3">
          <div className={`p-2 rounded-lg ${color}`}>{icon}</div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <h3 className="font-semibold text-gray-800 dark:text-gray-100 truncate">{item.title}</h3>
              {item.is_pinned && <Pin size={12} className="text-blue-500" />}
              {!item.is_read && <span className="w-2 h-2 rounded-full bg-blue-500 flex-shrink-0" />}
            </div>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1 line-clamp-2">{item.summary}</p>
            <div className="flex items-center gap-2 mt-2">
              <span className={`px-2 py-0.5 rounded-full text-xs ${color}`}>
                {t(`dynamic.categories.${item.category}`, item.category)}
              </span>
              <span className="text-xs text-gray-400">
                {item.created_at ? new Date(item.created_at).toLocaleString() : ''}
              </span>
            </div>
          </div>
          <div className="flex gap-1 flex-shrink-0" onClick={e => e.stopPropagation()}>
            <button onClick={onPin} className="p-1 text-gray-400 hover:text-blue-500" title="Pin">
              {item.is_pinned ? <Pin size={14} /> : <PinOff size={14} />}
            </button>
            <button onClick={onDelete} className="p-1 text-gray-400 hover:text-red-500" title="Delete">
              <Trash2 size={14} />
            </button>
          </div>
        </div>
      </div>

      {expanded && (
        <div className="border-t border-gray-100 dark:border-gray-800 p-4">
          <div className="chat-markdown text-sm leading-relaxed">
            <Markdown>{item.content}</Markdown>
          </div>
        </div>
      )}
    </div>
  );
}
