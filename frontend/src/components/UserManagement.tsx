import React, { useState, useEffect } from 'react';
import { Table, Button, Tag, Typography, Space, message, Modal, Input, Select, Switch, Popconfirm, Tabs } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, EditOutlined, DeleteOutlined, TeamOutlined } from '@ant-design/icons';
import type { ManagedUser, Role, UserRequest } from '../types';
import { listUsers, listRoles, createUser, updateUser, deleteUser } from '../api';
import RoleManagement from './RoleManagement';

interface Props {
  onBack: () => void;
}

const emptyForm: UserRequest & { password?: string } = {
  username: '',
  password: '',
  displayName: '',
  department: '',
  enabled: true,
  roleCodes: [],
};

const UserManagement: React.FC<Props> = ({ onBack }) => {
  const [users, setUsers] = useState<ManagedUser[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<ManagedUser | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<UserRequest & { password?: string }>(emptyForm);
  const [saving, setSaving] = useState(false);

  const refresh = async () => {
    setLoading(true);
    try {
      setUsers(await listUsers());
    } catch {
      message.error('加载用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  const loadRoles = async () => {
    try {
      setRoles(await listRoles());
    } catch {
      // non-blocking: role select may be empty if insufficient permission
    }
  };

  useEffect(() => { refresh(); loadRoles(); }, []);

  const openCreate = () => {
    setEditing(null);
    setForm({ ...emptyForm, roleCodes: [] });
    setOpen(true);
  };

  const openEdit = (u: ManagedUser) => {
    setEditing(u);
    setForm({
      username: u.username,
      password: '',
      displayName: u.displayName ?? '',
      department: u.department ?? '',
      enabled: u.enabled,
      roleCodes: u.roleCodes,
    });
    setOpen(true);
  };

  const handleSave = async () => {
    if (!form.username?.trim()) {
      message.error('用户名不能为空');
      return;
    }
    setSaving(true);
    try {
      const payload: UserRequest = {
        username: form.username.trim(),
        password: form.password || undefined,
        displayName: form.displayName || undefined,
        department: form.department || undefined,
        enabled: form.enabled,
        roleCodes: form.roleCodes || [],
      };
      if (editing) {
        await updateUser(editing.id, payload);
        message.success('已更新用户');
      } else {
        await createUser(payload);
        message.success('已创建用户');
      }
      setOpen(false);
      refresh();
    } catch (e) {
      const msg = (e as { response?: { data?: { error?: string } } }).response?.data?.error;
      message.error(msg || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteUser(id);
      message.success('已删除');
      refresh();
    } catch (e) {
      const msg = (e as { response?: { data?: { error?: string } } }).response?.data?.error;
      message.error(msg || '删除失败');
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '用户名', dataIndex: 'username', key: 'username', width: 140 },
    { title: '姓名', dataIndex: 'displayName', key: 'displayName', width: 120, render: (v?: string) => v || '—' },
    { title: '部门', dataIndex: 'department', key: 'department', width: 140, render: (v?: string) => v || '—' },
    {
      title: '角色', dataIndex: 'roleCodes', key: 'roleCodes',
      render: (codes: string[]) => (
        <Space size={4} wrap>
          {(codes || []).map(c => <Tag key={c} color="blue">{c}</Tag>)}
        </Space>
      ),
    },
    {
      title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90,
      render: (v: boolean) => v ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '操作', key: 'action', width: 130,
      render: (_: unknown, r: ManagedUser) => (
        <Space size={4}>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>编辑</Button>
          <Popconfirm title="确认删除该用户？" onConfirm={() => handleDelete(r.id)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const usersTab = (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建用户</Button>
      </div>

      <Table
        rowKey="id"
        dataSource={users}
        columns={columns}
        loading={loading}
        pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 个用户` }}
      />

      <Modal
        title={editing ? `编辑用户 — ${editing.username}` : '新建用户'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSave}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>用户名</div>
            <Input value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} placeholder="登录用户名" />
          </div>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>
              {editing ? '密码（留空则不修改）' : '密码'}
            </div>
            <Input.Password value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} placeholder={editing ? '留空保持原密码' : '初始密码'} />
          </div>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>姓名</div>
            <Input value={form.displayName} onChange={e => setForm({ ...form, displayName: e.target.value })} placeholder="真实姓名" />
          </div>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>部门</div>
            <Input value={form.department} onChange={e => setForm({ ...form, department: e.target.value })} placeholder="所属部门" />
          </div>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>角色</div>
            <Select
              mode="multiple"
              style={{ width: '100%' }}
              value={form.roleCodes}
              onChange={v => setForm({ ...form, roleCodes: v })}
              options={roles.map(r => ({ value: r.code, label: `${r.name} (${r.code})` }))}
              placeholder="选择角色"
            />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 12, color: '#8c8c8c' }}>启用</span>
            <Switch checked={form.enabled} onChange={v => setForm({ ...form, enabled: v })} />
          </div>
        </div>
      </Modal>
    </div>
  );

  return (
    <div style={{ height: '100vh', overflowY: 'auto', padding: 24, boxSizing: 'border-box' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={onBack}>返回</Button>
        <Typography.Title level={4} style={{ margin: 0, flex: 1 }}><TeamOutlined style={{ color: '#1677ff' }} /> 用户管理</Typography.Title>
      </div>

      <Tabs
        items={[
          { key: 'users', label: '用户', children: usersTab },
          { key: 'roles', label: '角色', children: <RoleManagement embedded onBack={onBack} /> },
        ]}
      />
    </div>
  );
};

export default UserManagement;
