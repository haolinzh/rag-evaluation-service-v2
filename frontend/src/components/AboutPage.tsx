import React from 'react';
import { Button, Typography, Card, Descriptions, Tag, Space, Row, Col, Statistic, Divider } from 'antd';
import {
  ArrowLeftOutlined,
  GithubOutlined,
  RobotOutlined,
  ApiOutlined,
  SafetyCertificateOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  GlobalOutlined,
  ThunderboltOutlined,
  TeamOutlined,
  SearchOutlined,
  CodeOutlined,
  DeploymentUnitOutlined,
  CloudOutlined,
} from '@ant-design/icons';

const { Title, Paragraph, Text } = Typography;

interface Props {
  onBack: () => void;
}

const features: { icon: React.ReactNode; title: string; desc: string }[] = [
  { icon: <SearchOutlined />, title: '混合检索', desc: 'ES 关键词 + 向量语义并行召回，RRF 融合' },
  { icon: <RobotOutlined />, title: 'Agent 对话模式', desc: '检索与联网交给 LLM 自主决策（tool-use 循环）' },
  { icon: <SafetyCertificateOutlined />, title: '安全拒答 + 脱敏', desc: '提示注入防御、关键词黑名单、PII 星号掩码' },
  { icon: <ThunderboltOutlined />, title: '语义缓存', desc: 'Redis 缓存归一化问题，命中直接返回' },
  { icon: <ExperimentOutlined />, title: '一键评测', desc: '三检索模式对比，5 项质量指标 + 大模型评测' },
  { icon: <DatabaseOutlined />, title: '向量库可切换', desc: 'pgvector / Elasticsearch dense_vector 运行时切换' },
  { icon: <TeamOutlined />, title: 'RBAC 权限', desc: '用户-角色-权限三层模型，文档四档可见性' },
  { icon: <GlobalOutlined />, title: '联网搜索 WebRAG', desc: '知识库置信度不足时自动联网补充（Bocha）' },
];

const techStack: { icon: React.ReactNode; group: string; items: string[] }[] = [
  {
    icon: <DeploymentUnitOutlined />,
    group: '后端',
    items: ['Spring Boot 3.5.16 (Java 17)', 'Spring Security 6', 'Spring AI Alibaba', 'PostgreSQL 16 + pgvector', 'Elasticsearch 8.13.4', 'Redis 7', 'Apache Tika 3.1.0'],
  },
  {
    icon: <CodeOutlined />,
    group: '前端',
    items: ['React 18', 'TypeScript', 'Vite', 'Ant Design 5', 'react-resizable-panels'],
  },
  {
    icon: <CloudOutlined />,
    group: '大模型',
    items: ['qwen-turbo / qwen-plus / qwen-max', 'deepseek-r1', 'text-embedding-v3 (1024 维)', 'qwen3-rerank'],
  },
];

const AboutPage: React.FC<Props> = ({ onBack }) => {
  return (
    <div style={{ height: '100vh', overflowY: 'auto', padding: 24, boxSizing: 'border-box', background: '#f5f6f8' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20, maxWidth: 1000, margin: '0 auto 20px' }}>
        <Button icon={<ArrowLeftOutlined />} onClick={onBack}>返回</Button>
        <Title level={4} style={{ margin: 0, flex: 1 }}>关于</Title>
      </div>

      <div style={{ maxWidth: 1000, margin: '0 auto' }}>
        {/* Hero */}
        <Card style={{ marginBottom: 16, borderRadius: 12, overflow: 'hidden' }}>
          <div style={{
            background: 'linear-gradient(135deg, #1677ff 0%, #4096ff 60%, #69b1ff 100%)',
            margin: -24, marginBottom: 0, padding: '40px 32px', color: '#fff', textAlign: 'center',
          }}>
            <RobotOutlined style={{ fontSize: 56, opacity: 0.95 }} />
            <Title level={2} style={{ color: '#fff', margin: '12px 0 4px' }}>RAG 评测服务 v2</Title>
            <Space size={8} style={{ marginBottom: 12 }}>
              <Tag color="rgba(255,255,255,0.2)" style={{ color: '#fff', border: '1px solid rgba(255,255,255,0.4)' }}>v2.0.0</Tag>
              <Tag color="rgba(255,255,255,0.2)" style={{ color: '#fff', border: '1px solid rgba(255,255,255,0.4)' }}>Spring Boot 3.5.16</Tag>
              <Tag color="rgba(255,255,255,0.2)" style={{ color: '#fff', border: '1px solid rgba(255,255,255,0.4)' }}>React 18</Tag>
            </Space>
            <Paragraph style={{ color: 'rgba(255,255,255,0.92)', fontSize: 15, maxWidth: 620, margin: '0 auto' }}>
              基于检索增强生成（RAG）的知识库问答与评测系统，用于对比不同检索策略的效果。
              支持多轮对话、混合检索、Agent 模式、安全拒答与 PII 脱敏。
            </Paragraph>
          </div>
        </Card>

        {/* 关键数据 */}
        <Card style={{ marginBottom: 16, borderRadius: 12 }}>
          <Row gutter={[16, 16]}>
            <Col span={6}><Statistic title="语料文档" value={8} suffix="份" /></Col>
            <Col span={6}><Statistic title="测试题" value={22} suffix="道" /></Col>
            <Col span={6}><Statistic title="检索模式" value={3} suffix="种" /></Col>
            <Col span={6}><Statistic title="质量指标" value={5} suffix="项" /></Col>
          </Row>
        </Card>

        {/* 核心特性 */}
        <Card title="核心特性" style={{ marginBottom: 16, borderRadius: 12 }}>
          <Row gutter={[16, 16]}>
            {features.map((f) => (
              <Col key={f.title} xs={24} sm={12} md={6}>
                <div style={{
                  padding: 16, height: '100%', borderRadius: 8,
                  background: '#fafafa', border: '1px solid #f0f0f0',
                }}>
                  <div style={{ fontSize: 24, color: '#1677ff', marginBottom: 8 }}>{f.icon}</div>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>{f.title}</div>
                  <div style={{ color: '#888', fontSize: 12, lineHeight: 1.5 }}>{f.desc}</div>
                </div>
              </Col>
            ))}
          </Row>
        </Card>

        {/* 技术栈 */}
        <Card title="技术栈" style={{ marginBottom: 16, borderRadius: 12 }}>
          <Row gutter={[16, 16]}>
            {techStack.map((g) => (
              <Col key={g.group} xs={24} md={8}>
                <div style={{
                  padding: 16, height: '100%', borderRadius: 8,
                  background: '#fafafa', border: '1px solid #f0f0f0',
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, fontWeight: 600 }}>
                    <span style={{ color: '#1677ff', fontSize: 18 }}>{g.icon}</span>
                    {g.group}
                  </div>
                  <Space size={[4, 8]} wrap>
                    {g.items.map((item) => (
                      <Tag key={item} style={{ margin: 0 }}>{item}</Tag>
                    ))}
                  </Space>
                </div>
              </Col>
            ))}
          </Row>
        </Card>

        {/* 项目信息 */}
        <Card title="项目信息" style={{ borderRadius: 12 }}>
          <Descriptions column={{ xs: 1, sm: 2 }} size="small" bordered>
            <Descriptions.Item label="项目名称">RAG 评测服务 v2</Descriptions.Item>
            <Descriptions.Item label="版本">v2.0.0</Descriptions.Item>
            <Descriptions.Item label="交付基线" span={2}>tag v1.0-delivery（commit d29e8eb）</Descriptions.Item>
            <Descriptions.Item label="项目简介" span={2}>
              <a href="https://github.com/haolinzh/rag-evaluation-service" target="_blank" rel="noopener noreferrer">
                rag-evaluation-service
              </a>
              {' '}的 v2 迭代版，在原 case study 交付版本基础上继续演进。
            </Descriptions.Item>
            <Descriptions.Item label="代码仓库" span={2}>
              <a href="https://github.com/haolinzh/rag-evaluation-service-v2" target="_blank" rel="noopener noreferrer">
                <GithubOutlined /> github.com/haolinzh/rag-evaluation-service-v2
              </a>
            </Descriptions.Item>
            <Descriptions.Item label="License" span={2}>仅供学习与面试展示用途</Descriptions.Item>
          </Descriptions>
        </Card>

        <Divider />
        <div style={{ textAlign: 'center', color: '#bbb', fontSize: 12, paddingBottom: 8 }}>
          <Text type="secondary">RAG 评测服务 v2 · Built with Spring AI Alibaba &amp; React</Text>
        </div>
      </div>
    </div>
  );
};

export default AboutPage;
