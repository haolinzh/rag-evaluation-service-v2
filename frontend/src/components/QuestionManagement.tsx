import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, Tag, Typography, Space, Popconfirm, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { EvaluationQuestion, EvaluationQuestionInput } from '../types';
import { fetchEvaluationQuestions, createEvaluationQuestion, updateEvaluationQuestion, deleteEvaluationQuestion } from '../api';

interface Props {
  onBack: () => void;
}

const TYPE_LABELS: Record<string, string> = {
  factual: '事实型',
  explanatory: '解释型',
  comparison: '对比型',
  safety_refusal: '拒答型',
};

const TYPE_COLORS: Record<string, string> = {
  factual: 'blue',
  explanatory: 'green',
  comparison: 'purple',
  safety_refusal: 'red',
};

const DIFFICULTY_LABELS: Record<string, string> = {
  basic: '基础',
  intermediate: '进阶',
};

const QuestionManagement: React.FC<Props> = ({ onBack }) => {
  const [questions, setQuestions] = useState<EvaluationQuestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<EvaluationQuestion | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<EvaluationQuestionInput>();

  const refresh = async () => {
    setLoading(true);
    try {
      setQuestions(await fetchEvaluationQuestions());
    } catch {
      message.error('加载测试集失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { refresh(); }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ language: 'zh', expectedType: 'factual', difficulty: 'basic' });
    setModalOpen(true);
  };

  const openEdit = (q: EvaluationQuestion) => {
    setEditing(q);
    form.setFieldsValue({
      question: q.question,
      language: q.language,
      expectedType: q.expectedType,
      difficulty: q.difficulty,
    });
    setModalOpen(true);
  };

  const handleDelete = async (q: EvaluationQuestion) => {
    try {
      await deleteEvaluationQuestion(q.id);
      message.success(`已删除 ${q.id}`);
      refresh();
    } catch {
      message.error('删除失败');
    }
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateEvaluationQuestion(editing.id, values);
        message.success(`已更新 ${editing.id}`);
      } else {
        await createEvaluationQuestion(values);
        message.success('已新增题目');
      }
      setModalOpen(false);
      refresh();
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 72 },
    {
      title: '题型', dataIndex: 'expectedType', key: 'expectedType', width: 100,
      render: (v: string) => <Tag color={TYPE_COLORS[v] ?? 'default'}>{TYPE_LABELS[v] ?? v}</Tag>,
    },
    {
      title: '难度', dataIndex: 'difficulty', key: 'difficulty', width: 80,
      render: (v: string) => DIFFICULTY_LABELS[v] ?? v,
    },
    { title: '语言', dataIndex: 'language', key: 'language', width: 70, render: (v: string) => (v === 'en' ? 'EN' : '中') },
    { title: '题目', dataIndex: 'question', key: 'question', ellipsis: true },
    {
      title: '操作', key: 'action', width: 130,
      render: (_: unknown, r: EvaluationQuestion) => (
        <Space size={4}>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>编辑</Button>
          <Popconfirm title={`删除 ${r.id}？`} description="删除后历史报告不受影响" onConfirm={() => handleDelete(r)} okText="删除" cancelText="取消">
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
        <Button icon={<ArrowLeftOutlined />} onClick={onBack}>返回</Button>
        <Typography.Title level={4} style={{ margin: 0 }}>测试集管理</Typography.Title>
        <Tag>{questions.length} 题</Tag>
        <span style={{ flex: 1 }} />
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增题目</Button>
      </div>

      <Table
        rowKey="id"
        dataSource={questions}
        columns={columns}
        loading={loading}
        pagination={{ pageSize: 15 }}
        scroll={{ x: 760, y: 'calc(100vh - 220px)' }}
      />

      <Modal
        title={editing ? `编辑题目 ${editing.id}` : '新增题目'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        okText="保存"
        cancelText="取消"
        width={640}
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item label="题目" name="question" rules={[{ required: true, message: '请输入题目内容' }]}>
            <Input.TextArea rows={3} placeholder="输入要评测的问题" />
          </Form.Item>
          <div style={{ display: 'flex', gap: 16 }}>
            <Form.Item label="题型" name="expectedType" rules={[{ required: true }]}>
              <Select
                style={{ width: 160 }}
                options={Object.entries(TYPE_LABELS).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
            <Form.Item label="难度" name="difficulty" rules={[{ required: true }]}>
              <Select
                style={{ width: 160 }}
                options={Object.entries(DIFFICULTY_LABELS).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
            <Form.Item label="语言" name="language" rules={[{ required: true }]}>
              <Select
                style={{ width: 160 }}
                options={[
                  { value: 'zh', label: '中文' },
                  { value: 'en', label: 'English' },
                ]}
              />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </div>
  );
};

export default QuestionManagement;
