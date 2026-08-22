import React, { useState, useEffect, useCallback } from 'react';
import { Card, Button, Typography, Tag, Alert, Progress, Space, Row, Col, Table, Input, Select, message } from 'antd';
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { fetchOpsStatus, fetchChunks } from '../api';
import type { OpsStatus, ChunkRecord } from '../types';

interface Props {
  onBack: () => void;
}

const formatSize = (bytes?: number) => {
  if (bytes == null) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
};

const formatNum = (n?: number) => (n == null ? '—' : n.toLocaleString());

const statusColor: Record<string, string> = { green: 'green', yellow: 'gold', red: 'red' };

const Tile: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ background: '#fafafa', borderRadius: 6, padding: '8px 10px', minHeight: 52 }}>
    <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 2 }}>{label}</div>
    <div style={{ fontSize: 15, fontWeight: 600, color: '#262626', lineHeight: 1.3, overflowWrap: 'anywhere' }}>
      {children}
    </div>
  </div>
);

const chunkColumns: ColumnsType<ChunkRecord> = [
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true, width: 220 },
  { title: '章节', dataIndex: 'chapter', key: 'chapter', width: 140, render: (v: string | null) => v || '—' },
  { title: '小节', dataIndex: 'section', key: 'section', width: 140, render: (v: string | null) => v || '—' },
  { title: '序号', dataIndex: 'chunkIndex', key: 'chunkIndex', width: 70, render: (v: number | null) => (v == null ? '—' : v) },
  { title: '内容', dataIndex: 'content', key: 'content', ellipsis: true },
];

const OpsPage: React.FC<Props> = ({ onBack }) => {
  const [status, setStatus] = useState<OpsStatus | null>(null);
  const [loading, setLoading] = useState(false);

  const [backend, setBackend] = useState<'pg' | 'es'>('pg');
  const [fileNameFilter, setFileNameFilter] = useState('');
  const [chunks, setChunks] = useState<ChunkRecord[]>([]);
  const [chunksTotal, setChunksTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [chunksLoading, setChunksLoading] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setStatus(await fetchOpsStatus());
    } catch {
      // 保留上次数据，静默失败
    } finally {
      setLoading(false);
    }
  }, []);

  const loadChunks = useCallback(async (b: 'pg' | 'es', f: string, p: number, s: number) => {
    setChunksLoading(true);
    try {
      const res = await fetchChunks(b, f, p, s);
      setChunks(res.items);
      setChunksTotal(res.total);
    } catch {
      message.error('数据加载失败');
    } finally {
      setChunksLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 5000);
    return () => clearInterval(interval);
  }, [refresh]);

  useEffect(() => {
    loadChunks(backend, fileNameFilter, page, pageSize);
  }, [backend, fileNameFilter, page, pageSize, loadChunks]);

  const es = status?.es;
  const pg = status?.pg;

  const esGrid = (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: '10px 12px' }}>
      <Tile label="集群状态">
        {es?.status ? <Tag color={statusColor[es.status] ?? 'default'} style={{ margin: 0 }}>{es.status}</Tag> : '—'}
      </Tile>
      <Tile label="集群名">{es?.clusterName || '—'}</Tile>
      <Tile label="版本">{es?.version || '—'}</Tile>
      <Tile label="节点数">{formatNum(es?.nodeCount)}</Tile>
      <Tile label="主分片">{formatNum(es?.activePrimaryShards)}</Tile>
      <Tile label="活跃分片">{formatNum(es?.activeShards)}</Tile>
      <Tile label="未分配分片">{formatNum(es?.unassignedShards)}</Tile>
      <Tile label="迁移中分片">{formatNum(es?.relocatingShards)}</Tile>
      <Tile label="文档数">{formatNum(es?.docCount)}</Tile>
      <Tile label="存储大小">{formatSize(es?.storeSizeBytes)}</Tile>
      <Tile label="CPU">
        {es?.cpuPercent != null ? `${es.cpuPercent.toFixed(1)}%` : '—'}
      </Tile>
    </div>
  );

  const pgGrid = (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: '10px 12px' }}>
      <Tile label="版本">{pg?.version ? pg.version.split(' on ')[0] : '—'}</Tile>
      <Tile label="数据库大小">{formatSize(pg?.databaseSizeBytes)}</Tile>
      <Tile label="连接数">{formatNum(pg?.numBackends)}</Tile>
      <Tile label="事务提交">{formatNum(pg?.xactCommit)}</Tile>
      <Tile label="事务回滚">{formatNum(pg?.xactRollback)}</Tile>
      <Tile label="死锁">{formatNum(pg?.deadlocks)}</Tile>
      <Tile label="缓存命中率">
        {pg?.cacheHitRatio != null ? `${(pg.cacheHitRatio * 100).toFixed(1)}%` : '—'}
      </Tile>
      <Tile label="Chunk 数">{formatNum(pg?.chunkCount)}</Tile>
      <Tile label="活跃元组">{formatNum(pg?.liveTuples)}</Tile>
      <Tile label="死元组">{formatNum(pg?.deadTuples)}</Tile>
      <Tile label="顺序扫描">{formatNum(pg?.seqScan)}</Tile>
      <Tile label="索引扫描">{formatNum(pg?.indexScan)}</Tile>
      <Tile label="索引大小">{formatSize(pg?.indexSizeBytes)}</Tile>
    </div>
  );

  return (
    <div style={{ height: '100vh', overflowY: 'auto', padding: 24, boxSizing: 'border-box' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={onBack}>返回</Button>
        <Typography.Title level={4} style={{ margin: 0, flex: 1 }}>运维</Typography.Title>
        <Button icon={<ReloadOutlined />} onClick={refresh} loading={loading}>刷新</Button>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card
            size="small"
            title={<Space>Elasticsearch{es?.indexName ? <Tag color="blue">{es.indexName}</Tag> : null}</Space>}
          >
            {es?.error && <Alert type="error" showIcon message="Elasticsearch 查询失败" description={es.error} style={{ marginBottom: 12 }} />}
            {esGrid}
            {es && !es.error && (
              <div style={{ marginTop: 12 }}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>堆内存使用率</div>
                <Progress percent={Math.min(100, Math.max(0, Number(es.heapUsedPercent?.toFixed(1) ?? 0)))} size="small" />
              </div>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            size="small"
            title={<Space>PostgreSQL{pg?.tableName ? <Tag color="blue">{pg.tableName}</Tag> : null}</Space>}
          >
            {pg?.error && <Alert type="error" showIcon message="PostgreSQL 查询失败" description={pg.error} style={{ marginBottom: 12 }} />}
            {pgGrid}
          </Card>
        </Col>
      </Row>

      <Card size="small" title="数据浏览" style={{ marginTop: 16 }}>
        <Space style={{ marginBottom: 12 }} wrap>
          <Select
            value={backend}
            onChange={(v) => { setBackend(v); setPage(1); }}
            style={{ width: 220 }}
            options={[
              { value: 'pg', label: 'PG · vector_chunks' },
              { value: 'es', label: 'ES · rag_chunks' },
            ]}
          />
          <Input.Search
            placeholder="按文件名过滤（可选）"
            allowClear
            onSearch={(v) => { setFileNameFilter(v.trim()); setPage(1); }}
            style={{ width: 280 }}
          />
        </Space>
        <Table<ChunkRecord>
          rowKey="chunkId"
          dataSource={chunks}
          columns={chunkColumns}
          loading={chunksLoading}
          size="small"
          scroll={{ x: 900 }}
          pagination={{
            current: page,
            pageSize,
            total: chunksTotal,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (t) => `共 ${t} 条`,
          }}
          onChange={(pag) => {
            if (pag.current) setPage(pag.current);
            if (pag.pageSize) setPageSize(pag.pageSize);
          }}
          expandable={{
            expandedRowRender: (r) => (
              <div style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontSize: 13, color: '#555', maxHeight: 320, overflowY: 'auto' }}>
                {r.content}
              </div>
            ),
          }}
        />
      </Card>
    </div>
  );
};

export default OpsPage;
