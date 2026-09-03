import React, { useState } from 'react';
import { Upload, Button, List, Popconfirm, Select, Space, Typography, message, Tag, Progress, Input, InputNumber, Tooltip, Modal, Segmented } from 'antd';
import { DeleteOutlined, ReloadOutlined, InboxOutlined, FolderOpenOutlined, DownloadOutlined, FileTextOutlined } from '@ant-design/icons';
import type { DocumentMeta } from '../types';
import { uploadDocument, deleteDocument, downloadDocument } from '../api';

const { Dragger } = Upload;
const { Text } = Typography;

interface Props {
  documents: DocumentMeta[];
  onRefresh: () => void;
  onOpenManagement: () => void;
  canManageDocs: boolean;
  onRequireLogin: () => void;
}

const labelStyle: React.CSSProperties = { fontSize: 12, color: '#8c8c8c', marginBottom: 4 };

const VISIBILITY_OPTIONS = [
  { value: 'PUBLIC', label: '所有人可见' },
  { value: 'DEPARTMENT', label: '本部门可见' },
  { value: 'EXECUTIVE', label: '高管可见' },
  { value: 'PRIVATE', label: '仅作者可见' },
];

const visibilityColor: Record<string, string> = {
  PUBLIC: 'green',
  DEPARTMENT: 'blue',
  EXECUTIVE: 'gold',
  PRIVATE: 'red',
};

const DocumentPanel: React.FC<Props> = ({ documents, onRefresh, onOpenManagement, canManageDocs, onRequireLogin }) => {
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [splitMode, setSplitMode] = useState<'size' | 'delimiter'>('size');
  const [chunkSize, setChunkSize] = useState(1000);
  const [delimiter, setDelimiter] = useState('');
  const [overlap, setOverlap] = useState(150);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [pendingVisibility, setPendingVisibility] = useState<'PUBLIC' | 'DEPARTMENT' | 'EXECUTIVE' | 'PRIVATE'>('DEPARTMENT');
  const [visibilityModalOpen, setVisibilityModalOpen] = useState(false);

  const doUpload = async (file: File, vis: string) => {
    setUploading(true);
    setProgress(0);
    try {
      await uploadDocument(file, { splitMode, chunkSize, delimiter, overlap }, vis, setProgress);
      message.success(`${file.name} 已上传，后台处理中`);
      onRefresh();
    } catch (err) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message;
      message.error(msg || `上传失败: ${file.name}`);
    } finally {
      setUploading(false);
      setProgress(0);
    }
  };

  const handleUpload = (file: File) => {
    if (!canManageDocs) {
      message.info('请先登录后上传文档');
      onRequireLogin();
      return false;
    }
    setPendingFile(file);
    setPendingVisibility('DEPARTMENT');
    setVisibilityModalOpen(true);
    return false; // prevent default upload
  };

  const confirmUpload = () => {
    const file = pendingFile;
    const vis = pendingVisibility;
    setVisibilityModalOpen(false);
    setPendingFile(null);
    if (!file) return;
    if (documents.some(d => d.fileName === file.name)) {
      Modal.confirm({
        title: '同名文件已存在',
        content: `「${file.name}」已存在，覆盖后将替换原文件及其分块，且无法恢复。是否覆盖？`,
        okText: '覆盖',
        okButtonProps: { danger: true },
        cancelText: '取消',
        onOk: () => doUpload(file, vis),
      });
    } else {
      doUpload(file, vis);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteDocument(id);
      message.success('已删除');
      onRefresh();
    } catch {
      message.error('删除失败');
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
      <Typography.Title level={5} style={{ marginTop: 0, marginBottom: 12 }}><FileTextOutlined style={{ color: '#1677ff' }} /> 文档</Typography.Title>

      <div style={{ marginBottom: 10 }}>
        <div style={labelStyle}>切分方式</div>
        <Segmented
          block
          value={splitMode}
          onChange={(v) => setSplitMode(v as 'size' | 'delimiter')}
          options={[
            { value: 'size', label: '按大小' },
            { value: 'delimiter', label: '分隔符' },
          ]}
        />
      </div>

      <div style={{ display: 'flex', gap: 12, marginBottom: 12 }}>
        <div style={{ flex: 1 }}>
          <div style={labelStyle}>{splitMode === 'size' ? 'Chunk 大小（字符）' : '分隔符'}</div>
          {splitMode === 'size' ? (
            <InputNumber min={50} max={5000} value={chunkSize} onChange={v => setChunkSize(v ?? 1000)} style={{ width: '100%' }} />
          ) : (
            <Input value={delimiter} onChange={e => setDelimiter(e.target.value)} placeholder="如 ## 或 ###" />
          )}
        </div>
        <div style={{ flex: 1 }}>
          <div style={labelStyle}>Overlap（字符）</div>
          <InputNumber min={0} max={500} value={overlap} onChange={v => setOverlap(v ?? 150)} disabled={splitMode === 'delimiter'} style={{ width: '100%' }} />
        </div>
      </div>

      <Dragger
        accept=".pdf,.docx,.txt"
        showUploadList={false}
        beforeUpload={handleUpload}
        disabled={uploading}
        style={{ marginBottom: 12 }}
      >
        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
        <p className="ant-upload-text">点击或拖拽文件上传</p>
        <p className="ant-upload-hint">支持 PDF, DOCX, TXT</p>
      </Dragger>

      {uploading && (
        <Progress
          percent={progress >= 100 ? 99 : progress}
          status="active"
          format={() => `上传中 ${Math.min(progress, 99)}%`}
          style={{ marginBottom: 12 }}
        />
      )}

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{documents.length} 个文档</Text>
        <span>
          <Button type="text" size="small" icon={<ReloadOutlined />} onClick={onRefresh}>刷新</Button>
          {canManageDocs && (
            <Button type="text" size="small" icon={<FolderOpenOutlined />} onClick={onOpenManagement}>管理</Button>
          )}
        </span>
      </div>

      <List
        dataSource={documents}
        locale={{ emptyText: '暂无文档' }}
        renderItem={(doc) => (
          <List.Item
            style={{ padding: '8px 4px' }}
            actions={[
              <Tooltip title="下载原文件" key="dl">
                <Button size="small" type="text" icon={<DownloadOutlined />} onClick={() => { downloadDocument(doc.id).catch(() => message.error('下载失败')); }} />
              </Tooltip>,
              ...(canManageDocs ? [
                <Popconfirm title="确认删除？" onConfirm={() => handleDelete(doc.id)} key="del">
                  <Button size="small" danger type="text" icon={<DeleteOutlined />}>删除</Button>
                </Popconfirm>
              ] : []),
            ]}
          >
            <List.Item.Meta
              title={
                <Tooltip title={doc.fileName}>
                  <Text ellipsis style={{ maxWidth: 180, display: 'block' }}>{doc.fileName}</Text>
                </Tooltip>
              }
              description={
                <Space size={4} wrap>
                  {doc.visibility && (
                    <Tag color={visibilityColor[doc.visibility]} style={{ marginInlineEnd: 0 }}>
                      {VISIBILITY_OPTIONS.find(o => o.value === doc.visibility)?.label || doc.visibility}
                    </Tag>
                  )}
                  <Tag style={{ marginInlineEnd: 0 }}>{formatSize(doc.fileSize)}</Tag>
                  {doc.status === 'QUEUED' || doc.status === 'PROCESSING' ? (
                    <Tag color="processing" style={{ marginInlineEnd: 0 }}>处理中</Tag>
                  ) : doc.status === 'FAILED' ? (
                    <Tooltip title={doc.errorMessage || '处理失败'}>
                      <Tag color="error" style={{ marginInlineEnd: 0 }}>失败</Tag>
                    </Tooltip>
                  ) : (
                    <Tag style={{ marginInlineEnd: 0 }}>{doc.chunkCount} chunks</Tag>
                  )}
                </Space>
              }
            />
          </List.Item>
        )}
      />
      <Modal
        open={visibilityModalOpen}
        title="选择文档可见性"
        okText="上传"
        cancelText="取消"
        onOk={confirmUpload}
        onCancel={() => { setVisibilityModalOpen(false); setPendingFile(null); }}
        destroyOnClose
      >
        <div style={labelStyle}>「{pendingFile?.name ?? ''}」的可见性</div>
        <Select
          value={pendingVisibility}
          onChange={(v) => setPendingVisibility(v as 'PUBLIC' | 'DEPARTMENT' | 'EXECUTIVE' | 'PRIVATE')}
          style={{ width: '100%' }}
          options={VISIBILITY_OPTIONS}
        />
      </Modal>
    </div>
  );
};

export default DocumentPanel;
