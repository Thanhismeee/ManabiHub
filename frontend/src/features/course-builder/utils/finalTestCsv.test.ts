import { describe, expect, it } from 'vitest';
import {
  createFinalTestCsvTemplate,
  FinalTestCsvError,
  parseFinalTestCsv,
} from './finalTestCsv';

describe('finalTestCsv', () => {
  it('round-trips the UTF-8 template without losing Japanese text', () => {
    const result = parseFinalTestCsv(createFinalTestCsvTemplate(), []);

    expect(result.questions).toHaveLength(1);
    expect(result.questions[0].content).toContain('"Điện thoại"');
    expect(result.questions[0].choices[0].content).toBe('電話');
    expect(result.questions[0].choices[0].isCorrect).toBe(true);
  });

  it('supports quoted commas and escaped quotes', () => {
    const csv = [
      '"Nội dung câu hỏi","Lựa chọn 1","Lựa chọn 2","Lựa chọn 3","Lựa chọn 4","Đáp án đúng (1-4)","Giải thích"',
      '"Câu hỏi, có ""trích dẫn""","A","B","C","D","2","Vì B đúng"',
    ].join('\r\n');

    const result = parseFinalTestCsv(csv, []);

    expect(result.questions[0].content).toBe('Câu hỏi, có "trích dẫn"');
    expect(result.questions[0].choices[1].isCorrect).toBe(true);
  });

  it('deduplicates against existing questions and rows in the same file', () => {
    const csv = [
      'Câu hỏi,A,B,C,D,Đáp án,Giải thích',
      ' Câu   trùng ,A,B,C,D,1,',
      'Câu trùng,A,B,C,D,1,',
    ].join('\n');

    const result = parseFinalTestCsv(csv, [
      {
        content: 'CÂU TRÙNG',
        explanation: '',
        choices: [],
      },
    ]);

    expect(result.questions).toHaveLength(0);
    expect(result.duplicateCount).toBe(2);
  });

  it.each([
    ['"Câu hỏi,A,B,C,D,Đáp án,Giải thích', 'chưa đóng'],
    ['Câu hỏi,A,B,C,D,Đáp án,Giải thích\nCâu 1,A,B,C,D,5,', 'số từ 1 đến 4'],
    ['Câu hỏi,A,B,C,D,Đáp án,Giải thích\nCâu 1,A,B,C,,1,', 'đủ 4 lựa chọn'],
  ])('rejects malformed or invalid input', (csv, expectedMessage) => {
    expect(() => parseFinalTestCsv(csv, [])).toThrowError(
      expect.objectContaining<Partial<FinalTestCsvError>>({
        message: expect.stringContaining(expectedMessage),
      }),
    );
  });
});
