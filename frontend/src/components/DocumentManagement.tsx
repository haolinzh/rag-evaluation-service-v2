import React, { useState, useEffect } from 'react';
import { Table, Button, Drawer, Tag, Typography, Space, Empty, message, Tooltip, Modal, Select, InputNumber, Input } from 'antd';
import { ArrowLeftOutlined, EyeOutlined, DownloadOutlined, EditOutlined, FileTextOutlined } from '@ant-design/icons';
import type { DocumentMeta, ChunkPreview, ChunkConfig } from '../types';
import { listDocuments, getDocumentChunks, downloadDocument, reprocessDocument } from '../api';

interface Props {
  onBack: () => void;
}

const formatTime = (s?: string) => {
  if (!s) return '—';
  return s.replace('T', ' ').split('.')[0];
};

const formatSize = (bytes?: number) => {
  if (bytes == null) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const isPending = (s?: string) => s === 'QUEUED' || s === 'PROCESSING';

const DocumentManagement: React.FC<Props> = ({ onBack }) => {
  const [documents, setDocuments] = useState<DocumentMeta[]>([]);
  const [loading, setLoading] = useState(false);
  const [previewDoc, setPreviewDoc] = useState<DocumentMeta | null>(null);
  const [previews, setPreviews] = useState<ChunkPreview[]>([]);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [editingDoc, setEditingDoc] = useState<DocumentMeta | null>(null);
  const [editForm, setEditForm] = useState<ChunkConfig>({ splitMode: 'size', chunkSize: 1000, delimiter: '', overlap: 150 });
  const [editVisibility, setEditVisibility] = useState<'PUBLIC' | 'DEPARTMENT' | 'EXECUTIVE' | 'PRIVATE'>('DEPARTMENT');
  const [saving, setSaving] = useState(false);

  const VISIBILITY_OPTIONS = [
    { value: 'PUBLIC', label: '所有人可见' },
    { value: 'DEPARTMENT', label: '本部门可见' },
    { value: 'EXECUTIVE', label: '高管可见' },
    { value: 'PRIVATE', label: '仅作者可见' },
  ];
  const visibilityColor: Record<string, string> = {
    PUBLIC: 'green', DEPARTMENT: 'blue', EXECUTIVE: 'gold', PRIVATE: 'red',
  };

  const refresh = async () => {
    setLoading(true);
    try {
      setDocuments(await listDocuments());
    } catch {
      message.error('加载文档列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { refresh(); }, []);

  useEffect(() => {
    if (!documents.some(d => isPending(d.status))) return;
    const timer = setInterval(refresh, 2500);
    return () => clearInterval(timer);
  }, [documents]);

  const openPreview = async (doc: DocumentMeta) => {
    setPreviewDoc(doc);
    setPreviews([]);
    setPreviewLoading(true);
    try {
      setPreviews(await getDocumentChunks(doc.id));
    } catch {
      message.error('加载 chunk 预览失败');
    } finally {
      setPreviewLoading(false);
    }
  };

  const openEdit = (doc: DocumentMeta) => {
    setEditingDoc(doc);
    setEditForm({
      splitMode: doc.splitMode === 'delimiter' ? 'delimiter' : 'size',
      chunkSize: doc.chunkSize ?? 1000,
      delimiter: doc.delimiter ?? '',
      overlap: doc.overlap ?? 150,
    });
    setEditVisibility(doc.visibility || 'DEPARTMENT');
  };

  const handleSave = async () => {
    if (!editingDoc) return;
    setSaving(true);
    try {
      await reprocessDocument(editingDoc.id, editForm, editVisibility);
      message.success('已提交重切分，后台处理中');
      setEditingDoc(null);
      refresh();
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } }).response?.data?.message;
      message.error(msg || '重切分失败');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { title: '编号', dataIndex: 'id', key: 'id', width: 70, sorter: (a: DocumentMeta, b: DocumentMeta) => a.id - b.id },
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true, width: 200, sorter: (a: DocumentMeta, b: DocumentMeta) => a.fileName.localeCompare(b.fileName) },
    {
      title: '状态', key: 'status', width: 100,
      render: (_: unknown, r: DocumentMeta) => isPending(r.status)
        ? <Tag color="processing">处理中</Tag>
        : r.status === 'FAILED'
          ? <Tooltip title={r.errorMessage || '处理失败'}><Tag color="error">失败</Tag></Tooltip>
          : <Tag color="success">已就绪</Tag>,
      sorter: (a: DocumentMeta, b: DocumentMeta) => (a.status ?? '').localeCompare(b.status ?? ''),
    },
    {
      title: '可见性', dataIndex: 'visibility', key: 'visibility', width: 110,
      render: (v?: DocumentMeta['visibility']) => v
        ? <Tag color={visibilityColor[v]}>{VISIBILITY_OPTIONS.find(o => o.value === v)?.label || v}</Tag>
        : '—',
      sorter: (a: DocumentMeta, b: DocumentMeta) => (a.visibility ?? '').localeCompare(b.visibility ?? ''),
    },
    {
      title: '作者', dataIndex: 'ownerName', key: 'ownerName', width: 130,
      render: (v?: string, r?: DocumentMeta) => v
        ? `${v}${r?.ownerDepartment ? ` (${r.ownerDepartment})` : ''}` : '—',
    },
    { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 160, render: (v: string) => formatTime(v), sorter: (a: DocumentMeta, b: DocumentMeta) => (a.updatedAt ?? '').localeCompare(b.updatedAt ?? '') },
    {
      title: 'Chunk 方式', key: 'splitMode', width: 120,
      render: (_: unknown, r: DocumentMeta) => r.splitMode === 'delimiter'
        ? <Tag color="purple">特殊字符{r.delimiter ? ` (${r.delimiter})` : ''}</Tag>
        : r.splitMode === 'size' ? <Tag color="blue">按大小</Tag> : '—',
      sorter: (a: DocumentMeta, b: DocumentMeta) => (a.splitMode ?? '').localeCompare(b.splitMode ?? ''),
    },
    { title: 'Chunk 大小', dataIndex: 'chunkSize', key: 'chunkSize', width: 100, render: (v?: number) => v != null ? `${v} 字符` : '—', sorter: (a: DocumentMeta, b: DocumentMeta) => (a.chunkSize ?? 0) - (b.chunkSize ?? 0) },
    { title: 'Overlap', dataIndex: 'overlap', key: 'overlap', width: 90, render: (v?: number) => v != null ? `${v} 字符` : '—', sorter: (a: DocumentMeta, b: DocumentMeta) => (a.overlap ?? 0) - (b.overlap ?? 0) },
    { title: 'Chunk 数', dataIndex: 'chunkCount', key: 'chunkCount', width: 90, render: (v?: number) => v != null ? v : '—', sorter: (a: DocumentMeta, b: DocumentMeta) => (a.chunkCount ?? -1) - (b.chunkCount ?? -1) },
    { title: '向量模型', dataIndex: 'embeddingModel', key: 'embeddingModel', width: 160, render: (v?: string) => v || '—', sorter: (a: DocumentMeta, b: DocumentMeta) => (a.embeddingModel ?? '').localeCompare(b.embeddingModel ?? '') },
    { title: '维度', dataIndex: 'embeddingDimension', key: 'embeddingDimension', width: 80, render: (v?: number) => v != null ? v : '—', sorter: (a: DocumentMeta, b: DocumentMeta) => (a.embeddingDimension ?? 0) - (b.embeddingDimension ?? 0) },
    { title: '文件大小', dataIndex: 'fileSize', key: 'fileSize', width: 100, render: (v?: number) => formatSize(v), sorter: (a: DocumentMeta, b: DocumentMeta) => (a.fileSize ?? 0) - (b.fileSize ?? 0) },
    {
      title: '操作', key: 'action', width: 210,
      render: (_: unknown, r: DocumentMeta) => (
        <Space size={4}>
          <Button size="small" icon={<EyeOutlined />} onClick={() => openPreview(r)}>预览</Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)} disabled={isPending(r.status)}>重切分</Button>
          <Button size="small" icon={<DownloadOutlined />} onClick={() => { downloadDocument(r.id).catch(() => message.error('下载失败')); }}>下载</Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ height: '100vh', overflowY: 'auto', padding: 24, boxSizing: 'border-box' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={onBack}>返回</Button>
        <Typography.Title level={4} style={{ margin: 0, flex: 1 }}><FileTextOutlined style={{ color: '#1677ff' }} /> 文档管理</Typography.Title>
      </div>

      <Table
        rowKey="id"
        dataSource={documents}
        columns={columns}
        loading={loading}
        pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 个文档` }}
        scroll={{ x: 1720, y: 'calc(100vh - 220px)' }}
      />

      <Modal
        title={editingDoc ? `重切分 — ${editingDoc.fileName}` : '重切分'}
        open={!!editingDoc}
        onCancel={() => setEditingDoc(null)}
        onOk={handleSave}
        okText="保存并重建"
        cancelText="取消"
        confirmLoading={saving}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>可见性</div>
            <Select
              value={editVisibility}
              onChange={(v) => setEditVisibility(v as 'PUBLIC' | 'DEPARTMENT' | 'EXECUTIVE' | 'PRIVATE')}
              style={{ width: '100%' }}
              options={VISIBILITY_OPTIONS}
            />
          </div>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>切分方式</div>
            <Select
              value={editForm.splitMode}
              onChange={v => setEditForm({ ...editForm, splitMode: v })}
              style={{ width: '100%' }}
              options={[
                { value: 'size', label: '按大小 (size)' },
                { value: 'delimiter', label: '特殊字符 (分隔符)' },
              ]}
            />
          </div>
          {editForm.splitMode === 'size' ? (
            <>
              <div>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Chunk 大小（字符）</div>
                <InputNumber min={50} max={5000} value={editForm.chunkSize} onChange={v => setEditForm({ ...editForm, chunkSize: v ?? 1000 })} style={{ width: '100%' }} />
              </div>
              <div>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Overlap（字符）</div>
                <InputNumber min={0} max={500} value={editForm.overlap} onChange={v => setEditForm({ ...editForm, overlap: v ?? 150 })} style={{ width: '100%' }} />
              </div>
            </>
          ) : (
            <div>
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>分隔符</div>
              <Input value={editForm.delimiter} onChange={e => setEditForm({ ...editForm, delimiter: e.target.value })} placeholder="如 ## 或 ###" />
            </div>
          )}
        </div>
      </Modal>

      <Drawer
        title={previewDoc ? `Chunk 预览 — ${previewDoc.fileName}` : 'Chunk 预览'}
        open={!!previewDoc}
        onClose={() => setPreviewDoc(null)}
        width={520}
      >
        {previewLoading && <div style={{ textAlign: 'center', padding: 24 }}>加载中…</div>}
        {!previewLoading && previews.length === 0 && <Empty description="暂无 chunk" />}
        {!previewLoading && previews.length > 0 && (
          <div style={{ marginBottom: 8, color: '#8c8c8c', fontSize: 12 }}>共 {previews.length} 个 chunk</div>
        )}
        {!previewLoading && previews.map(p => (
          <div key={p.chunkIndex} style={{ marginBottom: 12, padding: 10, background: '#fafafa', borderRadius: 8 }}>
            <Space size={6} style={{ marginBottom: 4 }}>
              <Tag color="blue">#{p.chunkIndex}</Tag>
              {p.chapter && <Tag color="green">{p.chapter}</Tag>}
              {p.section && <Tag color="cyan">{p.section}</Tag>}
            </Space>
            <div style={{ fontSize: 13, color: '#555', whiteSpace: 'pre-wrap' }}>{p.content}</div>
          </div>
        ))}
      </Drawer>
    </div>
  );
};

export default DocumentManagement;
