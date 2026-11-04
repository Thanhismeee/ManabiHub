import { sanitizeRichText } from '../../security/sanitizeRichText';
import './RichTextContent.css';

interface RichTextContentProps {
  value?: string | null;
  className?: string;
}

/**
 * Renders teacher-authored rich text using the application's bounded HTML policy.
 * Quill-specific list markup is styled here so stored editor content is readable
 * outside the editor without exposing raw tags.
 */
export function RichTextContent({ value, className = '' }: RichTextContentProps) {
  const sanitizedHtml = sanitizeRichText(value);

  if (!sanitizedHtml.trim()) return null;

  return (
    <div
      className={`rich-text-content ${className}`.trim()}
      dangerouslySetInnerHTML={{ __html: sanitizedHtml }}
    />
  );
}
