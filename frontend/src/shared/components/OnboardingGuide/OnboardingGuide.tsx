import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  Stack,
  Typography,
} from '@mui/material';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded';
import CheckRoundedIcon from '@mui/icons-material/CheckRounded';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import HelpOutlineRoundedIcon from '@mui/icons-material/HelpOutlineRounded';

export interface OnboardingStep {
  id: string;
  title: string;
  description: string;
  targetId?: string;
}

export interface OnboardingGuideProps {
  /** Stable scope name. A separate completion flag is stored for every scope. */
  scope: string;
  title: string;
  intro: string;
  steps: OnboardingStep[];
  /** Prefer a stable user id. The public JWT subject is used when omitted. */
  accountKey?: string | null;
}

export const ONBOARDING_STORAGE_PREFIX = 'manabihub.onboarding.v1';

function storageKey(scope: string, accountKey?: string | null) {
  const account = accountKey?.trim() || 'anonymous';
  return `${ONBOARDING_STORAGE_PREFIX}.${scope}.${encodeURIComponent(account)}`;
}

function readCompleted(key: string) {
  if (typeof window === 'undefined') return false;
  try {
    return window.localStorage.getItem(key) === 'completed';
  } catch {
    return false;
  }
}

function markCompleted(key: string) {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(key, 'completed');
  } catch {
    // Private browsing or a storage policy must not prevent the guide from closing.
  }
}

type TargetRect = { top: number; right: number; bottom: number; left: number; width: number; height: number };
type PopoverPlacement = { top: number; left: number; placement: 'above' | 'below' | 'center' };

const POPOVER_WIDTH = 420;
const VIEWPORT_MARGIN = 16;

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function getPopoverPlacement(rect: TargetRect | null): PopoverPlacement {
  if (!rect || typeof window === 'undefined' || rect.width <= 0 || rect.height <= 0) {
    return { top: 0, left: 0, placement: 'center' };
  }

  const width = Math.min(POPOVER_WIDTH, window.innerWidth - VIEWPORT_MARGIN * 2);
  const left = clamp(
    rect.left + rect.width / 2 - width / 2,
    VIEWPORT_MARGIN,
    Math.max(VIEWPORT_MARGIN, window.innerWidth - width - VIEWPORT_MARGIN),
  );
  const enoughBelow = window.innerHeight - rect.bottom >= 260;
  return {
    top: enoughBelow ? rect.bottom + 14 : rect.top - 14,
    left,
    placement: enoughBelow ? 'below' : 'above',
  };
}

function SpotlightBackdrop({ target, onClose }: { target: TargetRect | null; onClose: () => void }) {
  const panelSx = {
    position: 'fixed' as const,
    bgcolor: 'rgba(15, 23, 42, 0.56)',
    zIndex: 1290,
  };

  if (!target || target.width <= 0 || target.height <= 0) {
    return <Box aria-hidden="true" onClick={onClose} sx={{ ...panelSx, inset: 0 }} />;
  }

  return (
    <>
      <Box aria-hidden="true" onClick={onClose} sx={{ ...panelSx, top: 0, left: 0, right: 0, height: target.top }} />
      <Box aria-hidden="true" onClick={onClose} sx={{ ...panelSx, top: target.bottom, left: 0, right: 0, bottom: 0 }} />
      <Box aria-hidden="true" onClick={onClose} sx={{ ...panelSx, top: target.top, left: 0, width: target.left, height: target.height }} />
      <Box aria-hidden="true" onClick={onClose} sx={{ ...panelSx, top: target.top, left: target.right, right: 0, height: target.height }} />
    </>
  );
}

/**
 * First-run guide for authenticated workspaces. Steps with a target use a
 * spotlight and an anchored coachmark; steps without a target fall back to a
 * centered dialog. Closing with the skip link persists completion for the
 * current account, while the X only dismisses this visit.
 */
export function OnboardingGuide({
  scope,
  title,
  intro,
  steps,
  accountKey,
}: OnboardingGuideProps) {
  const key = useMemo(() => storageKey(scope, accountKey), [scope, accountKey]);
  const [open, setOpen] = useState(() => steps.length > 0 && !readCompleted(key));
  const [stepIndex, setStepIndex] = useState(0);
  const [targetRect, setTargetRect] = useState<TargetRect | null>(null);
  const [placement, setPlacement] = useState<PopoverPlacement>({ top: 0, left: 0, placement: 'center' });
  const highlightedNode = useRef<HTMLElement | null>(null);
  const previousStyle = useRef({ boxShadow: '', position: '', zIndex: '' });

  const activeStep = steps[stepIndex];

  useEffect(() => {
    setOpen(steps.length > 0 && !readCompleted(key));
    setStepIndex(0);
  }, [key, steps.length]);

  useLayoutEffect(() => {
    const previous = highlightedNode.current;
    if (previous) {
      previous.style.boxShadow = previousStyle.current.boxShadow;
      previous.style.position = previousStyle.current.position;
      previous.style.zIndex = previousStyle.current.zIndex;
      previous.removeAttribute('data-onboarding-active');
    }

    highlightedNode.current = null;
    setTargetRect(null);
    setPlacement({ top: 0, left: 0, placement: 'center' });

    if (!open || !activeStep?.targetId || typeof document === 'undefined') return undefined;

    const node = document.querySelector<HTMLElement>(
      `[data-onboarding-target="${activeStep.targetId}"]`,
    );
    if (!node) return undefined;

    previousStyle.current = {
      boxShadow: node.style.boxShadow,
      position: node.style.position,
      zIndex: node.style.zIndex,
    };
    node.style.boxShadow = '0 0 0 4px rgba(196, 30, 58, 0.36), 0 8px 28px rgba(27, 42, 74, 0.16)';
    if (getComputedStyle(node).position === 'static') node.style.position = 'relative';
    node.style.zIndex = '1295';
    node.setAttribute('data-onboarding-active', 'true');
    highlightedNode.current = node;
    node.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' });

    const updatePosition = () => {
      const rect = node.getBoundingClientRect();
      const next = {
        top: rect.top,
        right: rect.right,
        bottom: rect.bottom,
        left: rect.left,
        width: rect.width,
        height: rect.height,
      };
      setTargetRect(next);
      setPlacement(getPopoverPlacement(next));
    };

    const frame = window.requestAnimationFrame(updatePosition);
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);

    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
      node.style.boxShadow = previousStyle.current.boxShadow;
      node.style.position = previousStyle.current.position;
      node.style.zIndex = previousStyle.current.zIndex;
      node.removeAttribute('data-onboarding-active');
      highlightedNode.current = null;
    };
  }, [activeStep?.targetId, open]);

  useEffect(() => () => {
    if (highlightedNode.current) {
      highlightedNode.current.style.boxShadow = previousStyle.current.boxShadow;
      highlightedNode.current.style.position = previousStyle.current.position;
      highlightedNode.current.style.zIndex = previousStyle.current.zIndex;
      highlightedNode.current.removeAttribute('data-onboarding-active');
    }
  }, []);

  if (!open || !activeStep || steps.length === 0) return null;

  const isLastStep = stepIndex === steps.length - 1;
  const isAnchored = placement.placement !== 'center' && targetRect !== null;
  const closeGuide = () => setOpen(false);
  const skipGuide = () => {
    markCompleted(key);
    setOpen(false);
  };

  const paperPosition = isAnchored
    ? {
        top: placement.top,
        left: placement.left,
        width: `min(${POPOVER_WIDTH}px, calc(100vw - ${VIEWPORT_MARGIN * 2}px))`,
        transform: placement.placement === 'above' ? 'translateY(-100%)' : 'none',
      }
    : {
        top: '50%',
        left: '50%',
        width: `min(${POPOVER_WIDTH}px, calc(100vw - ${VIEWPORT_MARGIN * 2}px))`,
        transform: 'translate(-50%, -50%)',
      };

  return (
    <>
      <SpotlightBackdrop target={isAnchored ? targetRect : null} onClose={closeGuide} />
      <Dialog
        open={open}
        onClose={closeGuide}
        hideBackdrop
        fullWidth
        maxWidth={false}
        aria-labelledby={`${scope}-onboarding-title`}
        aria-describedby={`${scope}-onboarding-description`}
        sx={{
          zIndex: 1300,
          '& .MuiDialog-container': { display: 'block' },
          '& .MuiDialog-paper': {
            position: 'fixed',
            m: 0,
            maxWidth: 'none',
            maxHeight: 'calc(100vh - 32px)',
            overflow: 'auto',
            borderRadius: 2,
            boxShadow: '0 20px 55px rgba(15, 23, 42, 0.24)',
            '&::after': isAnchored ? {
              content: '""',
              position: 'absolute',
              width: 16,
              height: 16,
              bgcolor: '#fff',
              transform: 'rotate(45deg)',
              ...(placement.placement === 'below' ? { top: -7 } : { bottom: -7 }),
              left: `clamp(24px, ${targetRect ? targetRect.left + targetRect.width / 2 - placement.left : 120}px, calc(100% - 24px))`,
            } : undefined,
          },
        }}
        slotProps={{ paper: { sx: paperPosition } }}
      >
        <DialogTitle
          id={`${scope}-onboarding-title`}
          sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.25, pb: 1.25, pr: 6, position: 'relative' }}
        >
          <Box
            sx={{
              width: 32,
              height: 32,
              flexShrink: 0,
              display: 'grid',
              placeItems: 'center',
              borderRadius: '50%',
              bgcolor: '#FFF1F3',
              color: '#C41E3A',
            }}
          >
            <HelpOutlineRoundedIcon fontSize="small" />
          </Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography component="div" sx={{ fontWeight: 900, color: '#172033', lineHeight: 1.25 }}>
              {title}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Hướng dẫn nhanh · Bước {stepIndex + 1}/{steps.length}
            </Typography>
          </Box>
          <IconButton
            aria-label="Đóng hướng dẫn"
            onClick={closeGuide}
            size="small"
            sx={{ position: 'absolute', top: 12, right: 12, color: '#667085' }}
          >
            <CloseRoundedIcon fontSize="small" />
          </IconButton>
        </DialogTitle>

        <Box sx={{ px: 3 }} aria-label={`Tiến trình bước ${stepIndex + 1} trên ${steps.length}`}>
          <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
            {steps.map((step, index) => (
              <Box
                key={step.id}
                aria-hidden="true"
                sx={{
                  height: 5,
                  flex: 1,
                  borderRadius: 999,
                  bgcolor: index <= stepIndex ? '#C41E3A' : '#F4D7DC',
                  transition: 'background-color 180ms ease',
                }}
              />
            ))}
          </Stack>
        </Box>

        <DialogContent id={`${scope}-onboarding-description`} sx={{ pt: 2.25 }}>
          {stepIndex === 0 && (
            <Typography sx={{ mb: 2, color: '#5B6472', lineHeight: 1.55 }}>
              {intro}
            </Typography>
          )}
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start' }}>
            <Box
              sx={{
                flexShrink: 0,
                width: 34,
                height: 34,
                display: 'grid',
                placeItems: 'center',
                borderRadius: 1.5,
                bgcolor: '#C41E3A',
                color: 'common.white',
                fontWeight: 900,
              }}
            >
              {stepIndex + 1}
            </Box>
            <Box>
              <Typography variant="h6" sx={{ color: '#172033', fontWeight: 900, mb: 0.75, lineHeight: 1.3 }}>
                {activeStep.title}
              </Typography>
              <Typography sx={{ color: '#5B6472', lineHeight: 1.55 }}>
                {activeStep.description}
              </Typography>
            </Box>
          </Stack>
        </DialogContent>

        <Divider />
        <Box sx={{ px: 3, py: 1.75, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <Button
            onClick={skipGuide}
            variant="text"
            size="small"
            sx={{ mr: 'auto', color: '#667085', textTransform: 'none', px: 0, '&:hover': { bgcolor: 'transparent', color: '#344054', textDecoration: 'underline' } }}
          >
            Bỏ qua hướng dẫn
          </Button>
          {stepIndex > 0 && (
            <Button
              onClick={() => setStepIndex((current) => Math.max(0, current - 1))}
              variant="outlined"
              startIcon={<ArrowBackRoundedIcon />}
              sx={{ borderColor: '#CBD2DC', color: '#475467', '&:hover': { borderColor: '#98A2B3', bgcolor: '#F8FAFC' } }}
            >
              Quay lại
            </Button>
          )}
          <Button
            variant="contained"
            onClick={() => (isLastStep ? closeGuide() : setStepIndex((current) => current + 1))}
            endIcon={isLastStep ? <CheckRoundedIcon /> : <ArrowForwardRoundedIcon />}
            sx={{ bgcolor: '#C41E3A', '&:hover': { bgcolor: '#A71931' } }}
          >
            {isLastStep ? 'Hoàn tất' : 'Tiếp theo'}
          </Button>
        </Box>
      </Dialog>
    </>
  );
}
