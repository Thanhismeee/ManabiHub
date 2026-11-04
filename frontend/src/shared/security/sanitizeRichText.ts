import DOMPurify from 'dompurify';

const RICH_TEXT_TAGS = [
  'p',
  'br',
  'strong',
  'b',
  'em',
  'i',
  'u',
  's',
  'ul',
  'ol',
  'li',
  'blockquote',
  'code',
  'pre',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'a',
  'span',
];

const RICH_TEXT_ATTRIBUTES = ['href', 'rel', 'class', 'data-list'];

/**
 * Canonical policy for teacher-authored HTML.
 *
 * Editors sanitize both inbound stored content and outbound Quill HTML. Public
 * readers call the same function immediately before dangerouslySetInnerHTML.
 */
export function sanitizeRichText(value: string | null | undefined) {
  return DOMPurify.sanitize(value ?? '', {
    ALLOWED_ATTR: RICH_TEXT_ATTRIBUTES,
    ALLOWED_TAGS: RICH_TEXT_TAGS,
    FORBID_ATTR: ['id', 'name', 'style', 'target'],
    FORBID_TAGS: ['embed', 'form', 'iframe', 'object', 'script', 'style'],
  });
}
