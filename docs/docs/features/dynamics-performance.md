# Dynamics 性能优化设计

## 概述

Dynamics（动态）是 Evatar 系统中由后台意图推理器自动生成的文章/笔记。随着使用时间增长，数据量可能达到数万甚至数十万条。本文档描述了如何在不牺牲用户体验的前提下支撑大规模数据。

## 设计目标

- 首屏加载 < 200ms（从本地缓存）
- 滑动列表无卡顿，支持 10w+ 条数据
- 后台轮询不造成 UI 抖动
- 带宽消耗最小化

## 架构设计

```
┌──────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Android App │     │   Backend API    │     │    SQLite DB    │
│              │     │                  │     │                 │
│ SharedPreferences ←→ │ Cursor Pagination │ ←→ │ dynamics 表    │
│ (本地缓存)    │     │ + Lean Response  │     │ (含索引)        │
│              │     │                  │     │                 │
│ LazyColumn   │     │ /api/dynamics    │     │                 │
│ + 无限滚动    │     │ /api/dynamics/id │     │                 │
└──────────────┘     └──────────────────┘     └─────────────────┘
```

## 一、后端：Cursor 分页

### 问题

传统的 Offset 分页（`OFFSET n LIMIT m`）在大数据量下性能急剧下降：
- `OFFSET 10000 LIMIT 20` 需要扫描前 10000 条再跳过
- 深度翻页（如最后一页）性能极差

### 方案

使用基于 ID 的 Cursor 分页：

```sql
-- 第一页
SELECT * FROM dynamics ORDER BY is_pinned DESC, created_at DESC LIMIT 31;

-- 下一页 (cursor = 上一页最后一条的 id)
SELECT * FROM dynamics 
WHERE id < :cursor
ORDER BY is_pinned DESC, created_at DESC LIMIT 31;
```

**优势：**
- 每次查询都是 O(1) 复杂度（利用主键索引）
- 不受数据总量影响
- 新增数据不会导致翻页错位

### API 设计

```
GET /api/dynamics?cursor=0&limit=30&category=insight

Response:
{
  "items": [
    {
      "id": 123,
      "title": "...",
      "summary": "...",
      "category": "insight",
      "confidence": 0.85,
      "is_read": false,
      "is_pinned": true,
      "created_at": "2026-06-05T10:00:00"
      // 注意：不包含 content 字段
    }
  ],
  "next_cursor": 93,
  "has_more": true
}
```

**关键设计：**
- `cursor=0` 表示从最新开始
- `next_cursor` 返回下一页的游标值
- `has_more` 标记是否还有更多数据
- **列表响应不包含 `content`**（正文），只返回摘要信息

### Lean Response 策略

| 端点 | 返回字段 | 大小/条 |
|------|---------|--------|
| `GET /api/dynamics` (列表) | title, summary, category, metadata | ~200B |
| `GET /api/dynamics/{id}` (详情) | 全部字段含 content | ~2-5KB |

这样 30 条列表数据只有 ~6KB，而完整数据可能有 60-150KB。

### 数据库索引

```sql
-- dynamics 表索引
CREATE INDEX ix_dynamics_device_created ON dynamics(device_id, created_at);
CREATE INDEX ix_dynamics_category ON dynamics(category);
CREATE INDEX ix_dynamics_is_read ON dynamics(is_read);
CREATE INDEX ix_dynamics_is_pinned ON dynamics(is_pinned);
```

## 二、Android：本地缓存 + 无限滚动

### 本地缓存策略

```
App 启动
  ↓
读取 SharedPreferences 缓存 → 立即显示（0ms）
  ↓
请求服务端最新 30 条 → hash 对比 → 有变化才更新 UI + 写入缓存
```

**缓存实现：**

```kotlin
// 存储：SharedPreferences 中保存 JSON 数组
private fun saveToCache(items: List<UiDynamic>) {
    val jsonArray = JSONArray()
    for (item in items.take(100)) {  // 最多缓存 100 条
        jsonArray.put(JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("summary", item.summary)
            // ... 其他字段
        })
    }
    prefs.edit().putString("cached_items", jsonArray.toString()).apply()
}

// 读取：启动时从缓存加载
private fun loadFromCache() {
    val cached = prefs.getString("cached_items", null) ?: return
    val arr = JSONArray(cached)
    // 解析并更新 UI 状态
}
```

**为什么用 SharedPreferences 而不是 Room：**
- 数据结构简单（一个 JSON 数组）
- 不需要复杂查询
- 零依赖，无额外 ORM 学习成本
- 对于 100 条缓存数据，性能完全足够

### 无限滚动

```kotlin
// LazyColumn 底部自动触发加载
items(state.items, key = { it.id }) { item ->
    DynamicCard(item = item, ...)
}

// 到达底部时自动加载更多
if (state.hasMore && !state.loading) {
    item {
        LaunchedEffect(Unit) { viewModel.loadMore() }
        CircularProgressIndicator(...)
    }
}
```

**ViewModel 中的 loadMore：**

```kotlin
fun loadMore() {
    if (loadingMore || !hasMore || nextCursor == 0) return
    
    viewModelScope.launch {
        _state.value = _state.value.copy(loadingMore = true)
        val json = apiClient.getDynamicsPaginated(
            cursor = nextCursor, limit = 30, category = filter
        )
        val newItems = parseItems(json)
        val existingIds = _state.value.items.map { it.id }.toSet()
        val uniqueNew = newItems.filter { it.id !in existingIds }
        
        // 追加到列表
        val merged = _state.value.items + uniqueNew
        _state.value = _state.value.copy(
            items = merged,
            loadingMore = false,
            hasMore = json.optBoolean("has_more")
        )
        nextCursor = json.optInt("next_cursor", 0)
    }
}
```

### 轮询优化

**问题：** 每 60 秒轮询一次，如果数据没变化，Compose 会重新渲染导致抖动。

**方案：** Hash 对比，数据不变不更新。

```kotlin
fun refresh() {
    viewModelScope.launch {
        val json = apiClient.getDynamicsPaginated(cursor = 0, limit = 30)
        val items = parseItems(json)
        val newHash = items.hashCode()
        
        // 只在数据实际变化时更新状态
        if (newHash != _state.value.items.hashCode()) {
            _state.value = _state.value.copy(items = items, ...)
            saveToCache(items)
        } else {
            _state.value = _state.value.copy(loading = false)  // 仅停止加载指示器
        }
    }
}
```

## 三、Web 前端：无限滚动

### IntersectionObserver 方案

```tsx
// 观察底部哨兵元素
const observerRef = useRef<HTMLDivElement>(null);

useEffect(() => {
    const observer = new IntersectionObserver(
        (entries) => {
            if (entries[0].isIntersecting && hasMore && !loadingMore) {
                loadMore();  // 触发加载下一页
            }
        },
        { threshold: 0.1 }
    );
    if (observerRef.current) observer.observe(observerRef.current);
    return () => observer.disconnect();
}, [hasMore, loadingMore, nextCursor]);

// 列表底部的哨兵元素
{hasMore && (
    <div ref={observerRef}>
        {loadingMore && <LoadingSpinner />}
    </div>
)}
```

### 去重逻辑

```tsx
setItems(prev => {
    const existingIds = new Set(prev.map(i => i.id));
    const unique = newItems.filter(i => !existingIds.has(i.id));
    return [...prev, ...unique];
});
```

## 四、性能对比

| 指标 | 优化前 (Offset) | 优化后 (Cursor + Cache) |
|------|----------------|------------------------|
| 首屏加载 | 500-2000ms | < 200ms (缓存) |
| 翻到第 100 页 | 2-5s | < 100ms |
| 列表数据大小 (30条) | 60-150KB | ~6KB |
| 轮询抖动 | 每次重渲染 | 无变化时零渲染 |
| 内存占用 (1000条) | 全量加载 | 仅缓存 100 条 |

## 五、扩展考虑

### 当前方案的局限

- **SharedPreferences 缓存**：适合中小规模（< 数百条缓存），超大规模需迁移到 Room/SQLite
- **单字段 Cursor**：目前用 `id` 作为 cursor，如果需要按时间范围查询可能需要复合 cursor
- **无离线支持**：缓存仅用于加速首屏，不支持完全离线浏览

### 未来优化方向

1. **Room 数据库**：完整本地存储，支持离线浏览和全文搜索
2. **增量同步**：只拉取 `since_id` 之后的新数据，而非每次 30 条
3. **WebSocket 推送**：新动态实时推送到客户端，替代轮询
4. **图片缩略图**：Dynamics 中引用的图片使用缩略图列表展示
5. **分页预加载**：在用户滑动到 70% 时就开始预加载下一页
