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

const changelog: { title: string; items: string[] }[] = [
  {
    title: 'v2.0.2 增量：消息中心 + 角色合并',
    items: [
      '新增消息中心：关键操作落库为通知，顶栏铃铛 + 未读角标 + 消息列表页',
      '可见范围：管理员全量审计，普通用户只看自己相关；新增 message:view 权限',
      '覆盖操作：demo 初始化 / 测评 / 文档处理·删除·下载 / 问答 / 删除会话 / 用户与角色变更 / 下载 CSV / 清缓存与日志 / 配置更新 / 重建索引',
      '角色管理合并进用户管理页（Tab 切换），顶栏去掉独立「角色」入口',
    ],
  },
  {
    title: 'v2.0.1 增量：RAG 检索增强 + Demo 一键初始化',
    items: [
      '多轮查询改写（Query Rewrite）：检索前结合历史把「它 / 这个」等指代改写成独立可检索 query，含运行时开关，失败自动回退',
      '上下文检索（Contextual Retrieval）：embedding 拼「文件名 + 章节」前缀 + 跨 chunk 章节追踪，含开关',
      '两个特性可观测：配置页开关 + 请求日志「查询改写后 query」字段与检索流水线「查询改写」步骤',
      'Demo 一键初始化：一键入库演示文档 → 创建权限 / 角色 / 用户 → 触发一次测评',
      '评测归属与运行命名：测评记录当前用户名，运行名 {username}测评#n',
      '登出入口改为用户下拉菜单；对话 / 检索 / 联网控件收敛到聊天面板工具栏',
    ],
  },
  {
    title: '检索引擎跃迁',
    items: [
      '从单一向量检索升级为混合检索：ES BM25 关键词 + 向量语义并行召回，RRF 融合 + qwen3-rerank 精排',
      '引入 Spring AI Alibaba 作为 LLM / Embedding / 混合检索的统一实现',
      '向量库可切换：pgvector / Elasticsearch dense_vector 运行时切换，双写 + 一键重建索引',
      '日志详情新增检索流水线可视化（召回通道 + 评分）',
    ],
  },
  {
    title: '安全与权限（RBAC）',
    items: [
      '用户-角色-权限三层模型，BCrypt + Bearer Token 无状态会话',
      '文档四档可见性（PUBLIC / DEPARTMENT / EXECUTIVE / PRIVATE），请求日志按用户归属',
      '提示注入防御 → 关键词黑名单 → 相似度阈值 → 越界检测，四级安全闸门',
      'PII 星号中段脱敏（身份证 / 手机号 / 邮箱）',
    ],
  },
  {
    title: '联网搜索（WebRAG）',
    items: ['接入 Bocha 联网引擎，知识库置信度不足时自动联网补充，来源含可点击 URL'],
  },
  {
    title: 'Agent 对话模式',
    items: [
      '「Workflow / Agent」可切换：检索与联网作为 tool 交给 LLM 自主决策循环',
      '决策步骤经 SSE tool_call 事件实时展示，安全拒答与 PII 脱敏保留在代码层',
    ],
  },
  {
    title: '一键评测',
    items: [
      'hybrid / vector / hybrid-rerank 三模式对比，5 项质量指标 + LLM-as-Judge 大模型评测',
      'SSE 流式进度、后台可取消、结果持久化与历史回看、测试集 DB 管理',
    ],
  },
  {
    title: '工程化与运维',
    items: [
      'Docker 容器化（后端内置 tesseract OCR，前端 nginx 反代 /api）',
      '异步文档入库、扫描件 OCR、上传进度与去重',
      '运维面板：ES / PG 状态、chunk 浏览、向量索引异步重建',
      '运行时热配置（检索 / 模型 / 安全 / 缓存免重启）、Redis 语义缓存',
    ],
  },
  {
    title: '技术栈升级',
    items: [
      'Spring Boot 3.4.1 → 3.5.16',
      'Spring AI 1.0.3 → 1.1.2',
      'Spring AI Alibaba 1.0.0.2 → 1.1.2.3',
    ],
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
              <Tag color="rgba(255,255,255,0.2)" style={{ color: '#fff', border: '1px solid rgba(255,255,255,0.4)' }}>v2.0.2</Tag>
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

        {/* 版本演进 */}
        <Card
          title="版本演进"
          extra={<Tag color="blue">v2.0.1 → v2.0.2</Tag>}
          style={{ marginBottom: 16, borderRadius: 12 }}
        >
          {changelog.map((g, idx) => (
            <div key={g.title} style={{ marginBottom: idx === changelog.length - 1 ? 0 : 20 }}>
              <div style={{ fontWeight: 600, color: '#1677ff', marginBottom: 8, fontSize: 15 }}>
                {g.title}
              </div>
              <ul style={{ margin: 0, paddingLeft: 20, color: '#555' }}>
                {g.items.map((item) => (
                  <li key={item} style={{ marginBottom: 4, lineHeight: 1.6, fontSize: 13 }}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
        </Card>

        {/* 项目信息 */}
        <Card title="项目信息" style={{ borderRadius: 12 }}>
          <Descriptions column={{ xs: 1, sm: 2 }} size="small" bordered>
            <Descriptions.Item label="项目名称">RAG 评测服务 v2</Descriptions.Item>
            <Descriptions.Item label="版本">v2.0.2</Descriptions.Item>
            <Descriptions.Item label="项目简介" span={2}>
              RAG + generative AI evaluation service: hybrid retrieval (ES + pgvector + RRF), safety gate, PII redaction, semantic cache, ops metrics report
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
