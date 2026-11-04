export type OperationsHealthStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN' | string;
export type OperationsLogLevel = 'ERROR' | 'WARN' | 'INFO';

export interface OperationsOverview {
  applicationStatus: OperationsHealthStatus;
  databaseStatus: OperationsHealthStatus;
  startedAt: string | null;
  uptimeSeconds: number;
  activeProfiles: string[];
  timezone: string;
  businessTimezone: string;
  javaVersion: string;
  jvmName: string;
  memory: {
    heapUsedBytes: number;
    heapCommittedBytes: number;
    heapMaxBytes: number;
    nonHeapUsedBytes: number;
  };
  threads: {
    live: number;
    daemon: number;
    peak: number;
  };
  build: {
    applicationName: string;
    version: string | null;
    buildTime: string | null;
    gitCommit: string | null;
  };
}

export type RuntimeComponent =
  | 'DATABASE'
  | 'MAIL'
  | 'GOOGLE_OAUTH'
  | 'VNPAY'
  | 'VNPT_EKYC'
  | 'AI_PROVIDER'
  | 'SMS_PROVIDER'
  | 'JWT_SIGNING'
  | 'PAYOUT_SECURITY'
  | 'CORS_POLICY';

export interface RuntimeComponentStatus {
  component: RuntimeComponent;
  configured: boolean;
  enabled: boolean;
}

export interface RuntimeConfiguration {
  components: RuntimeComponentStatus[];
}

export interface OperationsLogEntry {
  timestamp: string;
  level: OperationsLogLevel | string;
  logger: string;
  message: string;
  correlationId: string | null;
  exception: string | null;
}

export interface OperationsLogPage {
  logSource: 'CURRENT_INSTANCE_MEMORY';
  currentProcessStartedAt: string;
  items: OperationsLogEntry[];
  returned: number;
  capacity: number;
}

export interface OperationsLogFilters {
  level?: OperationsLogLevel;
  query?: string;
  correlationId?: string;
  limit?: number;
}
