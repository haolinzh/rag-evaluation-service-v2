export interface DocumentMeta {
  id: number;
  fileName: string;
  fileSize: number;
  chunkCount: number;
  createdAt: string;
  updatedAt?: string;
  splitMode?: string;
  chunkSize?: number;
  overlap?: number;
  delimiter?: string;
  status?: 'PENDING' | 'READY' | 'FAILED';
  errorMessage?: string;
  embeddingModel?: string;
  embeddingDimension?: number;
  ownerName?: string;
  ownerDepartment?: string;
  visibility?: 'PUBLIC' | 'DEPARTMENT' | 'EXECUTIVE' | 'PRIVATE';
}

export interface AuthUser {
  id: number;
  username: string;
  displayName: string;
  department: string | null;
  permissions: string[];
}

export interface Permission {
  code: string;
  name: string;
  group: string;
}

export interface Role {
  id: number;
  code: string;
  name: string;
  description: string | null;
  builtin: boolean;
  permissionCodes: string[];
}

export interface ManagedUser {
  id: number;
  username: string;
  displayName: string | null;
  department: string | null;
  enabled: boolean;
  roleCodes: string[];
}

export interface UserRequest {
  username: string;
  password?: string;
  displayName?: string;
  department?: string;
  enabled?: boolean;
  roleCodes?: string[];
}

export interface RegisterRequest {
  username: string;
  password: string;
  displayName?: string;
  department?: string;
}

export interface RoleRequest {
  code: string;
  name: string;
  description?: string;
  permissionCodes?: string[];
}

export interface ChunkPreview {
  chunkIndex: number;
  chapter: string | null;
  section: string | null;
  content: string;
}

export interface ChunkConfig {
  splitMode: 'size' | 'delimiter';
  chunkSize: number;
  delimiter: string;
  overlap: number;
}

export interface Source {
  fileName: string;
  snippet: string;
  content?: string;
  score: number;
  sourceType: string;
  url?: string | null;
}

export interface ChatResponse {
  content: string;
  thinking?: string | null;
  retrievalMode: string;
  sources: Source[];
  refusal: boolean;
  refusalReason: string | null;
}

export interface ChatMessage {
  id: number;
  sessionId: string;
  role: string;
  content: string;
  createdAt: string;
  thinking?: string | null;
  retrievalMode?: string | null;
  refusal?: boolean | null;
  sources?: string | null;
}

export interface OpsReport {
  totalRequests: number;
  p50LatencyMs: number;
  p95LatencyMs: number;
  missP50LatencyMs: number;
  missP95LatencyMs: number;
  totalTokens: number;
  cacheHitRate: number;
  refusalRate: number;
  answerComplianceRate: number;
}

export interface RequestLog {
  id: number;
  requestId: string;
  sessionId: string;
  ownerId: number | null;
  ownerUsername: string | null;
  createdAt: string;
  question: string;
  rewrittenQuery?: string | null;
  answer: string | null;
  model: string;
  retrievalMode: string;
  chatMode?: string;
  hitDocuments: string;
  retrievedChunks: string | null;
  rerankCandidates: string | null;
  prompt: string | null;
  responseTimeMs: number;
  llmCallCount: number;
  cacheHit: boolean;
  refusal: boolean;
  refusalReason: string | null;
  retrievalLatencyMs: number;
  generationLatencyMs: number;
  promptTokens: number;
  completionTokens: number;
  chunksRetrieved: number;
  maxChunkScore: number;
  piiRedactions: number;
  keywordCount: number;
  vectorCount: number;
  overlapCount: number;
  embeddingLatencyMs: number;
  keywordLatencyMs: number;
  vectorLatencyMs: number;
  rerankLatencyMs: number;
  cacheLookupLatencyMs: number;
  webSearchUsed: boolean;
  webSearchLatencyMs: number;
  status: string;
}

export interface ModelOption {
  group: 'chat' | 'embedding' | 'rerank';
  id: string;
  label: string;
  dimensions: number | null;
}

export interface SystemConfig {
  retrieval: {
    mode: string;
    topK: number;
    recallSizeMultiplier: number;
    rrfK: number;
    rerankCandidates: number;
    similarityThreshold: number;
    queryRewriteEnabled: boolean;
    contextualRetrievalEnabled: boolean;
  };
  models: { chat: string; embedding: string; rerank: string };
  safety: {
    minSimilarity: number;
    enableOutOfScopeCheck: boolean;
    outOfScopeThreshold: number;
    forbiddenKeywords: string;
  };
  cache: { enabled: boolean; ttlSeconds: number };
  judge: { enabled: boolean; model: string; temperature: number };
  generation: { temperature: number; topP: number; maxTokens: number; systemPrompt: string };
  vector: {
    backend: 'pgvector' | 'elasticsearch';
    pgvector: { indexType: 'ivfflat' | 'hnsw'; lists: number; probes: number; efSearch: number };
    elasticsearch: { numCandidates: number };
  };
  modelOptions: ModelOption[];
  embeddingDimension: number;
  apiKeyMasked?: string | null;
  webSearch: {
    enabled: boolean;
    provider: string;
    maxResults: number;
  };
  webApiKeyMasked?: string | null;
  chatMode?: 'workflow' | 'agent';
  agentModel?: string;
}

export interface EvaluationQuestion {
  id: string;
  question: string;
  language: string;
  expectedType: string;
  difficulty: string;
  createdAt?: string | null;
}

export interface EvaluationQuestionInput {
  question: string;
  language: string;
  expectedType: string;
  difficulty: string;
}

export interface EvaluationQuestionResult {
  questionId: string;
  question: string;
  language: string;
  expectedType: string;
  answer: string;
  retrievalMode: string;
  refusal: boolean;
  sources: Source[];
  latencyMs: number;
  faithfulness: number;
  contextPrecision: number;
  answerCompliance: number;
  refusalAppropriate: number;
  styleConsistent: number;
  answerRelevancy?: number | null;
  judgeUsed?: boolean;
  judgeModel?: string | null;
  judgeReason?: string | null;
  error?: string;
}

export interface EvaluationSummary {
  mode: string;
  totalQuestions: number;
  answeredQuestions: number;
  avgFaithfulness: number;
  avgContextPrecision: number;
  avgAnswerCompliance: number;
  avgRefusalAppropriate: number;
  avgStyleConsistent: number;
  avgAnswerRelevancy?: number | null;
  p50LatencyMs: number;
  p95LatencyMs: number;
  avgLatencyMs: number;
}

export interface EvaluationReport {
  modes: string[];
  judgeEnabled?: boolean;
  judgeModel?: string | null;
  summaries: EvaluationSummary[];
  results: Record<string, EvaluationQuestionResult[]>;
}

export interface EvaluationRunMeta {
  id: number;
  createdAt: string;
  modes: string[];
  judgeEnabled?: boolean;
  judgeModel?: string | null;
  runName?: string | null;
}

export interface EsStatus {
  clusterName: string | null;
  version: string | null;
  status: string | null;
  nodeCount: number;
  dataNodeCount: number;
  activePrimaryShards: number;
  activeShards: number;
  relocatingShards: number;
  unassignedShards: number;
  pendingTasks: number;
  indexName: string;
  docCount: number;
  storeSizeBytes: number;
  heapUsedPercent: number;
  cpuPercent: number;
  error: string | null;
}

export interface PgStatus {
  version: string | null;
  databaseSizeBytes: number;
  numBackends: number;
  xactCommit: number;
  xactRollback: number;
  deadlocks: number;
  cacheHitRatio: number;
  tableName: string;
  liveTuples: number;
  deadTuples: number;
  seqScan: number;
  indexScan: number;
  chunkCount: number;
  indexSizeBytes: number;
  error: string | null;
}

export interface OpsStatus {
  es: EsStatus;
  pg: PgStatus;
}

export interface ChunkRecord {
  chunkId: string;
  fileName: string;
  chapter: string | null;
  section: string | null;
  chunkIndex: number | null;
  content: string;
}

export interface ChunkPage {
  total: number;
  items: ChunkRecord[];
}

export interface RebuildStatus {
  running: boolean;
  processedDocuments: number;
  totalDocuments: number;
  chunkCount: number;
  phase: string;
  message: string | null;
}

export type EvaluationEvent =
  | { type: 'ingest_start'; missing: string[]; total: number }
  | { type: 'ingesting'; fileName: string }
  | { type: 'ingested'; fileName: string; chunks: number }
  | { type: 'ingest_error'; fileName: string; message: string }
  | { type: 'ingest_done'; ingested: number; failed: string[]; skipped?: number }
  | { type: 'start'; modes: string[]; totalQuestions: number }
  | { type: 'mode_start'; mode: string; index: number; totalModes: number }
  | { type: 'question_start'; mode: string; questionId: string; index: number; total: number; question: string }
  | { type: 'question_done'; mode: string; questionId: string; index: number; total: number; result: EvaluationQuestionResult }
  | { type: 'question_error'; mode: string; questionId: string; index: number; total: number; question: string; message: string }
  | { type: 'mode_done'; mode: string; summary: EvaluationSummary }
  | { type: 'done'; report: EvaluationReport }
  | { type: 'cancelled' }
  | { type: 'error'; message: string };

export type DemoEvent =
  | { type: 'phase'; phase: string; message: string }
  | { type: 'rbac'; permissionsCreated: number; roleCreated: boolean; userCreated: boolean }
  | EvaluationEvent;
