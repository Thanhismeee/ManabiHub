import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { VideoBlock } from './CourseLearningPage';
import type { LearningLessonBlock } from '../types';

vi.mock('../services/learningService', () => ({
  learningService: {
    saveVideoProgress: vi.fn().mockResolvedValue({ status: 'IN_PROGRESS' }),
    saveVideoProgressKeepalive: vi.fn(),
  },
}));

const block: LearningLessonBlock = {
  id: 'video-1',
  moduleId: 'module-1',
  type: 'VIDEO',
  title: 'Video lesson',
  // Use the real ReactPlayer so its volume synchronization runs on every render.
  videoUrl: '/lesson.mp4',
  durationMinutes: 5,
  quizOptions: [],
  quizItems: [],
  flashcards: [],
  orderIndex: 1,
  contentAvailable: true,
  progressStatus: 'IN_PROGRESS',
  current: true,
  locked: false,
};

function renderVideo() {
  const props = {
    block,
    onProgressSaved: vi.fn(),
    onProgressSaveError: vi.fn(),
    onLoadingChange: vi.fn(),
  };
  const view = render(<VideoBlock {...props} />);
  const video = view.container.querySelector('video')!;
  Object.defineProperties(video, {
    duration: { configurable: true, value: 300 },
    paused: { configurable: true, value: false },
  });
  fireEvent.loadedMetadata(video);
  fireEvent.play(video);
  return { ...view, video, props };
}

describe('course video audio controls', () => {
  beforeEach(() => window.localStorage.clear());
  afterEach(cleanup);

  it.each([0.2, 0])('keeps volume %s when playback progress and lesson data update', (volume) => {
    const { video, props, rerender } = renderVideo();

    video.volume = volume;
    fireEvent.volumeChange(video);
    video.currentTime = 1;
    fireEvent.timeUpdate(video);

    expect(screen.getByText('Đã xem 0:01 / 5:00')).toBeInTheDocument();
    expect(video.volume).toBe(volume);

    rerender(<VideoBlock {...props} block={{ ...block, watchedVideoSeconds: 1 }} />);
    expect(video.volume).toBe(volume);

    // Pause and buffering also update the surrounding lesson UI.
    fireEvent.pause(video);
    fireEvent.waiting(video);
    expect(video.volume).toBe(volume);
    fireEvent.playing(video);
    expect(video.volume).toBe(volume);
  });

  it('allows repeated volume changes and mute/unmute without restoring full volume', () => {
    const { video } = renderVideo();

    for (const [index, volume] of [0.6, 0.1, 0, 0.3].entries()) {
      video.volume = volume;
      fireEvent.volumeChange(video);
      video.currentTime = index + 1;
      fireEvent.timeUpdate(video);
      expect(video.volume).toBe(volume);
    }

    for (const muted of [true, false]) {
      video.muted = muted;
      fireEvent.volumeChange(video);
      fireEvent.waiting(video);
      fireEvent.playing(video);
      expect(video.muted).toBe(muted);
      expect(video.volume).toBe(0.3);
    }
  });
});
