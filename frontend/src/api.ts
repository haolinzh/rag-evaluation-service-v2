import axios from 'axios';
import type { DocumentMeta, ChatResponse, ChatMessage, OpsReport, ChunkConfig, ChunkPreview, RequestLog, SystemConfig, Source, EvaluationQuestion, EvaluationQuestionInput, EvaluationEvent, EvaluationReport, EvaluationRunMeta, OpsStatus, ChunkPage, RebuildStatus, AuthUser, Permission, Role, ManagedUser, UserRequest, RoleRequest, RegisterRequest, DemoEvent, AppNotification } from './types';

const api = axios.create({ baseURL: '/api' });

const TOKEN_KEY = 'rag_token';
const USER_KEY = 'rag_user';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getCachedUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

export function setAuth(token: string, user: AuthUser): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

function authHeaders(json = true): Record<string, string> {
  const headers: Record<string, string> = {};
  if (json) headers['Content-Type'] = 'application/json';
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      clearAuth();
      window.dispatchEvent(new Event('auth-expired'));
    }
    return Promise.reject(err);
  },
);

export async function uploadDocument(
  file: File,
  config: ChunkConfig,
  visibility: string,
  onProgress?: (percent: number) => void
): Promise<DocumentMeta> {
  const form = new FormData();
  form.append('file', file);
  form.append('splitMode', config.splitMode);
  form.append('chunkSize', String(config.chunkSize));
  form.append('delimiter', config.delimiter);
  form.append('overlap', String(config.overlap));
  form.append('visibility', visibility);
  const { data } = await api.post('/documents/upload', form, {
    onUploadProgress: (e) => {
      if (onProgress && e.total) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    },
  });
  return data;
}

export async function listDocuments(): Promise<DocumentMeta[]> {
  const { data } = await api.get('/documents');
  return data;
}

export async function deleteDocument(id: number): Promise<void> {
  await api.delete(`/documents/${id}`);
}

export async function reprocessDocument(id: number, config: ChunkConfig, visibility?: string): Promise<DocumentMeta> {
  const { data } = await api.put(`/documents/${id}`, null, {
    params: { ...config, visibility: visibility || undefined },
  });
  return data;
}

export async function downloadDocument(id: number): Promise<void> {
  const resp = await fetch(`/api/documents/${id}/download`, { headers: authHeaders(false) });
  if (!resp.ok) {
    throw new Error(`下载失败 (HTTP ${resp.status})`);
  }
  const blob = await resp.blob();
  const disposition = resp.headers.get('Content-Disposition') || '';
  const match = /filename\*=UTF-8''([^;]+)/.exec(disposition) || /filename="?([^";]+)"?/.exec(disposition);
  const filename = match ? decodeURIComponent(match[1]) : 'download';
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export async function getDocumentChunks(id: number): Promise<ChunkPreview[]> {
  const { data } = await api.get(`/documents/${id}/chunks`);
  return data;
}

export async function askQuestion(question: string, sessionId: string, mode: string, webSearch: string, chatMode?: string): Promise<ChatResponse> {
  const { data } = await api.post('/chat', { question, sessionId, mode, webSearch, chatMode });
  return data;
}

export interface StreamEvent {
  type: 'thinking' | 'content' | 'done' | 'error' | 'tool_call';
  text?: string;
  content?: string;
  thinking?: string;
  retrievalMode?: string;
  sources?: Source[];
  refusal?: boolean;
  refusalReason?: string;
  tool?: string;
  query?: string;
}

export async function streamAsk(
  question: string,
  sessionId: string,
  mode: string,
  webSearch: string,
  chatMode: string,
  onEvent: (evt: StreamEvent) => void
): Promise<void> {
  const resp = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ question, sessionId, mode, webSearch, chatMode }),
  });

  if (!resp.ok || !resp.body) {
    const text = await resp.text().catch(() => '');
    throw new Error(text || `HTTP ${resp.status}`);
  }

  const reader = resp.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let idx: number;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const rawEvent = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      for (const line of rawEvent.split('\n')) {
        if (!line.startsWith('data:')) continue;
        const data = line.slice(5).trim();
        if (!data) continue;
        try {
          onEvent(JSON.parse(data) as StreamEvent);
        } catch {
          // ignore malformed chunk
        }
      }
    }
  }
}

export async function getChatHistory(sessionId: string): Promise<ChatMessage[]> {
  const { data } = await api.get(`/chat/history/${sessionId}`);
  return data;
}

export async function deleteChatHistory(sessionId: string): Promise<void> {
  await api.delete(`/chat/history/${sessionId}`);
}

export async function fetchReport(): Promise<Blob> {
  const { data } = await api.get('/report/csv', { responseType: 'blob' });
  return data;
}

export async function fetchMetricsSummary(): Promise<OpsReport> {
  const { data } = await api.get('/report/summary');
  return data;
}

export async function fetchOpsStatus(): Promise<OpsStatus> {
  const { data } = await api.get('/ops/status');
  return data;
}

export async function fetchChunks(
  backend: 'pg' | 'es',
  fileName: string,
  page: number,
  size: number
): Promise<ChunkPage> {
  const { data } = await api.get('/ops/chunks', {
    params: { backend, fileName: fileName || undefined, page, size },
  });
  return data;
}

export async function clearCache(): Promise<void> {
  await api.post('/cache/clear');
}

export async function fetchLogs(limit = 100): Promise<RequestLog[]> {
  const { data } = await api.get('/logs', { params: { limit } });
  return data;
}

export async function clearLogs(): Promise<void> {
  await api.delete('/logs');
}

export async function fetchConfig(): Promise<SystemConfig> {
  const { data } = await api.get('/config');
  return data;
}

export async function updateConfig(config: SystemConfig): Promise<SystemConfig> {
  const { data } = await api.put('/config', config);
  return data;
}

export async function updateRetrievalMode(mode: string): Promise<SystemConfig> {
  const { data } = await api.put('/config/mode', { mode });
  return data;
}

export async function updateApiKey(apiKey: string): Promise<SystemConfig> {
  const { data } = await api.put('/config/apikey', { apiKey });
  return data;
}

export async function updateWebApiKey(apiKey: string): Promise<SystemConfig> {
  const { data } = await api.put('/config/websearch/apikey', { apiKey });
  return data;
}

export async function rebuildVectorIndex(): Promise<RebuildStatus> {
  const { data } = await api.post('/config/rebuild-vector-index');
  return data;
}

export async function rebuildPgIndex(): Promise<{ indexType: string; lists: number }> {
  const { data } = await api.post('/config/rebuild-pg-index');
  return data;
}

export async function fetchRebuildStatus(): Promise<RebuildStatus> {
  const { data } = await api.get('/config/rebuild-vector-index/status');
  return data;
}

export async function fetchEvaluationQuestions(): Promise<EvaluationQuestion[]> {
  const { data } = await api.get('/evaluation/questions');
  return data;
}

export async function createEvaluationQuestion(input: EvaluationQuestionInput): Promise<EvaluationQuestion> {
  const { data } = await api.post('/evaluation/questions', input);
  return data;
}

export async function updateEvaluationQuestion(id: string, input: EvaluationQuestionInput): Promise<EvaluationQuestion> {
  const { data } = await api.put(`/evaluation/questions/${id}`, input);
  return data;
}

export async function deleteEvaluationQuestion(id: string): Promise<void> {
  await api.delete(`/evaluation/questions/${id}`);
}

export async function fetchEvaluationHistory(): Promise<EvaluationRunMeta[]> {
  const { data } = await api.get('/evaluation/history');
  return data;
}

export async function fetchEvaluationStatus(): Promise<{ running: boolean }> {
  const { data } = await api.get('/evaluation/status');
  return data;
}

export async function cancelEvaluation(): Promise<void> {
  await api.post('/evaluation/cancel');
}

export async function fetchEvaluationRun(id: number): Promise<EvaluationReport> {
  const { data } = await api.get(`/evaluation/history/${id}`);
  return data;
}

export async function runEvaluation(
  modes: string[],
  clearCache: boolean,
  judge: { judgeEnabled: boolean; judgeModel: string },
  types: string[],
  onEvent: (evt: EvaluationEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const resp = await fetch('/api/evaluation/run', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ modes, clearCache, judgeEnabled: judge.judgeEnabled, judgeModel: judge.judgeModel, types }),
    signal,
  });

  if (!resp.ok || !resp.body) {
    const text = await resp.text().catch(() => '');
    throw new Error(text || `HTTP ${resp.status}`);
  }

  const reader = resp.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let idx: number;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const rawEvent = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      for (const line of rawEvent.split('\n')) {
        if (!line.startsWith('data:')) continue;
        const data = line.slice(5).trim();
        if (!data) continue;
        try {
          onEvent(JSON.parse(data) as EvaluationEvent);
        } catch {
          // ignore malformed chunk
        }
      }
    }
  }
}

export async function initDemo(onEvent: (evt: DemoEvent) => void): Promise<void> {
  const resp = await fetch('/api/demo/init', {
    method: 'POST',
    headers: authHeaders(),
  });

  if (!resp.ok || !resp.body) {
    const text = await resp.text().catch(() => '');
    throw new Error(text || `HTTP ${resp.status}`);
  }

  const reader = resp.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let idx: number;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const rawEvent = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      for (const line of rawEvent.split('\n')) {
        if (!line.startsWith('data:')) continue;
        const data = line.slice(5).trim();
        if (!data) continue;
        try {
          onEvent(JSON.parse(data) as DemoEvent);
        } catch {
          // ignore malformed chunk
        }
      }
    }
  }
}

export async function login(username: string, password: string, adminOnly = false): Promise<{ token: string; user: AuthUser }> {
  const { data } = await api.post('/auth/login', { username, password, adminOnly });
  return data;
}

export async function register(input: RegisterRequest): Promise<{ token: string; user: AuthUser }> {
  const { data } = await api.post('/auth/register', input);
  return data;
}

export async function logout(): Promise<void> {
  try {
    await api.post('/auth/logout');
  } catch {
    // ignore — clear local state regardless
  }
  clearAuth();
}

export async function fetchMe(): Promise<AuthUser> {
  const { data } = await api.get('/auth/me');
  return data;
}

export async function fetchGuestPermissions(): Promise<string[]> {
  const { data } = await api.get('/auth/guest-permissions');
  return data;
}

export async function fetchPermissions(): Promise<Permission[]> {
  const { data } = await api.get('/auth/permissions');
  return data;
}

export async function listUsers(): Promise<ManagedUser[]> {
  const { data } = await api.get('/auth/users');
  return data;
}

export async function createUser(input: UserRequest): Promise<ManagedUser> {
  const { data } = await api.post('/auth/users', input);
  return data;
}

export async function updateUser(id: number, input: UserRequest): Promise<ManagedUser> {
  const { data } = await api.put(`/auth/users/${id}`, input);
  return data;
}

export async function deleteUser(id: number): Promise<void> {
  await api.delete(`/auth/users/${id}`);
}

export async function listRoles(): Promise<Role[]> {
  const { data } = await api.get('/auth/roles');
  return data;
}

export async function createRole(input: RoleRequest): Promise<Role> {
  const { data } = await api.post('/auth/roles', input);
  return data;
}

export async function updateRole(id: number, input: RoleRequest): Promise<Role> {
  const { data } = await api.put(`/auth/roles/${id}`, input);
  return data;
}

export async function deleteRole(id: number): Promise<void> {
  await api.delete(`/auth/roles/${id}`);
}

export async function fetchMessages(limit = 20): Promise<AppNotification[]> {
  const { data } = await api.get('/messages', { params: { limit } });
  return data;
}

export async function fetchUnreadCount(): Promise<number> {
  const { data } = await api.get('/messages/unread-count');
  return data.count;
}

export async function markMessagesRead(): Promise<void> {
  await api.post('/messages/read');
}
