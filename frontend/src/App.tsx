import React, { useState, useEffect } from 'react';
import { Layout, theme, Grid, Button, Drawer, Space, Dropdown } from 'antd';
import { FileTextOutlined, BarChartOutlined, SettingOutlined, ExperimentOutlined, DashboardOutlined, TeamOutlined, SafetyCertificateOutlined, LogoutOutlined, LoginOutlined, UserOutlined, ControlOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { Group, Panel, Separator } from 'react-resizable-panels';
import DocumentPanel from './components/DocumentPanel';
import ChatPanel from './components/ChatPanel';
import MetricsPanel from './components/MetricsPanel';
import LogPanel from './components/LogPanel';
import DocumentManagement from './components/DocumentManagement';
import LogManagement from './components/LogManagement';
import ConfigPage from './components/ConfigPage';
import EvaluationPage from './components/EvaluationPage';
import OpsPage from './components/OpsPage';
import LoginModal from './components/LoginModal';
import UserManagement from './components/UserManagement';
import RoleManagement from './components/RoleManagement';
import AboutPage from './components/AboutPage';
import type { DocumentMeta, AuthUser } from './types';
import { listDocuments, fetchConfig, updateRetrievalMode, getToken, getCachedUser, setAuth, fetchMe, logout, fetchGuestPermissions } from './api';

const { Header, Content } = Layout;

type View = 'main' | 'documents' | 'logs' | 'config' | 'eval' | 'ops' | 'users' | 'roles' | 'about';

const VIEW_HASHES: Record<View, string> = {
  main: '/',
  documents: '/documents',
  logs: '/logs',
  config: '/config',
  eval: '/eval',
  ops: '/ops',
  users: '/users',
  roles: '/roles',
  about: '/about',
};

const parseViewFromHash = (): View => {
  const h = window.location.hash.replace(/^#/, '');
  const entry = (Object.entries(VIEW_HASHES) as [View, string][]).find(([, v]) => v === h);
  return entry ? entry[0] : 'main';
};

const App: React.FC = () => {
  const [documents, setDocuments] = useState<DocumentMeta[]>([]);
  const [retrievalMode, setRetrievalMode] = useState<string>('hybrid');
  const [webEnabled, setWebEnabled] = useState(false);
  const [chatMode, setChatMode] = useState<'workflow' | 'agent'>('workflow');
  const [view, setView] = useState<View>(parseViewFromHash);
  const [docsOpen, setDocsOpen] = useState(false);
  const [metricsOpen, setMetricsOpen] = useState(false);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [adminLoginOpen, setAdminLoginOpen] = useState(false);
  const { token: { colorBgContainer } } = theme.useToken();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const [guestPermissions, setGuestPermissions] = useState<string[]>(['document:read:public', 'config:view', 'ops:view', 'report:view']);
  const has = (p: string) => (user ? user.permissions : guestPermissions).includes(p);

  useEffect(() => {
    const target = VIEW_HASHES[view];
    if (window.location.hash !== '#' + target) {
      window.location.hash = target;
    }
  }, [view]);

  useEffect(() => {
    const onHashChange = () => setView(parseViewFromHash());
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  useEffect(() => {
    const token = getToken();
    if (!token) {
      setAuthReady(true);
      return;
    }
    const cached = getCachedUser();
    if (cached) setUser(cached);
    fetchMe()
      .then((u) => { setUser(u); setAuth(token, u); })
      .catch(() => {})
      .finally(() => setAuthReady(true));
  }, []);

  useEffect(() => {
    if (user) return;
    fetchGuestPermissions().then(setGuestPermissions).catch(() => {});
  }, [user]);

  const refreshDocuments = () => {
    listDocuments().then(setDocuments).catch(console.error);
  };

  useEffect(() => { refreshDocuments(); }, [user]);

  useEffect(() => {
    const onExpired = () => setUser(null);
    window.addEventListener('auth-expired', onExpired);
    return () => window.removeEventListener('auth-expired', onExpired);
  }, []);

  useEffect(() => {
    if (!documents.some(d => d.status === 'PENDING')) return;
    const timer = setInterval(refreshDocuments, 2500);
    return () => clearInterval(timer);
  }, [documents]);

  useEffect(() => {
    if (!has('config:view')) return;
    fetchConfig()
      .then((c) => {
        setRetrievalMode(c.retrieval.mode);
        setWebEnabled(c.webSearch.enabled);
        setChatMode(c.chatMode === 'agent' ? 'agent' : 'workflow');
      })
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const handleModeChange = (mode: string) => {
    setRetrievalMode(mode);
    updateRetrievalMode(mode).catch(() => {});
  };

  const handleLogin = (u: AuthUser) => {
    setUser(u);
    setLoginOpen(false);
  };

  const handleLogout = async () => {
    await logout();
    setUser(null);
    setView('main');
  };

  if (!authReady) {
    return (
      <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>
        加载中…
      </div>
    );
  }

  if (view === 'documents') return <DocumentManagement onBack={() => setView('main')} />;
  if (view === 'logs') return <LogManagement onBack={() => setView('main')} canClear={has('log:clear')} />;
  if (view === 'config') return <ConfigPage onBack={() => setView('main')} onSaved={(mode, web, cm) => { setRetrievalMode(mode); setWebEnabled(web); setChatMode(cm === 'agent' ? 'agent' : 'workflow'); }} canEdit={has('config:edit')} />;
  if (view === 'eval') return <EvaluationPage onBack={() => setView('main')} />;
  if (view === 'ops') return <OpsPage onBack={() => setView('main')} />;
  if (view === 'users') return <UserManagement onBack={() => setView('main')} />;
  if (view === 'roles') return <RoleManagement onBack={() => setView('main')} />;
  if (view === 'about') return <AboutPage onBack={() => setView('main')} />;

  const documentPanel = (
    <DocumentPanel
      documents={documents}
      onRefresh={refreshDocuments}
      onOpenManagement={() => setView('documents')}
      canManageDocs={has('document:manage:own') || has('document:manage:all')}
      onRequireLogin={() => setLoginOpen(true)}
    />
  );
  const chatPanel = <ChatPanel retrievalMode={retrievalMode} onRetrievalModeChange={handleModeChange} isGuest={!user} canWebSearch={webEnabled && has('chat:web')} chatMode={chatMode} />;
  const canViewLogs = !!user;
  const hasMetrics = has('report:view') || canViewLogs;
  const metricsPanel = hasMetrics ? (
    <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', gap: 16, overflowY: 'auto' }}>
      {has('report:view') && <MetricsPanel canClearCache={has('cache:clear')} />}
      {canViewLogs && <LogPanel onOpenManagement={() => setView('logs')} canClear={has('log:clear')} />}
    </div>
  ) : (
    <div style={{ flex: 1, minHeight: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999', padding: 24, textAlign: 'center' }}>
      无运维指标查看权限
    </div>
  );

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <style>{`
        .rag-sep { width: 6px; background: #f0f0f0; transition: background 0.15s ease; }
        .rag-sep[data-separator="hover"], .rag-sep[data-separator="active"] { background: #1677ff; }
        .rag-sep-v { height: 6px; background: #f0f0f0; transition: background 0.15s ease; }
        .rag-sep-v[data-separator="hover"], .rag-sep-v[data-separator="active"] { background: #1677ff; }
      `}</style>

      <Header style={{
        color: '#fff', fontSize: 20, fontWeight: 600,
        display: 'flex', alignItems: 'center', padding: '0 24px', gap: 12,
      }}>
        {isMobile && (
          <Button type="text" icon={<FileTextOutlined />} onClick={() => setDocsOpen(true)} style={{ color: '#fff' }}>
            文档
          </Button>
        )}
        <span style={{ flex: 1, textAlign: isMobile ? 'center' : 'left' }}>RAG 知识库问答系统-V2</span>
        {has('evaluation:use') && (
          <Button type="text" icon={<ExperimentOutlined />} onClick={() => setView('eval')} style={{ color: '#fff' }}>
            测评
          </Button>
        )}
        {has('ops:view') && (
          <Button type="text" icon={<DashboardOutlined />} onClick={() => setView('ops')} style={{ color: '#fff' }}>
            运维
          </Button>
        )}
        {has('config:view') && (
          <Button type="text" icon={<SettingOutlined />} onClick={() => setView('config')} style={{ color: '#fff' }}>
            配置
          </Button>
        )}
        {has('user:manage') && (
          <Button type="text" icon={<TeamOutlined />} onClick={() => setView('users')} style={{ color: '#fff' }}>
            用户
          </Button>
        )}
        {has('role:manage') && (
          <Button type="text" icon={<SafetyCertificateOutlined />} onClick={() => setView('roles')} style={{ color: '#fff' }}>
            角色
          </Button>
        )}
        {isMobile && hasMetrics && (
          <Button type="text" icon={<BarChartOutlined />} onClick={() => setMetricsOpen(true)} style={{ color: '#fff' }}>
            指标
          </Button>
        )}
        {!has('user:manage') && (
          <Button type="text" icon={<ControlOutlined />} onClick={() => setAdminLoginOpen(true)} style={{ color: '#fff' }}>
            管理
          </Button>
        )}
        {user ? (
          <Dropdown
            menu={{
              items: [
                {
                  key: 'logout',
                  icon: <LogoutOutlined />,
                  label: '登出',
                  onClick: handleLogout,
                },
              ],
            }}
          >
            <span style={{ color: '#fff', fontSize: 14, cursor: 'pointer', opacity: 0.9 }}>
              <UserOutlined /> {user.displayName || user.username}{user.department ? ` · ${user.department}` : ''}
            </span>
          </Dropdown>
        ) : (
          <Space size={8} style={{ color: '#fff', fontSize: 14 }}>
            <span style={{ opacity: 0.9 }}>
              <UserOutlined /> 游客
            </span>
            <Button type="text" icon={<LoginOutlined />} onClick={() => setLoginOpen(true)} style={{ color: '#fff' }}>
              登录
            </Button>
          </Space>
        )}
        <Button type="text" icon={<InfoCircleOutlined />} onClick={() => setView('about')} style={{ color: '#fff' }}>
          关于
        </Button>
      </Header>

      {isMobile ? (
        <Content style={{ padding: 16, display: 'flex', flexDirection: 'column', minHeight: 0, flex: 1, overflow: 'hidden' }}>
          {chatPanel}
          <Drawer title="文档" placement="left" width={320} open={docsOpen} onClose={() => setDocsOpen(false)}>
            {documentPanel}
          </Drawer>
          <Drawer title="运维指标" placement="right" width={320} open={metricsOpen} onClose={() => setMetricsOpen(false)}>
            {metricsPanel}
          </Drawer>
        </Content>
      ) : (
        <Group orientation="horizontal" id="rag-main" style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
          <Panel id="documents" defaultSize="20" minSize="15" maxSize="35" style={{ padding: 16, background: colorBgContainer, display: 'flex', flexDirection: 'column' }}>
            {documentPanel}
          </Panel>
          <Separator className="rag-sep" />
          <Panel id="chat" defaultSize="56" minSize="30" style={{ padding: 16, display: 'flex', flexDirection: 'column' }}>
            {chatPanel}
          </Panel>
          <Separator className="rag-sep" />
          <Panel id="metrics" defaultSize="24" minSize="18" maxSize="32" style={{ padding: 16, background: colorBgContainer, display: 'flex', flexDirection: 'column' }}>
            {metricsPanel}
          </Panel>
        </Group>
      )}

      <LoginModal open={loginOpen} onClose={() => setLoginOpen(false)} onLogin={handleLogin} />
      <LoginModal open={adminLoginOpen} onClose={() => setAdminLoginOpen(false)} onLogin={handleLogin} adminOnly />
    </Layout>
  );
};

export default App;
