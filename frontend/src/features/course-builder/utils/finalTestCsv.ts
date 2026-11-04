import type { FinalTestQuestion } from '../services/finalTestService';

export const MAX_FINAL_TEST_CSV_BYTES = 1_000_000;

const MAX_CSV_ROWS = 501;
const MAX_CSV_COLUMNS = 7;
const MAX_CSV_CELL_LENGTH = 2_000;

const TEMPLATE_ROWS = [
  [
    'Nội dung câu hỏi',
    'Lựa chọn 1',
    'Lựa chọn 2',
    'Lựa chọn 3',
    'Lựa chọn 4',
    'Đáp án đúng (1-4)',
    'Giải thích đáp án',
  ],
  [
    'Kanji của từ "Điện thoại" là gì?',
    '電話',
    '電車',
    '電気',
    '電話機',
    '1',
    'Điện thoại là 電話 (Denwa)',
  ],
];

export class FinalTestCsvError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'FinalTestCsvError';
  }
}

export interface FinalTestCsvImportResult {
  duplicateCount: number;
  questions: FinalTestQuestion[];
}

function escapeCsvCell(value: string) {
  return `"${value.replaceAll('"', '""')}"`;
}

export function createFinalTestCsvTemplate() {
  return `\uFEFF${TEMPLATE_ROWS.map((row) => row.map(escapeCsvCell).join(',')).join('\r\n')}\r\n`;
}

function normalizeQuestion(value: string) {
  return value.normalize('NFKC').trim().replace(/\s+/g, ' ').toLocaleLowerCase('vi');
}

function normalizeHeader(value: string) {
  return value
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .trim()
    .toLocaleLowerCase('vi');
}

function parseCsvRows(source: string) {
  if (source.includes('\0')) {
    throw new FinalTestCsvError('File CSV chứa ký tự không hợp lệ.');
  }

  const input = source.startsWith('\uFEFF') ? source.slice(1) : source;
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let inQuotes = false;
  let quoteClosed = false;

  const pushCell = () => {
    if (cell.length > MAX_CSV_CELL_LENGTH) {
      throw new FinalTestCsvError(
        `Một ô vượt quá giới hạn ${MAX_CSV_CELL_LENGTH.toLocaleString('vi-VN')} ký tự.`,
      );
    }
    row.push(cell);
    cell = '';
    quoteClosed = false;
  };

  const pushRow = () => {
    pushCell();
    if (row.length > MAX_CSV_COLUMNS) {
      throw new FinalTestCsvError(`File CSV chỉ được có tối đa ${MAX_CSV_COLUMNS} cột.`);
    }
    rows.push(row);
    row = [];
    if (rows.length > MAX_CSV_ROWS) {
      throw new FinalTestCsvError(
        `File CSV chỉ được có tối đa ${MAX_CSV_ROWS - 1} câu hỏi mỗi lần import.`,
      );
    }
  };

  for (let index = 0; index < input.length; index += 1) {
    const character = input[index];

    if (inQuotes) {
      if (character === '"' && input[index + 1] === '"') {
        cell += '"';
        index += 1;
      } else if (character === '"') {
        inQuotes = false;
        quoteClosed = true;
      } else {
        cell += character;
      }
      continue;
    }

    if (quoteClosed && character !== ',' && character !== '\r' && character !== '\n') {
      throw new FinalTestCsvError('Dấu ngoặc kép trong file CSV không đúng định dạng.');
    }

    if (character === '"') {
      if (cell.length > 0) {
        throw new FinalTestCsvError('Dấu ngoặc kép trong file CSV không đúng định dạng.');
      }
      inQuotes = true;
    } else if (character === ',') {
      pushCell();
    } else if (character === '\n') {
      pushRow();
    } else if (character === '\r') {
      if (input[index + 1] === '\n') {
        index += 1;
      }
      pushRow();
    } else {
      cell += character;
    }
  }

  if (inQuotes) {
    throw new FinalTestCsvError('File CSV có ô chưa đóng dấu ngoặc kép.');
  }

  if (cell.length > 0 || row.length > 0) {
    pushRow();
  }

  return rows.filter((currentRow) => currentRow.some((value) => value.trim().length > 0));
}

export function parseFinalTestCsv(
  source: string,
  existingQuestions: FinalTestQuestion[],
): FinalTestCsvImportResult {
  if (new TextEncoder().encode(source).byteLength > MAX_FINAL_TEST_CSV_BYTES) {
    throw new FinalTestCsvError('File CSV vượt quá giới hạn 1 MB.');
  }

  const rows = parseCsvRows(source);
  const header = rows[0];
  if (!header || !normalizeHeader(header[0] ?? '').includes('cau hoi')) {
    throw new FinalTestCsvError(
      "File CSV không đúng định dạng. Cột đầu tiên phải chứa từ 'Câu hỏi'.",
    );
  }

  const existingContents = new Set(existingQuestions.map((question) => normalizeQuestion(question.content)));
  const questions: FinalTestQuestion[] = [];
  let duplicateCount = 0;

  rows.slice(1).forEach((currentRow, rowIndex) => {
    const displayRow = rowIndex + 2;
    const content = (currentRow[0] ?? '').trim();
    if (!content) {
      return;
    }

    const choices = currentRow.slice(1, 5).map((value) => (value ?? '').trim());
    if (choices.length !== 4 || choices.some((choice) => !choice)) {
      throw new FinalTestCsvError(`Dòng ${displayRow} phải có đủ 4 lựa chọn.`);
    }

    const correctChoice = Number((currentRow[5] ?? '').trim());
    if (!Number.isInteger(correctChoice) || correctChoice < 1 || correctChoice > 4) {
      throw new FinalTestCsvError(`Đáp án đúng ở dòng ${displayRow} phải là số từ 1 đến 4.`);
    }

    const normalizedContent = normalizeQuestion(content);
    if (existingContents.has(normalizedContent)) {
      duplicateCount += 1;
      return;
    }
    existingContents.add(normalizedContent);

    questions.push({
      content,
      explanation: (currentRow[6] ?? '').trim(),
      choices: choices.map((choice, choiceIndex) => ({
        content: choice,
        isCorrect: choiceIndex + 1 === correctChoice,
      })),
    });
  });

  return { duplicateCount, questions };
}
