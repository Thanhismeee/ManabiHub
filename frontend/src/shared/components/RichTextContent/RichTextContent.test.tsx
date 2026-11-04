import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { RichTextContent } from './RichTextContent';

describe('RichTextContent', () => {
  it('renders stored HTML as formatted content instead of visible tags', () => {
    const { container } = render(
      <RichTextContent value={'<p>Sau khóa học, học viên đọc được Kanji.</p>'} />,
    );

    expect(screen.getByText('Sau khóa học, học viên đọc được Kanji.')).toBeInTheDocument();
    expect(container).not.toHaveTextContent('<p>');
  });

  it('keeps Quill lists but removes executable markup', () => {
    const { container } = render(
      <RichTextContent
        value={'<ol><li data-list="bullet"><span class="ql-ui"></span>Mục tiêu N5</li></ol><script>alert(1)</script>'}
      />,
    );

    expect(screen.getByText('Mục tiêu N5')).toBeInTheDocument();
    expect(container.querySelector('li')).toHaveAttribute('data-list', 'bullet');
    expect(container.querySelector('script')).not.toBeInTheDocument();
  });
});
