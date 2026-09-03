import React, { useState, useEffect } from 'react';
import { Table, Button, Tag, Typography, Space, message, Modal, Input, Checkbox, Popconfirm } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, EditOutlined, DeleteOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import type { Role, Permission, RoleRequest } from '../types';
import { listRoles, fetchPermissions, createRole, updateRole, deleteRole } from '../api';

interface Props {
  onBack: () => void;
  embedded?: boolean;
}

const RoleManagement: React.FC<Props> = ({ onBack, embedded = false }) => {
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<Role | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<RoleRequest>({ code: '', name: '', description: '', permissionCodes: [] });
  const [saving, setSaving] = useState(false);

  const refresh = async () => {
    setLoading(true);
    try {
      setRoles(await listRoles());
    } catch {
      message.error('加载角色列表失败');
    } finally {
      setLoading(false);
    }
  };

  const loadPermissions = async () => {
    try {
      setPermissions(await fetchPermissions());
    } catch {
      message.error('加载权限目录失败');
    }
  };

  useEffect(() => { refresh(); loadPermissions(); }, []);

  const openCreate = () => {
    setEditing(null);
    setForm({ code: '', name: '', description: '', permissionCodes: [] });
    setOpen(true);
  };

  const openEdit = (r: Role) => {
    setEditing(r);
    setForm({ code: r.code, name: r.name, description: r.description ?? undefined, permissionCodes: r.permissionCodes });
    setOpen(true);
  };

  const handleSave = async () => {
    if (!form.code?.trim() || !form.name?.trim()) {
      message.error('code 和名称不能为空');
      return;
    }
    setSaving(true);
    try {
      const payload: RoleRequest = {
        code: form.code.trim(),
        name: form.name.trim(),
        description: form.description || undefined,
        permissionCodes: form.permissionCodes || [],
      };
      if (editing) {
        await updateRole(editing.id, payload);
        message.success('已更新角色');
      } else {
        await createRole(payload);
        message.success('已创建角色');
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
      await deleteRole(id);
      message.success('已删除');
      refresh();
    } catch (e) {
      const msg = (e as { response?: { data?: { error?: string } } }).response?.data?.error;
      message.error(msg || '删除失败');
    }
  };

  const groups = Array.from(new Set(permissions.map(p => p.group)));

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: 'Code', dataIndex: 'code', key: 'code', width: 140, render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: '名称', dataIndex: 'name', key: 'name', width: 140 },
    { title: '描述', dataIndex: 'description', key: 'description', render: (v?: string) => v || '—' },
    {
      title: '权限数', dataIndex: 'permissionCodes', key: 'permissionCodes', width: 90,
      render: (codes: string[]) => (codes || []).length,
    },
    {
      title: '类型', dataIndex: 'builtin', key: 'builtin', width: 90,
      render: (v: boolean) => v ? <Tag color="gold">内置</Tag> : <Tag>自定义</Tag>,
    },
    {
      title: '操作', key: 'action', width: 130,
      render: (_: unknown, r: Role) => (
        <Space size={4}>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>编辑</Button>
          <Popconfirm title="确认删除该角色？" onConfirm={() => handleDelete(r.id)} disabled={r.builtin}>
            <Button size="small" danger icon={<DeleteOutlined />} disabled={r.builtin}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={embedded ? undefined : { height: '100vh', overflowY: 'auto', padding: 24, boxSizing: 'border-box' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        {!embedded && <Button icon={<ArrowLeftOutlined />} onClick={onBack}>返回</Button>}
        {!embedded && <Typography.Title level={4} style={{ margin: 0, flex: 1 }}><SafetyCertificateOutlined style={{ color: '#722ed1' }} /> 角色管理</Typography.Title>}
        {embedded && <div style={{ flex: 1 }} />}
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建角色</Button>
      </div>

      <Table
        rowKey="id"
        dataSource={roles}
        columns={columns}
        loading={loading}
        pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 个角色` }}
      />

      <Modal
        title={editing ? `编辑角色 — ${editing.name}` : '新建角色'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSave}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        width={640}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', gap: 12 }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Code（唯一标识）</div>
              <Input value={form.code} disabled={!!editing?.builtin} onChange={e => setForm({ ...form, code: e.target.value })} placeholder="如 AUDITOR" />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>名称</div>
              <Input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="角色名称" />
            </div>
          </div>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>描述</div>
            <Input value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} placeholder="可选描述" />
          </div>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 8 }}>权限</div>
            <div style={{ maxHeight: 320, overflowY: 'auto', border: '1px solid #f0f0f0', borderRadius: 8, padding: 12 }}>
              {groups.map(g => {
                const groupCodes = permissions.filter(p => p.group === g).map(p => p.code);
                const groupValue = (form.permissionCodes || []).filter(c => groupCodes.includes(c));
                return (
                  <div key={g} style={{ marginBottom: 12 }}>
                    <div style={{ fontWeight: 600, marginBottom: 6 }}>{g}</div>
                    <Checkbox.Group
                      value={groupValue}
                      onChange={v => {
                        const rest = (form.permissionCodes || []).filter(c => !groupCodes.includes(c));
                        setForm({ ...form, permissionCodes: [...rest, ...(v as string[])] });
                      }}
                      style={{ display: 'flex', flexDirection: 'column', gap: 4 }}
                      options={permissions.filter(p => p.group === g).map(p => ({ value: p.code, label: `${p.name} (${p.code})` }))}
                    />
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default RoleManagement;
