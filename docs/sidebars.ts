import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docsSidebar: [
    'index',
    {
      type: 'category',
      label: '快速开始',
      collapsed: false,
      items: [
        'getting-started/index',
        'getting-started/prerequisites',
        'getting-started/first-run',
      ],
    },
    {
      type: 'category',
      label: '架构',
      items: [
        'architecture/index',
        'architecture/data-flow',
        'architecture/tech-stack',
      ],
    },
    {
      type: 'category',
      label: '后端',
      items: [
        'backend/index',
        'backend/api-reference',
        'backend/models',
        'backend/services',
        'backend/config',
      ],
    },
    {
      type: 'category',
      label: 'Android',
      items: [
        'android/index',
        'android/architecture',
        'android/screens',
        'android/sync',
      ],
    },
    {
      type: 'category',
      label: '前端',
      items: [
        'frontend/index',
        'frontend/pages',
      ],
    },
    {
      type: 'category',
      label: '功能特性',
      items: [
        'features/index',
        'features/screenshot-sync',
        'features/ai-analysis',
        'features/chat-agent',
        'features/dynamics',
        'features/memory',
        'features/push-notifications',
        'features/security',
      ],
    },
    {
      type: 'category',
      label: '部署',
      items: ['deployment/index'],
    },
    {
      type: 'category',
      label: '贡献',
      items: ['contributing/index'],
    },
  ],
};

export default sidebars;
