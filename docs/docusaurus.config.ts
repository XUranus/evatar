import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Evatar',
  tagline: '截图同步 & AI 分析助手 — 完整技术文档',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://xuranus.github.io',
  baseUrl: '/evatar/',

  onBrokenLinks: 'warn',

  markdown: {
    mermaid: true,
  },

  i18n: {
    defaultLocale: 'zh-CN',
    locales: ['zh-CN', 'en'],
    localeConfigs: {
      'zh-CN': {
        label: '简体中文',
        direction: 'ltr',
        htmlLang: 'zh-Hans',
      },
      en: {
        label: 'English',
        direction: 'ltr',
      },
    },
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          routeBasePath: '/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    colorMode: {
      defaultMode: 'dark',
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Evatar',
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: '文档',
        },
        {
          href: 'https://github.com',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: '文档',
          items: [
            { label: '快速开始', to: '/getting-started' },
            { label: '架构概览', to: '/architecture' },
            { label: 'API 参考', to: '/backend/api-reference' },
          ],
        },
        {
          title: '功能',
          items: [
            { label: '截图同步', to: '/features/screenshot-sync' },
            { label: 'AI 分析', to: '/features/ai-analysis' },
            { label: '聊天助手', to: '/features/chat-agent' },
          ],
        },
        {
          title: '更多',
          items: [
            { label: '部署指南', to: '/deployment' },
            { label: '贡献指南', to: '/contributing' },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Evatar. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'kotlin', 'bash', 'json'],
    },
  } satisfies Preset.ThemeConfig,

  themes: ['@docusaurus/theme-mermaid'],
};

export default config;
