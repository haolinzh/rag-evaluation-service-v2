import React, { useState, useEffect, useRef } from 'react';
import { Form, Select, InputNumber, Switch, Button, Typography, Space, Card, Alert, message, Row, Col, Spin, Input, Radio, Popconfirm, Progress } from 'antd';
import { ArrowLeftOutlined, SaveOutlined, SettingOutlined, KeyOutlined, DatabaseOutlined, ReloadOutlined } from '@ant-design/icons';
import { fetchConfig, updateConfig, updateApiKey, rebuildVectorIndex, fetchRebuildStatus, rebuildPgIndex } from '../api';
import type { SystemConfig, RebuildStatus } from '../types';

interface Props {
  onBack: () => void;
  onSaved: (mode: string) => void;
}

interface FormValues {
  mode: string;
  topK: number;
  recallSizeMultiplier: number;
  rrfK: number;
  rerankCandidates: number;
  similarityThreshold: number;
  chat: string;
  embedding: string;
  rerank: string;
  judgeModel: string;
  judgeEnabled: boolean;
  judgeTemperature: number;
  temperature: number;
  topP: number;
  maxTokens: number;
  vectorBackend: 'pgvector' | 'elasticsearch';
  pgIndexType: 'ivfflat' | 'hnsw';
  pgLists: number;
  pgProbes: number;
  pgEfSearch: number;
  esNumCandidates: number;
  minSimilarity: number;
  enableOutOfScopeCheck: boolean;
  outOfScopeThreshold: number;
  forbiddenKeywords: string[];
  enabled: boolean;
  ttlSeconds: number;
}

const rebuildPhaseLabel: Record<string, string> = {
  IDLE: '空闲',
  PREPARING: '解析+向量化',
  WRITING: '双写入库',
  INDEXING: '重建索引',
  DONE: '完成',
  FAILED: '失败',
};

const ConfigPage: React.FC<Props> = ({ onBack, onSaved }) => {
  const [form] = Form.useForm<FormValues>();
  const [config, setConfig] = useState<SystemConfig | null>(null);
  const [saving, setSaving] = useState(false);
  const [apiKeyInput, setApiKeyInput] = useState('');
  const [savingKey, setSavingKey] = useState(false);
  const [rebuildStatus, setRebuildStatus] = useState<RebuildStatus | null>(null);
  const [rebuildingPg, setRebuildingPg] = useState(false);

  const embeddingValue = Form.useWatch('embedding', form);

  useEffect(() => {
    fetchConfig()
      .then((c) => {
        setConfig(c);
        form.setFieldsValue({
          mode: c.retrieval.mode,
          topK: c.retrieval.topK,
          recallSizeMultiplier: c.retrieval.recallSizeMultiplier,
          rrfK: c.retrieval.rrfK,
          rerankCandidates: c.retrieval.rerankCandidates,
          similarityThreshold: c.retrieval.similarityThreshold,
          chat: c.models.chat,
          embedding: c.models.embedding,
          rerank: c.models.rerank,
          judgeModel: c.judge.model,
          judgeEnabled: c.judge.enabled,
          judgeTemperature: c.judge.temperature,
          temperature: c.generation.temperature,
          topP: c.generation.topP,
          maxTokens: c.generation.maxTokens,
          vectorBackend: c.vector.backend,
          pgIndexType: c.vector.pgvector.indexType,
          pgLists: c.vector.pgvector.lists,
          pgProbes: c.vector.pgvector.probes,
          pgEfSearch: c.vector.pgvector.efSearch,
          esNumCandidates: c.vector.elasticsearch.numCandidates,
          minSimilarity: c.safety.minSimilarity,
          enableOutOfScopeCheck: c.safety.enableOutOfScopeCheck,
          outOfScopeThreshold: c.safety.outOfScopeThreshold,
          forbiddenKeywords: c.safety.forbiddenKeywords.split(',').map((s) => s.trim()).filter(Boolean),
          enabled: c.cache.enabled,
          ttlSeconds: c.cache.ttlSeconds,
        });
      })
      .catch(() => message.error('配置加载失败'));
  }, [form]);

  useEffect(() => {
    fetchRebuildStatus().then(setRebuildStatus).catch(() => {});
  }, []);

  useEffect(() => {
    if (!rebuildStatus?.running) return;
    const timer = setInterval(async () => {
      try {
        setRebuildStatus(await fetchRebuildStatus());
      } catch { /* ignore */ }
    }, 1500);
    return () => clearInterval(timer);
  }, [rebuildStatus?.running]);

  const prevRunning = useRef(false);
  useEffect(() => {
    const running = !!rebuildStatus?.running;
    if (prevRunning.current && !running) {
      if (rebuildStatus?.phase === 'DONE') {
        message.success(`重建完成：${rebuildStatus.totalDocuments} 文档，${rebuildStatus.chunkCount} 分块`);
      } else if (rebuildStatus?.phase === 'FAILED') {
        message.error(rebuildStatus.message ?? '重建失败');
      }
    }
    prevRunning.current = running;
  }, [rebuildStatus]);

  const modelGroup = (g: string) =>
    (config?.modelOptions ?? []).filter((o) => o.group === g).map((o) => ({ label: o.label, value: o.id }));

  const keywordOptions =
    config?.safety.forbiddenKeywords.split(',').map((s) => s.trim()).filter(Boolean).map((k) => ({ label: k, value: k })) ?? [];

  const selectedEmbedding = config?.modelOptions.find((o) => o.id === embeddingValue);
  const dimMismatch =
    !!selectedEmbedding && selectedEmbedding.dimensions != null && selectedEmbedding.dimensions !== config?.embeddingDimension;

  const onFinish = async (v: FormValues) => {
    if (!config) return;
    const next: SystemConfig = {
      retrieval: {
        mode: v.mode,
        topK: v.topK,
        recallSizeMultiplier: v.recallSizeMultiplier,
        rrfK: v.rrfK,
        rerankCandidates: v.rerankCandidates,
        similarityThreshold: v.similarityThreshold,
      },
      models: { chat: v.chat, embedding: v.embedding, rerank: v.rerank },
      judge: { enabled: v.judgeEnabled, model: v.judgeModel, temperature: v.judgeTemperature },
      generation: { temperature: v.temperature, topP: v.topP, maxTokens: v.maxTokens },
      vector: {
        backend: v.vectorBackend,
        pgvector: { indexType: v.pgIndexType, lists: v.pgLists, probes: v.pgProbes, efSearch: v.pgEfSearch },
        elasticsearch: { numCandidates: v.esNumCandidates },
      },
      safety: {
        minSimilarity: v.minSimilarity,
        enableOutOfScopeCheck: v.enableOutOfScopeCheck,
        outOfScopeThreshold: v.outOfScopeThreshold,
        forbiddenKeywords: v.forbiddenKeywords.join(','),
      },
      cache: { enabled: v.enabled, ttlSeconds: v.ttlSeconds },
      modelOptions: config.modelOptions,
      embeddingDimension: config.embeddingDimension,
    };
    setSaving(true);
    try {
      await updateConfig(next);
      onSaved(v.mode);
      message.success('配置已保存，即时生效');
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const saveApiKey = async () => {
    const k = apiKeyInput.trim();
    if (!k) {
      message.warning('请输入 API Key');
      return;
    }
    setSavingKey(true);
    try {
      const c = await updateApiKey(k);
      setConfig(c);
      setApiKeyInput('');
      message.success('API Key 已保存，即时生效');
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '保存失败');
    } finally {
      setSavingKey(false);
    }
  };

  const clearApiKey = async () => {
    setSavingKey(true);
    try {
      const c = await updateApiKey('');
      setConfig(c);
      setApiKeyInput('');
      message.success('已清除 API Key');
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '清除失败');
    } finally {
      setSavingKey(false);
    }
  };

  const onRebuild = async () => {
    try {
      const s = await rebuildVectorIndex();
      setRebuildStatus(s);
      message.success('已开始重建，后台处理中');
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '重建失败');
    }
  };

  const onRebuildPgIndex = async () => {
    setRebuildingPg(true);
    try {
      const r = await rebuildPgIndex();
      message.success(`PG 索引已重建（${r.indexType}${r.indexType === 'ivfflat' ? `，lists=${r.lists}` : ''}）`);
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '重建 PG 索引失败');
    } finally {
      setRebuildingPg(false);
    }
  };

  if (!config) {
    return (
      <div style={{ padding: 48, display: 'flex', justifyContent: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div style={{ height: '100vh', overflowY: 'auto', padding: 24, maxWidth: 960, margin: '0 auto', boxSizing: 'border-box' }}>
      <Space style={{ marginBottom: 16 }} align="center">
        <Button icon={<ArrowLeftOutlined />} onClick={onBack}>返回</Button>
        <Typography.Title level={4} style={{ margin: 0 }}><SettingOutlined /> 系统配置</Typography.Title>
        <Button type="primary" icon={<SaveOutlined />} onClick={() => form.submit()} loading={saving}>保存</Button>
      </Space>

      <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
        <Card title={<span><KeyOutlined /> API Key（阿里云百炼）</span>} size="small" style={{ marginBottom: 16 }}>
          <Typography.Text type="secondary">
            用于调用 DashScope 大模型 / Embedding。填写后即时生效；仅显示脱敏尾号，不回显完整 Key。
          </Typography.Text>
          <Space.Compact style={{ width: '100%', marginTop: 8 }}>
            <Input.Password
              placeholder="sk-..."
              value={apiKeyInput}
              onChange={(e) => setApiKeyInput(e.target.value)}
              style={{ maxWidth: 420 }}
            />
            <Button type="primary" onClick={saveApiKey} loading={savingKey}>保存 Key</Button>
            <Button onClick={clearApiKey} disabled={!config.apiKeyMasked}>清除</Button>
          </Space.Compact>
          <div style={{ marginTop: 8 }}>
            <Typography.Text type="secondary">
              {config.apiKeyMasked
                ? `当前已配置：${config.apiKeyMasked}`
                : '当前未配置（可在上方填写，或通过环境变量 DASHSCOPE_API_KEY 注入）'}
            </Typography.Text>
          </div>
        </Card>

        <Card title="检索参数" size="small" style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="检索模式" name="mode" rules={[{ required: true }]}>
                <Select
                  options={[
                    { label: 'vector（仅向量）', value: 'vector' },
                    { label: 'hybrid（关键词+向量 RRF）', value: 'hybrid' },
                    { label: 'hybrid-rerank（RRF+精排）', value: 'hybrid-rerank' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="top-k" name="topK" rules={[{ required: true }]}>
                <InputNumber min={1} max={50} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="召回倍数 (recall-size-multiplier)" name="recallSizeMultiplier" rules={[{ required: true }]}>
                <InputNumber min={1} max={20} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="RRF k" name="rrfK" rules={[{ required: true }]}>
                <InputNumber min={1} max={200} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="精排候选数 (rerank-candidates)" name="rerankCandidates" rules={[{ required: true }]}>
                <InputNumber min={1} max={200} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="相似度阈值 (similarity-threshold)" name="similarityThreshold" rules={[{ required: true }]}>
                <InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        <Card title="模型" size="small" style={{ marginBottom: 16 }}>
          {dimMismatch && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              message={`向量模型 ${selectedEmbedding?.id} 维度为 ${selectedEmbedding?.dimensions}，与当前向量库 (${config.embeddingDimension} 维) 不一致，切换后需重新入库。`}
            />
          )}
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="对话模型" name="chat" rules={[{ required: true }]}>
                <Select options={modelGroup('chat')} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="向量模型" name="embedding" rules={[{ required: true }]}>
                <Select options={modelGroup('embedding')} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="精排模型" name="rerank" rules={[{ required: true }]}>
                <Select options={modelGroup('rerank')} />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        <Card title="生成参数（对话）" size="small" style={{ marginBottom: 16 }}>
          <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
            作用于知识库问答生成。top_p 设为 1.0 时以 temperature 为准；max_tokens 为 0 表示不限制长度。
          </Typography.Text>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="temperature（采样温度）" name="temperature" rules={[{ required: true }]}>
                <InputNumber min={0} max={2} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="top_p（核采样）" name="topP" rules={[{ required: true }]}>
                <InputNumber min={0.01} max={1} step={0.05} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="max_tokens（最大长度，0=不限制）" name="maxTokens" rules={[{ required: true }]}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        <Card
          title={<span><DatabaseOutlined /> 向量数据库</span>}
          size="small"
          style={{ marginBottom: 16 }}
          extra={
            <Space>
              <Popconfirm
                title="重建 PG 索引？"
                description="仅按当前索引参数 DROP+CREATE pgvector 索引，无需重新向量化（较快）。"
                onConfirm={onRebuildPgIndex}
                okText="重建"
                cancelText="取消"
              >
                <Button icon={<ReloadOutlined />} loading={rebuildingPg} disabled={!!rebuildStatus?.running}>重建 PG 索引</Button>
              </Popconfirm>
              <Popconfirm
                title="重建向量索引？"
                description="将清空并按当前索引参数重新入库所有文档（双写 pgvector 与 Elasticsearch）。"
                onConfirm={onRebuild}
                okText="重建"
                cancelText="取消"
              >
                <Button icon={<ReloadOutlined />} loading={!!rebuildStatus?.running}>重建向量索引</Button>
              </Popconfirm>
            </Space>
          }
        >
          <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
            双写 pgvector 与 Elasticsearch。「当前向量后端」决定实际用于语义检索的后端，保存后立即切换（双写，无需重新入库）；下方两个参数区分别配置各自后端。索引类型 / lists 等建索引参数改动后点击「重建 PG 索引」即可（仅重建 pgvector 索引，无需重新向量化）；embedding 维度变化等才需点击「重建向量索引」全量重新入库。
          </Typography.Text>
          {rebuildStatus?.running && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message={`正在重建向量索引（${rebuildPhaseLabel[rebuildStatus.phase] ?? rebuildStatus.phase}）`}
              description={
                <div>
                  <Progress
                    percent={rebuildStatus.totalDocuments > 0
                      ? Math.round((rebuildStatus.processedDocuments / rebuildStatus.totalDocuments) * 100)
                      : 0}
                    size="small"
                  />
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                    已处理 {rebuildStatus.processedDocuments}/{rebuildStatus.totalDocuments} 文档
                    {rebuildStatus.chunkCount > 0 ? `，${rebuildStatus.chunkCount} 分块` : ''}
                    {rebuildStatus.message ? `，当前：${rebuildStatus.message}` : ''}
                  </div>
                </div>
              }
            />
          )}
          <Form.Item
            label="当前向量后端（实际检索用）"
            name="vectorBackend"
            rules={[{ required: true }]}
            extra="保存后语义检索立即切换到所选后端"
          >
            <Radio.Group optionType="button" buttonStyle="solid">
              <Radio.Button value="pgvector">PgVector</Radio.Button>
              <Radio.Button value="elasticsearch">Elasticsearch</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Typography.Text strong style={{ display: 'block', margin: '12px 0 8px' }}>PgVector 参数</Typography.Text>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item label="索引类型" name="pgIndexType" rules={[{ required: true }]}>
                <Select options={[{ label: 'IVFFlat', value: 'ivfflat' }, { label: 'HNSW', value: 'hnsw' }]} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item label="lists" name="pgLists" rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item label="probes" name="pgProbes" rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item label="ef_search" name="pgEfSearch" rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Typography.Text strong style={{ display: 'block', margin: '12px 0 8px' }}>Elasticsearch 参数</Typography.Text>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item label="num_candidates" name="esNumCandidates" rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        <Card title="评测（大模型评测）" size="small" style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="评测模型" name="judgeModel" rules={[{ required: true }]}>
                <Select options={modelGroup('chat')} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="评测 temperature" name="judgeTemperature" rules={[{ required: true }]}>
                <InputNumber min={0} max={2} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="默认启用大模型评测" name="judgeEnabled" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        <Card title="安全" size="small" style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="最小相似度 (min-similarity)" name="minSimilarity" rules={[{ required: true }]}>
                <InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="越界阈值 (out-of-scope-threshold)" name="outOfScopeThreshold" rules={[{ required: true }]}>
                <InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="启用越界检测" name="enableOutOfScopeCheck" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item label="禁用关键词" name="forbiddenKeywords">
                <Select mode="tags" placeholder="输入后回车添加" options={keywordOptions} />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        <Card title="缓存" size="small" style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="启用语义缓存" name="enabled" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="缓存 TTL（秒）" name="ttlSeconds" rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Card>
      </Form>
    </div>
  );
};

export default ConfigPage;
