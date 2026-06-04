import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import Markdown from 'react-markdown';
import { Lightbulb, Clock, BarChart3, FileText, Pin, PinOff, Trash2, RefreshCw, Loader } from 'lucide-react';
import {
  getDynamics, markDynamicRead, toggleDynamicPin, deleteDynamic, triggerReasoning,
  markAllDynamicsRead,
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
  const [total, setTotal] = useState(0);
  const [filter, setFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [expanded, setExpanded] = useState<number | null>(null);
  const [triggering, setTriggering] = useState(false);

  const load = () => {
    getDynamics(1, 50, filter || undefined).then(r => {
      setItems(r.data.items);
      setTotal(r.data.total);
    });
  };

  useEffect(() => { load(); }, [filter]);

  const handleTrigger = async () => {
    setTriggering(true);
    try {
      await triggerReasoning();
      setTimeout(load, 2000);
    } finally {
      setTriggering(false);
    }
  };

  const handleMarkAllRead = useCallback(() => {
    markAllDynamicsRead().then(() => {
      setItems(prev => prev.map(item => ({ ...item, is_read: true })));
    });
  }, []);

  const categories = ['', 'insight', 'reminder', 'report', 'note'];

  const filteredItems = searchQuery.trim()
    ? items.filter(item =>
        item.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        item.summary.toLowerCase().includes(searchQuery.toLowerCase()) ||
        item.content.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : items;

  const unreadCount = items.filter(item => !item.is_read).length;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-gray-100">
          {t('dynamic.title', '动态')}
        </h1>
        <div className="flex gap-2 items-center">
          {categories.map(c => (
            <button
              key={c}
              onClick={() => setFilter(c)}
              className={`px-3 py-1 rounded-full text-sm border transition-colors ${
                filter === c
                  ? 'bg-blue-500 text-white border-blue-500'
                  : 'bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-400 border-gray-300 dark:border-gray-700 hover:border-blue-400'
              }`}
            >
              {c ? <>{categoryIcons[c]} {t(`dynamic.categories.${c}`, c)}</> : t('dynamic.all', '全部')}
            </button>
          ))}
          {unreadCount > 0 && (
            <button
              onClick={handleMarkAllRead}
              className="px-3 py-1 bg-green-500 text-white rounded-full text-sm hover:bg-green-600"
            >
              ✓ {t('dynamic.mark_all_read', '全部标为已读')}
            </button>
          )}
          <button
            onClick={handleTrigger}
            disabled={triggering}
            className="px-3 py-1 bg-blue-500 text-white rounded-full text-sm hover:bg-blue-600 disabled:opacity-40"
          >
            {triggering ? <Loader size={14} className="animate-spin" /> : <RefreshCw size={14} />} {t('dynamic.trigger', '生成')}
          </button>
        </div>
      </div>

      {/* Search bar */}
      <div className="relative">
        <input
          type="text"
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
          placeholder={t('dynamic.search_placeholder', '搜索动态...')}
          className="w-full px-4 py-2.5 pl-10 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-xl text-sm text-gray-800 dark:text-gray-200 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500"
        />
        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">🔍</span>
      </div>

      {filteredItems.length === 0 ? (
        <div className="flex flex-col items-center justify-center h-64 text-gray-400 dark:text-gray-500">
          <div className="text-4xl mb-4 text-gray-400"><FileText size={48} /></div>
          <div className="text-lg">{t('dynamic.empty', '暂无动态')}</div>
          <div className="text-sm mt-2">{t('dynamic.empty_desc', '后台意图推理会定期分析你的截图和聊天，生成有价值的笔记。')}</div>
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
                if (next && !item.is_read) markDynamicRead(item.id);
              }}
              onPin={() => toggleDynamicPin(item.id).then(load)}
              onDelete={() => { if (confirm(t('dynamic.confirm_delete'))) deleteDynamic(item.id).then(load); }}
            />
          ))}
        </div>
      )}

      {total > filteredItems.length && (
        <div className="text-center text-sm text-gray-400">
          {t('dynamic.showing', { shown: filteredItems.length, total, defaultValue: `显示 ${filteredItems.length}/${total}` })}
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
    <div className={`bg-white dark:bg-gray-900 rounded-xl shadow-sm border overflow-hidden transition-all ${
      item.is_pinned ? 'border-blue-300 dark:border-blue-700' : 'border-gray-100 dark:border-gray-800'
    } ${!item.is_read ? 'ring-1 ring-blue-200 dark:ring-blue-800' : ''}`}>
      {/* Header */}
      <div className="p-4 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800/50" onClick={onToggle}>
        <div className="flex items-start gap-3">
          <span className="flex items-center justify-center" style={{ width: '32px', height: '32px', borderRadius: '8px', background: 'var(--bg-secondary, #f3f4f6)', color: 'var(--text-secondary)' }}>{icon}</span>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <h3 className="font-semibold text-gray-800 dark:text-gray-100 truncate">{item.title}</h3>
              {item.is_pinned && <span className="text-blue-500"><Pin size={12} /></span>}
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
              {item.confidence && (
                <span className="text-xs text-gray-400">
                  {Math.round(item.confidence * 100)}%
                </span>
              )}
            </div>
          </div>
          <div className="flex gap-1 flex-shrink-0">
            <button onClick={e => { e.stopPropagation(); onPin(); }} className="p-1 text-gray-400 hover:text-blue-500" title={item.is_pinned ? t('common.unpin') : t('common.pin')}>
              {item.is_pinned ? <Pin size={12} /> : <PinOff size={12} />}
            </button>
            <button onClick={e => { e.stopPropagation(); onDelete(); }} className="p-1 text-gray-400 hover:text-red-500" title={t('common.delete')}>
              <Trash2 size={14} />
            </button>
          </div>
        </div>
      </div>

      {/* Expanded content */}
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
