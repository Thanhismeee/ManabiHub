import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { OnboardingGuide, type OnboardingStep } from './OnboardingGuide';

const steps: OnboardingStep[] = [
  {
    id: 'first',
    title: 'Bắt đầu ở đây',
    description: 'Đây là khu vực đầu tiên cần biết.',
    targetId: 'guide-target',
  },
  {
    id: 'second',
    title: 'Tiếp tục từ đây',
    description: 'Đây là khu vực tiếp theo.',
  },
];

describe('OnboardingGuide', () => {
  beforeEach(() => window.localStorage.clear());
  afterEach(() => window.localStorage.clear());

  it('walks through steps and only persists after the account opts out', () => {
    const { unmount } = render(
      <>
        <div data-onboarding-target="guide-target">Khu vực dashboard</div>
        <OnboardingGuide
          scope="student-dashboard"
          accountKey="student-1"
          title="Hướng dẫn dashboard"
          intro="Giới thiệu ngắn."
          steps={steps}
        />
      </>,
    );

    expect(screen.getByRole('dialog')).toHaveTextContent('Bắt đầu ở đây');
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp theo' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('Tiếp tục từ đây');

    fireEvent.click(screen.getByRole('button', { name: 'Bỏ qua hướng dẫn' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(window.localStorage.getItem('manabihub.onboarding.v1.student-dashboard.student-1')).toBe('completed');

    unmount();
    render(
      <OnboardingGuide
        scope="student-dashboard"
        accountKey="student-1"
        title="Hướng dẫn dashboard"
        intro="Giới thiệu ngắn."
        steps={steps}
      />,
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows again when closed without opting out and isolates accounts', () => {
    const { unmount } = render(
      <OnboardingGuide
        scope="teacher-dashboard"
        accountKey="teacher-1"
        title="Hướng dẫn giảng viên"
        intro="Giới thiệu ngắn."
        steps={steps}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Đóng hướng dẫn' }));
    expect(window.localStorage.length).toBe(0);
    unmount();

    render(
      <OnboardingGuide
        scope="teacher-dashboard"
        accountKey="teacher-1"
        title="Hướng dẫn giảng viên"
        intro="Giới thiệu ngắn."
        steps={steps}
      />,
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    unmount();
    render(
      <OnboardingGuide
        scope="teacher-dashboard"
        accountKey="teacher-2"
        title="Hướng dẫn giảng viên"
        intro="Giới thiệu ngắn."
        steps={steps}
      />,
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
