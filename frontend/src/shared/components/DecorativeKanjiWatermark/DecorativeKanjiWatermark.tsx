import { Typography } from '@mui/material';

interface DecorativeKanjiWatermarkProps {
  text: string;
}

/** Decorative background text shared by student-facing pages. */
export function DecorativeKanjiWatermark({ text }: DecorativeKanjiWatermarkProps) {
  return (
    <Typography
      aria-hidden="true"
      data-testid="decorative-kanji-watermark"
      sx={{
        position: 'absolute',
        top: -40,
        right: -20,
        display: { xs: 'none', md: 'block' },
        fontFamily: '"Noto Serif JP", "Yu Mincho", "Hiragino Mincho ProN", serif',
        fontSize: { md: '12rem', lg: '15rem' },
        fontWeight: 900,
        lineHeight: 1,
        writingMode: 'vertical-rl',
        color: 'rgba(0, 0, 0, 0.025)',
        userSelect: 'none',
        pointerEvents: 'none',
        zIndex: 0,
      }}
    >
      {text}
    </Typography>
  );
}
