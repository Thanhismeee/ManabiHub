import { useEffect } from 'react';
import { axiosClient } from '../../../shared/api/axiosClient';

const LEASE_INTERVAL_MS = 60000; // Heartbeat every 60s

export function useLearningLease(courseId: string | undefined) {
  useEffect(() => {
    if (!courseId) return;

    const acquireLease = async () => {
      try {
        await axiosClient.post(`/learning-lease/acquire/${courseId}`);
      } catch (error) {
        // Errors like 409 are handled globally by axiosClient interceptor
        console.error('Failed to acquire learning lease', error);
      }
    };

    acquireLease(); // Initial acquire

    const interval = setInterval(acquireLease, LEASE_INTERVAL_MS);

    return () => clearInterval(interval);
  }, [courseId]);
}
