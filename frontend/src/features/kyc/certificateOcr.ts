import { createWorker, OEM, PSM, type LoggerMessage } from 'tesseract.js';

export interface JlptOcrResult {
  /** Sanitized evidence text sent to the backend, not the noisy OCR transcript. */
  rawText: string;
  holderName: string;
  dateOfBirth: string;
  level: string;
  certificateCode: string;
}

type OcrInput = File | HTMLCanvasElement;

interface OcrPass {
  input: OcrInput;
  pageSegMode: PSM;
  characterWhitelist?: string;
  fallback?: boolean;
}

/**
 * OCR the certificate in focused passes.  JLPT certificates contain a security
 * background plus Japanese / English boilerplate.  We crop individual zones so
 * that Tesseract sees less noise and returns better field values.
 *
 * Language note – we load **jpn+eng** so that Tesseract can properly segment
 * kanji labels (氏名, 生年月日, 受験地) and not misread them as random ASCII.
 * The trained-data files are fetched lazily on first use (~15 MB total).
 */
export async function recognizeJlptCertificate(
  file: File,
  onProgress?: (progress: number) => void,
): Promise<JlptOcrResult> {
  const passes = await createOcrPasses(file);
  let activePass = 0;
  const worker = await createWorker('jpn+eng', OEM.LSTM_ONLY, {
    logger: (message: LoggerMessage) => {
      if (message.status === 'recognizing text') {
        const overallProgress = ((activePass + message.progress) / passes.length) * 100;
        onProgress?.(Math.min(99, Math.round(overallProgress)));
      }
    },
  });

  try {
    const transcripts: string[] = [];
    for (const [index, pass] of passes.entries()) {
      if (pass.fallback && hasAllRequiredFields(parseJlptOcrText(transcripts.join('\n')))) {
        break;
      }

      activePass = index;
      await worker.setParameters({
        tessedit_pageseg_mode: pass.pageSegMode,
        preserve_interword_spaces: '1',
        // Clear the previous focused pass whitelist before reading normal text.
        tessedit_char_whitelist: pass.characterWhitelist ?? '',
      });
      const result = await worker.recognize(pass.input);
      if (result.data.text.trim()) transcripts.push(result.data.text);
    }
    onProgress?.(100);
    return parseJlptOcrText(transcripts.join('\n'));
  } finally {
    await worker.terminate();
  }
}

/** Parse only the fields that the certificate form and backend can verify. */
export function parseJlptOcrText(rawText: string): JlptOcrResult {
  const text = normalizeOcrText(rawText);
  const lines = text.split('\n').map((line) => line.trim()).filter(Boolean);
  const holderName = findCertificateName(lines);
  const rawDateOfBirth = findFirstMatch(text, [
    /(?:date\s*of\s*birth|birth\s*date|dob|生年月日)\s*[(:：y/m/d)\s-]*\s*(\d{4}[./-]\d{1,2}[./-]\d{1,2})/i,
    /(?:date\s*of\s*birth|birth\s*date|dob|生年月日)\s*[(:：y/m/d)\s-]*\s*(\d{1,2}[./-]\d{1,2}[./-]\d{4})/i,
    /\b(\d{4}[./-]\d{1,2}[./-]\d{1,2})\b/,
    /\b(\d{1,2}[./-]\d{1,2}[./-]\d{4})\b/,
  ]);
  const dateOfBirth = normalizeDate(rawDateOfBirth);
  const certificateCode = findCertificateCode(text);
  const rawLevel = findFirstMatch(text, [
    /(?:Level|級|レベル)\s*[:：-]?\s*([NＮ]\s*[-.:]?\s*[1-5１-５])/i,
    /([NＮ]\s*[-.:]?\s*[1-5１-５])\s*(?:Level|級|レベル)/i,
    /([NＮ]\s*[-.:]?\s*[1-5１-５])\s*[A-Z]\s*[A-Z0-9\s]{5,}\s*[A-Z]\b/i,
    /(?:^|[^A-Za-z0-9])([NＮ]\s*[-.:]?\s*[1-5１-５])(?:[^A-Za-z0-9]|$)/i,
  ]);
  const level = rawLevel
    .toUpperCase()
    .replace(/[\s\-.:]+/g, '')
    .replace(/Ｎ/g, 'N')
    .replace(/[１-５]/g, (match) => String.fromCharCode(match.charCodeAt(0) - 0xFEE0));

  const evidenceLines = [
    holderName && `Name: ${holderName}`,
    dateOfBirth && `Date of Birth: ${dateOfBirth}`,
    level && `Level: ${level}`,
    certificateCode && `Certificate No: ${certificateCode}`,
  ].filter((line): line is string => Boolean(line));

  return {
    rawText: evidenceLines.join('\n'),
    holderName,
    dateOfBirth,
    level,
    certificateCode,
  };
}

function normalizeOcrText(rawText: string) {
  return rawText
    .normalize('NFKC')
    .replace(/\r/g, '')
    .replace(/[‐‑‒–—−]/g, '-')
    .replace(/[ \t]+/g, ' ')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .join('\n');
}

/**
 * Find the holder's name on the certificate.
 *
 * Strategy:
 * 1. Look for a label line (Name, 氏名, etc.) and take the next line.
 * 2. Look for an inline "Name: VALUE" pattern.
 * 3. Fall back to the first line that looks like an all-caps person name.
 */
function findCertificateName(lines: string[]) {
  // Strategy 1: label on its own line, value on the next line
  const labelIndex = lines.findIndex((line) =>
    /^(?:name|full\s*name|holder|氏名|名前)\s*[:：]?\s*$/i.test(line),
  );
  if (labelIndex >= 0) {
    const labeled = cleanName(lines[labelIndex + 1] ?? '');
    if (isLikelyPersonName(labeled)) return labeled;
  }

  // Strategy 2: inline "Name: SOME VALUE" or "氏名 SOME VALUE"
  for (const line of lines) {
    const inline = line.match(/^(?:name|full\s*name|holder|氏名)\s*[:：-]?\s+(.+)$/i);
    const labeled = cleanName(inline?.[1] ?? '');
    if (isLikelyPersonName(labeled)) return labeled;
  }

  // Strategy 3: first line that looks like a person name (all-caps, multi-word)
  for (const line of lines) {
    const candidate = cleanName(line);
    if (isLikelyPersonName(candidate)) return candidate;
  }
  return '';
}

/**
 * Heuristic check: does `value` look like a person name printed on a JLPT
 * certificate?
 *
 * Filters:
 * - Must be 5–50 characters long.
 * - Must not contain digits.
 * - Must not contain known boilerplate words.
 * - Must have 2–5 space-separated words, each at least 2 characters.
 * - Each word must be alphabetic (including Vietnamese diacritics).
 */
function isLikelyPersonName(value: string) {
  if (!value || value.length < 5 || value.length > 50 || /\d/.test(value)) return false;
  const upper = value.toUpperCase();
  const blocked = [
    'CERTIFICATE', 'JAPANESE', 'LANGUAGE', 'PROFICIENCY', 'DATE OF BIRTH',
    'TEST SITE', 'VIETNAM', 'THIS IS TO CERTIFY', 'EDUCATIONAL', 'EXCHANGES',
    'SERVICES', 'LEVEL', 'NAME', 'ROSSA', 'JAPAN FOUNDATION',
    'JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE',
    'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER',
  ];
  if (blocked.some((word) => upper.includes(word))) return false;
  const words = value.split(' ').filter(Boolean);
  // Each word must be at least 2 chars to block garbage like "HH y m d"
  return words.length >= 2 && words.length <= 5 && words.every((word) => word.length >= 2 && /^[A-Za-zÀ-ỹ''-]+$/.test(word));
}

function findCertificateCode(text: string) {
  const compactLines = text
    .split('\n')
    .map((line) => line.toUpperCase().replace(/[^A-Z0-9-]/g, ''))
    .filter(Boolean);

  // Prefer the registration number at the lower-right corner. It is the
  // stable number used to identify a JLPT sitting, e.g. 25B2080102-33745.
  for (const line of compactLines) {
    const match = line.match(/([0-9OQIL]{2})([A-Z])([0-9OQILSBZ]{6,})-([0-9OQILSBZ]{3,})/i);
    if (match) {
      return `${normalizeOcrDigits(match[1])}${match[2].toUpperCase()}${normalizeOcrDigits(match[3])}-${normalizeOcrDigits(match[4])}`;
    }
  }

  // Fall back to the serial at the lower-left corner, e.g. N3A568591A.
  // Focused OCR commonly inserts spaces or mistakes 0/1/2/5/8 for letters.
  for (const line of compactLines) {
    const match = line.match(/N([1-5])([A-Z])([0-9OQILSBZ]{5,})([A-Z])/i);
    if (match) {
      return `N${match[1]}${match[2].toUpperCase()}${normalizeOcrDigits(match[3])}${match[4].toUpperCase()}`;
    }
  }

  return '';
}

function normalizeOcrDigits(value: string) {
  return value
    .toUpperCase()
    .replace(/[OQ]/g, '0')
    .replace(/[IL]/g, '1')
    .replace(/Z/g, '2')
    .replace(/S/g, '5')
    .replace(/B/g, '8');
}

function hasAllRequiredFields(result: JlptOcrResult) {
  return Boolean(
    result.holderName
    && result.dateOfBirth
    && result.level
    && result.certificateCode,
  );
}

function findFirstMatch(text: string, patterns: RegExp[]) {
  for (const pattern of patterns) {
    const match = text.match(pattern);
    if (match?.[1]) return match[1].trim();
  }
  return '';
}

function cleanName(value: string) {
  return value
    .replace(/^(?:RA|R\s*A|A|ZZ[YV])\s+/i, '')
    // Strip trailing field labels that sometimes bleed into the name line.
    .replace(/\b(?:date|dob|birth|level|registration|certificate)\b.*$/i, '')
    // Strip any Japanese / CJK characters that leaked in from label proximity.
    .replace(/[\u3000-\u9FFF\uF900-\uFAFF]/g, ' ')
    .replace(/[^A-Za-zÀ-ỹ'' -]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function normalizeDate(value: string) {
  if (!value) return '';
  const parts = value.split(/[./-]/).map((part) => Number(part));
  if (parts.length !== 3 || parts.some((part) => !Number.isInteger(part))) return '';
  const [first, second, third] = parts;
  const year = first > 999 ? first : third;
  const month = second;
  const day = first > 999 ? third : first;
  if (year < 1900 || year > new Date().getFullYear() + 1 || month < 1 || month > 12 || day < 1 || day > 31) return '';
  return `${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}`;
}

/**
 * Create focused crop zones for the major certificate areas.
 *
 * A JLPT certificate has a stable layout:
 *   - Top centre: level badge (N1–N5)
 *   - Upper-middle: Name row, DOB row (values on right side)
 *   - Bottom strip: two serial/registration codes
 *
 * We crop each zone independently so Tesseract processes smaller, cleaner
 * images rather than the full noisy page.
 */
async function createOcrPasses(file: File): Promise<OcrPass[]> {
  if (typeof document === 'undefined' || typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') {
    return [{ input: file, pageSegMode: PSM.SPARSE_TEXT }];
  }

  const url = URL.createObjectURL(file);
  try {
    const image = await loadImage(url);
    const width = image.naturalWidth || image.width;
    const height = image.naturalHeight || image.height;
    if (!width || !height) return [{ input: file, pageSegMode: PSM.SPARSE_TEXT }];

    return [
      // The large N1-N5 badge is above the old 22%-high crop and therefore
      // needs its own pass. SINGLE_WORD prevents the guilloche background and
      // Japanese heading from competing with the two-character level. RAW_LINE
      // works better here because the large serif glyphs have a wide gap.
      {
        input: cropImage(image, width, height, 0.40, 0.06, 0.20, 0.09, { contrast: 1.6 }),
        pageSegMode: PSM.RAW_LINE,
        characterWhitelist: 'N12345',
      },
      // Name + date of birth block.
      {
        input: cropImage(image, width, height, 0.08, 0.22, 0.72, 0.25, { contrast: 1.5 }),
        pageSegMode: PSM.SPARSE_TEXT,
      },
      // Registration number at the lower-right corner.
      {
        input: cropImage(image, width, height, 0.71875, 0.9335, 0.229, 0.04, { contrast: 1.7 }),
        pageSegMode: PSM.SINGLE_LINE,
        characterWhitelist: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-',
      },
      // Serial number at the lower-left corner, used if the registration
      // number cannot be read.
      {
        input: cropImage(image, width, height, 0.0415, 0.9335, 0.167, 0.04, { contrast: 1.7 }),
        pageSegMode: PSM.SINGLE_LINE,
        characterWhitelist: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-',
      },
      // Full page only when one of the four required fields is still missing.
      { input: file, pageSegMode: PSM.SPARSE_TEXT, fallback: true },
    ];
  } catch {
    return [{ input: file, pageSegMode: PSM.SPARSE_TEXT }];
  } finally {
    URL.revokeObjectURL(url);
  }
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('certificate image could not be loaded'));
    image.src = url;
  });
}

interface CropOptions {
  contrast?: number;
  threshold?: number;
}

function cropImage(
  image: HTMLImageElement,
  sourceWidth: number,
  sourceHeight: number,
  leftRatio: number,
  topRatio: number,
  widthRatio: number,
  heightRatio: number,
  options?: CropOptions,
) {
  const sourceLeft = Math.round(sourceWidth * leftRatio);
  const sourceTop = Math.round(sourceHeight * topRatio);
  const cropWidth = Math.round(sourceWidth * widthRatio);
  const cropHeight = Math.round(sourceHeight * heightRatio);
  const scale = Math.min(2, 1800 / cropWidth);
  const contrast = options?.contrast ?? 1.3;
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(cropWidth * scale));
  canvas.height = Math.max(1, Math.round(cropHeight * scale));
  const context = canvas.getContext('2d');
  if (!context) return canvas;
  context.fillStyle = '#fff';
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.filter = `grayscale(1) contrast(${contrast})`;
  context.drawImage(image, sourceLeft, sourceTop, cropWidth, cropHeight, 0, 0, canvas.width, canvas.height);
  context.filter = 'none';
  if (options?.threshold !== undefined) {
    applyBinaryThreshold(context, canvas.width, canvas.height, options.threshold);
  }
  return canvas;
}

function applyBinaryThreshold(
  context: CanvasRenderingContext2D,
  width: number,
  height: number,
  threshold: number,
) {
  const imageData = context.getImageData(0, 0, width, height);
  for (let index = 0; index < imageData.data.length; index += 4) {
    const luminance = (
      imageData.data[index] * 0.299
      + imageData.data[index + 1] * 0.587
      + imageData.data[index + 2] * 0.114
    );
    const value = luminance < threshold ? 0 : 255;
    imageData.data[index] = value;
    imageData.data[index + 1] = value;
    imageData.data[index + 2] = value;
  }
  context.putImageData(imageData, 0, 0);
}
