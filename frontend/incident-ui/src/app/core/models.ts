export interface Incident {
  id: string;
  serviceId: string;
  serviceName: string;
  title: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'DETECTED' | 'INVESTIGATING' | 'MITIGATED' | 'RESOLVED';
  detectedAt: string;
  resolvedAt: string | null;
}

export interface TimelineEntry {
  timestamp: string;
  type: 'DEPLOYMENT' | 'LOG' | 'INCIDENT_EVENT';
  summary: string;
  level: string | null;
  sourceRef: string;
  occurrences: number;
}

export interface Timeline {
  incidentId: string;
  service: string;
  windowStart: string;
  windowEnd: string;
  /** False when the log service could not be reached, so log evidence is missing, not absent. */
  logsAvailable: boolean;
  entries: TimelineEntry[];
}

export interface EvidenceClaim {
  claim: string;
  sourceRefs: string[];
  verified: boolean;
}

export interface AlternativeHypothesis {
  hypothesis: string;
  assessment: 'RULED_OUT' | 'LESS_LIKELY' | 'NOT_EVALUABLE';
  reason: string;
  sourceRefs: string[];
}

export interface RelatedIncident {
  sourceRef: string;
  title: string | null;
  relevance: string;
}

export interface DeploymentCorrelation {
  deploymentInWindow: boolean;
  deploymentRef: string | null;
  deploymentSummary: string | null;
  deployedAt: string | null;
  minutesBeforeDetection: number | null;
  firstErrorAt: string | null;
  minutesFromDeploymentToFirstError: number | null;
  note: string;
}

export interface GroundingReport {
  claimsTotal: number;
  claimsVerified: number;
  invalidCitationsRemoved: number;
  downgradedToInsufficientEvidence: boolean;
  confidenceCapped: boolean;
  evidenceTruncated: boolean;
  notes: string[];
}

export interface AnalysisResult {
  status: 'ROOT_CAUSE_IDENTIFIED' | 'INSUFFICIENT_EVIDENCE';
  rootCause: string;
  confidence: number;
  affectedServices: string[];
  evidence: EvidenceClaim[];
  alternativeHypotheses: AlternativeHypothesis[];
  contributingFactors: string[];
  recommendations: string[];
  relatedIncidents: RelatedIncident[];
  deploymentCorrelation: DeploymentCorrelation;
  sources: { ref: string; category: string; title: string | null }[];
  grounding: GroundingReport;
}

export interface AnalysisResponse {
  id: string;
  incidentId: string;
  model: string;
  /** True when served from storage because the evidence had not changed: no model call was made. */
  cached: boolean;
  modelCalls: number;
  promptTokens: number | null;
  completionTokens: number | null;
  latencyMs: number;
  createdAt: string;
  analysis: AnalysisResult;
}

export interface ChatReply {
  id: string;
  incidentId: string;
  reply: string;
  sources: string[];
  removedCitations: string[];
  model: string;
  latencyMs: number;
  createdAt: string;
}

export interface ChatMessage {
  id: string;
  incidentId: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  sources: string[];
  createdAt: string;
}

export interface ModelUsage {
  model: string;
  requestsToday: number;
  requestsPerDayLimit: number;
  requestsLastMinute: number;
  requestsPerMinuteLimit: number;
}

export interface UsageSnapshot {
  enabled: boolean;
  apiKeyConfigured: boolean;
  activeModel: string;
  maxConcurrentCalls: number;
  callsInFlight: number;
  quotaDay: string;
  quotaZone: string;
  dailyResetAt: string;
  models: ModelUsage[];
}

export interface AuthSession {
  accessToken: string;
  username: string;
  roles: string[];
  expiresAt: string;
}
