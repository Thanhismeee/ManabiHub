import { describe, expect, it } from 'vitest';
import { applySequentialLocks } from './CourseLearningPage';
import {
  formatVideoTime,
  normalizeWatchedSecondsAtVideoEnd,
  normalizeVideoProgressPayload,
  resolveVideoResumePosition,
} from '../utils/videoProgress';
import type { LearningLessonBlock, LearningModule } from '../types';

function lesson(id: string, status: LearningLessonBlock['progressStatus']): LearningLessonBlock {
  return {
    id,
    moduleId: 'module-1',
    type: 'TEXT',
    title: id,
    quizOptions: [],
    quizItems: [],
    flashcards: [],
    orderIndex: Number(id),
    contentAvailable: true,
    progressStatus: status,
    current: false,
    locked: false,
  };
}

describe('CourseLearningPage access guards', () => {
  it('derives sequential locks even when the API response omits them', () => {
    const modules: LearningModule[] = [{
      id: 'module-1',
      title: 'Module 1',
      orderIndex: 1,
      blocks: [
        lesson('1', 'NOT_STARTED'),
        lesson('2', 'NOT_STARTED'),
        lesson('3', 'COMPLETED'),
      ],
    }];

    const normalized = applySequentialLocks(modules);

    expect(normalized[0].blocks.map((block) => block.locked)).toEqual([false, true, false]);
    expect(modules[0].blocks.map((block) => block.locked)).toEqual([false, false, false]);
  });

  it('opens the next lesson only after all previous lessons are completed', () => {
    const modules: LearningModule[] = [{
      id: 'module-1',
      title: 'Module 1',
      orderIndex: 1,
      blocks: [lesson('1', 'COMPLETED'), lesson('2', 'COMPLETED'), lesson('3', 'NOT_STARTED')],
    }];

    expect(applySequentialLocks(modules)[0].blocks.map((block) => block.locked)).toEqual([false, false, false]);
  });
});

describe('CourseLearningPage video completion guards', () => {
  it('normalizes minor browser timing gaps when playback reaches the real end', () => {
    expect(normalizeWatchedSecondsAtVideoEnd(286, 300)).toBe(300);
  });

  it('does not complete a video that was mostly skipped', () => {
    expect(normalizeWatchedSecondsAtVideoEnd(120, 300)).toBe(120);
  });

  it('keeps the watched value when media duration is unavailable', () => {
    expect(normalizeWatchedSecondsAtVideoEnd(120, Number.NaN)).toBe(120);
  });
});

describe('CourseLearningPage video resume helpers', () => {
  it('formats video time for short and long lessons', () => {
    expect(formatVideoTime(65)).toBe('1:05');
    expect(formatVideoTime(3661)).toBe('1:01:01');
  });

  it('prefers the newest local checkpoint when the server save is older', () => {
    expect(resolveVideoResumePosition(30, {
      positionSeconds: 42,
      watchedSeconds: 42,
      savedAt: Date.now(),
    })).toBe(42);
  });

  it('keeps the resume point inside the media duration', () => {
    expect(resolveVideoResumePosition(120, null, 100)).toBe(99);
  });

  it('caps replayed watch time and end-position rounding to the media duration', () => {
    expect(normalizeVideoProgressPayload(136, 142, 135.8)).toEqual({
      positionSeconds: 135,
      watchedSeconds: 135,
      mediaDurationSeconds: 135,
    });
  });
});
