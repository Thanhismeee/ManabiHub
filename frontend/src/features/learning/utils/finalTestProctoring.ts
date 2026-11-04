export type FinalTestViolationType =
  | 'CLIPBOARD'
  | 'DRAG_DROP'
  | 'CONTEXT_MENU'
  | 'TAB_SWITCH'
  | 'WINDOW_BLUR'
  | 'SCREENSHOT_SHORTCUT'
  | 'PRINT_ATTEMPT';

export const FINAL_TEST_MAX_VIOLATIONS = 3;

export function isClipboardShortcut(event: {
  key: string;
  ctrlKey?: boolean;
  metaKey?: boolean;
  shiftKey?: boolean;
}) {
  const key = event.key.toLowerCase();
  const modifier = Boolean(event.ctrlKey || event.metaKey);
  return Boolean(
    (modifier && (key === 'c' || key === 'x' || key === 'v'))
      || (event.shiftKey && key === 'insert'),
  );
}

export function isScreenshotShortcut(event: {
  key: string;
  code?: string;
  ctrlKey?: boolean;
  metaKey?: boolean;
  shiftKey?: boolean;
}) {
  const key = event.key.toLowerCase();
  const code = (event.code ?? '').toLowerCase();
  return Boolean(
    key === 'printscreen'
      || code === 'printscreen'
      || (event.metaKey && event.shiftKey && ['3', '4', '5', 's'].includes(key))
      || (event.ctrlKey && event.shiftKey && (key === 's' || key === 'printscreen')),
  );
}

export function violationLabel(type: FinalTestViolationType) {
  switch (type) {
    case 'CLIPBOARD': return 'Thao tác copy/cut/paste';
    case 'DRAG_DROP': return 'Kéo hoặc thả nội dung';
    case 'CONTEXT_MENU': return 'Mở menu chuột phải';
    case 'TAB_SWITCH': return 'Chuyển tab hoặc ẩn trang thi';
    case 'WINDOW_BLUR': return 'Rời khỏi cửa sổ thi';
    case 'SCREENSHOT_SHORTCUT': return 'Phím chụp màn hình';
    case 'PRINT_ATTEMPT': return 'Thao tác in màn hình';
  }
}
