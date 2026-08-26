import React, { useState } from 'react';
import { Modal, Form, Input, Button, Typography, message, Alert } from 'antd';
import { LockOutlined, UserOutlined, IdcardOutlined, TeamOutlined } from '@ant-design/icons';
import { login, register, setAuth } from '../api';
import type { AuthUser } from '../types';

interface Props {
  open: boolean;
  onClose: () => void;
  onLogin: (user: AuthUser) => void;
  adminOnly?: boolean;
}

const errMsg = (e: unknown) =>
  (e as { response?: { data?: { error?: string } } }).response?.data?.error;

const LoginModal: React.FC<Props> = ({ open, onClose, onLogin, adminOnly = false }) => {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [loading, setLoading] = useState(false);

  const handleLogin = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const { token, user } = await login(values.username, values.password, adminOnly);
      setAuth(token, user);
      onLogin(user);
    } catch (err) {
      message.error(errMsg(err) || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (values: {
    username: string;
    password: string;
    displayName?: string;
    department?: string;
  }) => {
    setLoading(true);
    try {
      const { token, user } = await register({
        username: values.username,
        password: values.password,
        displayName: values.displayName,
        department: values.department,
      });
      setAuth(token, user);
      message.success('注册成功，已自动登录');
      onLogin(user);
    } catch (err) {
      message.error(errMsg(err) || '注册失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      width={380}
      destroyOnClose
    >
      <Typography.Title level={4} style={{ textAlign: 'center', marginBottom: 4 }}>
        {adminOnly ? '管理登录' : 'RAG 知识库问答系统-V2'}
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ textAlign: 'center', marginBottom: 24 }}>
        {adminOnly ? '仅限管理员账号登录' : mode === 'login' ? '请登录后使用' : '注册新账号'}
      </Typography.Paragraph>

      {adminOnly && (
        <Alert
          type="info"
          showIcon
          message="演示账号"
          description="用户名 admin，密码 admin"
          style={{ marginBottom: 16 }}
        />
      )}

      {mode === 'login' ? (
        <Form onFinish={handleLogin} layout="vertical">
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" size="large" autoFocus />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" size="large" block loading={loading}>
              登录
            </Button>
          </Form.Item>
          {!adminOnly && (
            <div style={{ textAlign: 'center' }}>
              <Button type="link" onClick={() => setMode('register')}>没有账号？去注册</Button>
            </div>
          )}
        </Form>
      ) : (
        <Form onFinish={handleRegister} layout="vertical">
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" size="large" autoFocus />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }, { min: 6, message: '密码至少 6 位' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码（至少 6 位）" size="large" />
          </Form.Item>
          <Form.Item
            name="confirm"
            dependencies={['password']}
            rules={[
              { required: true, message: '请再次输入密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) return Promise.resolve();
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="确认密码" size="large" />
          </Form.Item>
          <Form.Item name="displayName">
            <Input prefix={<IdcardOutlined />} placeholder="姓名（可选）" size="large" />
          </Form.Item>
          <Form.Item name="department">
            <Input prefix={<TeamOutlined />} placeholder="部门（可选）" size="large" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" size="large" block loading={loading}>
              注册
            </Button>
          </Form.Item>
          <div style={{ textAlign: 'center' }}>
            <Button type="link" onClick={() => setMode('login')}>已有账号？去登录</Button>
          </div>
        </Form>
      )}
    </Modal>
  );
};

export default LoginModal;
