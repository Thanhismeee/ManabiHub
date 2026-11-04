declare global {
  interface Window {
    SDK?: {
      launch: (config: Record<string, unknown>) => void;
    };
  }
}

export {};
