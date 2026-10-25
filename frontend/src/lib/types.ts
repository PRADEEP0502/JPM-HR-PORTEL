export type Role = 'ADMIN' | 'HR';
export type ApplicationStatus = 'NEW' | 'UNDER_REVIEW' | 'SHORTLISTED' | 'SELECTED' | 'REJECTED' | 'ON_HOLD';
export type InterviewStatus = 'NOT_SCHEDULED' | 'SCHEDULED' | 'COMPLETED' | 'RESCHEDULED' | 'CANCELLED';
export type VerificationStatus = 'PENDING' | 'VERIFIED';
export type CandidateSource = 'GOOGLE_SHEETS' | 'MANUAL' | 'DEMO';
export type ExperienceBucket = 'FRESHER' | 'Y0_2' | 'Y2_5' | 'Y5_10' | 'Y10_PLUS' | 'UNKNOWN';
export type InterviewMode = 'IN_PERSON' | 'VIDEO' | 'PHONE';
export type DocType = 'RESUME' | 'AADHAAR' | 'CERTIFICATE' | 'OTHER';

export interface User {
  id: number;
  username: string;
  fullName: string;
  email: string | null;
  role: Role;
  enabled: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Department {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
}

export interface CandidateListItem {
  id: number;
  candidateCode: string;
  fullName: string;
  mobile: string | null;
  email: string | null;
  departmentId: number | null;
  departmentName: string | null;
  qualification: string | null;
  experienceYears: number | null;
  applicationDate: string;
  applicationStatus: ApplicationStatus;
  interviewStatus: InterviewStatus;
  verificationStatus: VerificationStatus;
  source: CandidateSource;
}

export interface DocumentInfo {
  id: number;
  docType: DocType;
  storage: 'LOCAL' | 'DRIVE';
  fileName: string | null;
  contentType: string | null;
  sizeBytes: number | null;
  uploadedBy: string | null;
  encrypted: boolean;
  createdAt: string;
}

export interface Interview {
  id: number;
  candidateId: number;
  candidateCode: string;
  candidateName: string;
  departmentName: string | null;
  scheduledAt: string;
  durationMinutes: number;
  roundName: string;
  interviewer: string;
  mode: InterviewMode;
  location: string | null;
  status: InterviewStatus;
  remarks: string | null;
  createdBy: string | null;
  updatedAt: string;
}

export interface Remark {
  id: number;
  author: string;
  remark: string;
  createdAt: string;
}

export interface Activity {
  at: string;
  actor: string;
  action: string;
  details: string | null;
}

export interface CandidateDetail {
  id: number;
  candidateCode: string;
  fullName: string;
  dateOfBirth: string | null;
  mobile: string | null;
  email: string | null;
  address: string | null;
  qualification: string | null;
  institution: string | null;
  passingYear: number | null;
  previousCompany: string | null;
  experienceYears: number | null;
  previousDesignation: string | null;
  skills: string | null;
  expectedSalary: string | null;
  departmentId: number | null;
  departmentName: string | null;
  departmentRaw: string | null;
  applicationDate: string;
  appliedAt: string;
  applicationStatus: ApplicationStatus;
  interviewStatus: InterviewStatus;
  verificationStatus: VerificationStatus;
  referenceName: string | null;
  referenceRelation: string | null;
  referenceContact: string | null;
  familyRefName: string | null;
  familyRefRelation: string | null;
  familyRefContact: string | null;
  source: CandidateSource;
  dataIssues: string | null;
  version: number;
  updatedAt: string;
  documents: DocumentInfo[];
  interviews: Interview[];
  remarks: Remark[];
  activity: Activity[];
}

export interface Count {
  key: string;
  label: string;
  count: number;
}

export interface DepartmentCount {
  departmentId: number | null;
  name: string;
  total: number;
  today: number;
  open: number;
}

export interface Spotlight {
  key: string;
  label: string;
  count: number;
  /** Most recently updated candidates in this stage (preview only; `count` is the total). */
  recent: CandidateListItem[];
}

export interface Dashboard {
  generatedAt: string;
  demoMode: boolean;
  demoCandidates: number;
  summary: {
    totalCandidates: number;
    todayApplications: number;
    shortlisted: number;
    interviewsScheduled: number;
    selected: number;
    pendingVerification: number;
  };
  departmentWise: Count[];
  monthly: Count[];
  applicationStatus: Count[];
  interviewStatus: Count[];
  experience: Count[];
  lastSevenDays: Count[];
  spotlights: Spotlight[];
  todaysInterviews: Interview[];
  upcomingInterviews: Interview[];
  interviewersToday: string[];
  departments: DepartmentCount[];
  recentCandidates: CandidateListItem[];
}

export interface SyncStatus {
  configured: boolean;
  credentialsConfigured: boolean;
  spreadsheetConfigured: boolean;
  autoSyncEnabled: boolean;
  syncIntervalMinutes: number;
  running: boolean;
  formUrl: string | null;
  lastSyncAt: string | null;
  lastStatus: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED' | null;
  lastSuccessAt: string | null;
  lastError: string | null;
}

export interface SyncRun {
  id: number;
  startedAt: string;
  finishedAt: string | null;
  triggeredBy: string;
  status: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED';
  rowsRead: number;
  createdCount: number;
  duplicateCount: number;
  skippedCount: number;
  warningCount: number;
  messages: string[];
  errorMessage: string | null;
}

export interface IntegrationSettings {
  spreadsheetId: string | null;
  sheetRange: string | null;
  formUrl: string | null;
  autoSyncEnabled: boolean;
  syncIntervalMinutes: number;
  columnMapping: Record<string, string>;
  fields: { key: string; label: string; required: boolean }[];
  credentialsConfigured: boolean;
  serviceAccountEmail: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface SyncTestResult {
  ok: boolean;
  spreadsheetTitle: string | null;
  headers: string[];
  detectedMapping: Record<string, string>;
  unmappedHeaders: string[];
  problems: string[];
  dataRows: number;
}

export type ReportType = 'DEPARTMENT' | 'MONTHLY' | 'SELECTED' | 'REJECTED' | 'PENDING_VERIFICATION' | 'INTERVIEW_STATUS';

export interface Report {
  type: ReportType;
  title: string;
  from: string | null;
  to: string | null;
  generatedAt: string;
  columns: { key: string; label: string }[];
  rows: Record<string, string | number | null>[];
  chart: { label: string; value: number }[];
}

export interface AuditEntry {
  id: number;
  occurredAt: string;
  actor: string;
  action: string;
  entityType: string | null;
  entityId: number | null;
  details: string | null;
}

export interface NotificationItem {
  id: string;
  type: 'NEW_CANDIDATE' | 'UPCOMING_INTERVIEW' | 'SYNC_FAILED';
  title: string;
  message: string;
  at: string;
  link: string;
  unread: boolean;
}

// ---------------------------------------------------------------- communication

export type CommChannel = 'CALL' | 'WHATSAPP' | 'EMAIL';
export type CommStatus = 'CALL_INITIATED' | 'PREPARED' | 'MARKED_SENT' | 'ACCEPTED' | 'DELIVERED' | 'FAILED';
export type CallOutcome = 'CONNECTED' | 'NO_ANSWER' | 'BUSY' | 'SWITCHED_OFF' | 'WRONG_NUMBER' | 'CALL_BACK_LATER';

export interface CommunicationRecord {
  id: number;
  candidateId: number;
  interviewId: number | null;
  channel: CommChannel;
  recipient: string;
  candidateName: string;
  templateVersion: number | null;
  subject: string | null;
  content: string | null;
  status: CommStatus;
  provider: string | null;
  providerMessageId: string | null;
  errorMessage: string | null;
  callOutcome: CallOutcome | null;
  notes: string | null;
  retryOfId: number | null;
  initiatedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface Compose {
  candidateId: number;
  candidateCode: string;
  candidateName: string;
  department: string | null;
  contact: {
    mobile: string | null;
    mobileValid: boolean;
    whatsappNumber: string | null;
    mobileProblem: string | null;
    email: string | null;
    emailValid: boolean;
    emailProblem: string | null;
  };
  interview: {
    id: number;
    scheduledAt: string;
    date: string;
    time: string;
    roundName: string;
    interviewer: string;
    status: InterviewStatus;
    venue: string;
    location: string | null;
  } | null;
  whatsapp: { ready: boolean; message: string | null; url: string | null; templateVersion: number; problems: string[] };
  email: {
    ready: boolean;
    to: string | null;
    subject: string | null;
    body: string | null;
    templateVersion: number;
    problems: string[];
    serviceConfigured: boolean;
  };
}

export interface CommSettings {
  companyName: string;
  defaultVenue: string;
  hrSignature: string;
  defaultCountryCode: string;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface MessageTemplateInfo {
  id: number;
  channel: 'WHATSAPP' | 'EMAIL';
  version: number;
  subject: string | null;
  body: string;
  active: boolean;
  createdBy: string;
  createdAt: string;
}

export interface TemplatesOverview {
  whatsapp: MessageTemplateInfo;
  email: MessageTemplateInfo;
  settings: CommSettings;
  placeholders: Record<string, string>;
  required: string[];
  emailService: { configured: boolean; provider: string | null; fromAddress: string | null; fromName: string | null };
}
