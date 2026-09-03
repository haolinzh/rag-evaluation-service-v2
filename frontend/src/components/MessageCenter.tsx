import React, { useEffect, useState, useCallback } from 'react';
import { List, Button, Typography, Badge, Tag, Space, Empty, message } from 'antd';
import { ArrowLeftOutlined, ReloadOutlined, CheckOutlined, BellOutlined } from '@ant-design/icons';
import { fetchMessages, fetchUnreadCount, markMessagesRead } from '../api';
import type { AppNotification } from '../types';

interface Props {
  onBack: () => void;
  onReadAll?: () => void;
}

const TYPE_META: Record<string, { label: string; color: string }> = {
  demo_init: { label: '初始化', color: 'green' },
  evaluation: { label: '测评', color: 'blue' },
  document: { label: '文档', color: 'cyan' },
  user_manage: { label: '用户', color: 'purple' },
  role_manage: { label: '角色', color: 'gold' },
  chat: { label: '对话', color: 'geekblue' },
  report: { label: '报告', color: 'magenta' },
  cache: { label: '缓存', color: 'orange' },
  log: { label: '日志', color: 'volcano' },
  config: { label: '配置', color: 'blue' },
};

const formatTime = (s?: string) => {
  if (!s) return '—';
  return s.replace('T', ' ').split('.')[0];
};

const MessageCenter: React.FC<Props> = ({ onBack, onReadAll }) => {
  const [messagesList, setMessagesList] = useState<AppNotification[]>([]);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [list, count] = await Promise.all([fetchMessages(100), fetchUnreadCount()]);
      setMessagesList(list);
      setUnread(count);
    } catch {
      message.error('消息加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const handleMarkAllRead = async () => {
    try {
      await markMessagesRead();
      setMessagesList((prev) => prev.map((m) => ({ ...m, read: true })));
      setUnread(0);
      onReadAll?.();
      message.success('已全部标记为已读');
    } catch {
      message.error('操作失败');
    }
  };

  return (
    <div style={{ height: '100vh', overflowY: 'auto', padding: 24, boxSizing: 'border-box' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={onBack}>返回</Button>
        <Typography.Title level={4} style={{ margin: 0, flex: 1 }}><BellOutlined style={{ color: '#faad14' }} /> 消息中心</Typography.Title>
        <Button icon={<ReloadOutlined />} onClick={refresh} loading={loading}>刷新</Button>
        <Button icon={<CheckOutlined />} onClick={handleMarkAllRead} disabled={unread === 0}>全部已读</Button>
      </div>

      {messagesList.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无消息" style={{ marginTop: 80 }} />
      ) : (
        <List
          dataSource={messagesList}
          loading={loading}
          pagination={{ pageSize: 20, showSizeChanger: true, pageSizeOptions: [20, 50, 100] }}
          renderItem={(m) => {
            const meta = TYPE_META[m.type];
            return (
              <List.Item style={{ padding: '14px 8px', opacity: m.read ? 0.65 : 1 }}>
                <List.Item.Meta
                  avatar={!m.read ? <Badge status="processing" /> : <Badge status="default" />}
                  title={
                    <Space size={8}>
                      <span style={{ fontWeight: m.read ? 400 : 600 }}>{m.title}</span>
                      {meta ? <Tag color={meta.color} style={{ margin: 0 }}>{meta.label}</Tag> : <Tag style={{ margin: 0 }}>{m.type}</Tag>}
                    </Space>
                  }
                  description={
                    <div style={{ fontSize: 13, color: '#666' }}>
                      <div>{m.content}</div>
                      <div style={{ marginTop: 4, color: '#999', fontSize: 12 }}>
                        {m.actorName ? `操作人：${m.actorName}` : '系统'} · {formatTime(m.createdAt)}
                      </div>
                    </div>
                  }
                />
              </List.Item>
            );
          }}
        />
      )}
    </div>
  );
};

export default MessageCenter;
