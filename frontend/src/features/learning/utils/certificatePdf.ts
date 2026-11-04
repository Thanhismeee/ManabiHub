import type { LearningCertificate } from '../types';

const CANVAS_WIDTH = 3508;
const CANVAS_HEIGHT = 2480;
const PDF_WIDTH_MM = 297;
const PDF_HEIGHT_MM = 210;
const MINIMUM_VALID_CERTIFICATE_TIME = Date.UTC(2000, 0, 1);

const COLORS = {
  ivory: '#fffdf7',
  navy: '#101b33',
  red: '#c91f3d',
  redDark: '#8f1028',
  gold: '#c8a45a',
  goldLight: '#ead9ae',
  muted: '#5c6473',
  paleRed: '#f9e9ec',
};

type ApiTimestamp = string | number | null | undefined;

export function parseCertificateTimestamp(value: ApiTimestamp) {
  if (value === null || value === undefined || value === '') return null;

  let timestamp: number;
  if (typeof value === 'number') {
    timestamp = Math.abs(value) < 100_000_000_000 ? value * 1000 : value;
  } else {
    const normalized = value.trim();
    if (!normalized) return null;
    if (/^-?\d+(\.\d+)?$/.test(normalized)) {
      const numericValue = Number(normalized);
      timestamp = Math.abs(numericValue) < 100_000_000_000
        ? numericValue * 1000
        : numericValue;
    } else {
      timestamp = Date.parse(normalized);
    }
  }

  if (!Number.isFinite(timestamp) || timestamp < MINIMUM_VALID_CERTIFICATE_TIME) return null;
  const date = new Date(timestamp);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatCertificateDate(value: ApiTimestamp, fallbackValue?: ApiTimestamp) {
  const date = parseCertificateTimestamp(value) ?? parseCertificateTimestamp(fallbackValue);
  if (!date) return 'Vừa hoàn thành';
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
}

export async function downloadCertificatePdf(certificate: LearningCertificate) {
  const canvas = document.createElement('canvas');
  canvas.width = CANVAS_WIDTH;
  canvas.height = CANVAS_HEIGHT;

  const context = canvas.getContext('2d');
  if (!context) {
    throw new Error('Trình duyệt không hỗ trợ tạo chứng chỉ PDF.');
  }

  await renderCertificate(context, certificate);

  const { jsPDF } = await import('jspdf');
  const pdf = new jsPDF({
    orientation: 'landscape',
    unit: 'mm',
    format: 'a4',
    compress: true,
  });

  pdf.setProperties({
    title: `Chứng chỉ hoàn thành - ${certificate.courseTitle}`,
    subject: `Chứng chỉ hoàn thành khóa học của ${certificate.studentName}`,
    author: 'ManabiHub',
    creator: 'ManabiHub',
    keywords: `ManabiHub, chứng chỉ, ${certificate.certificateNumber}`,
  });
  pdf.addImage(
    canvas.toDataURL('image/png', 1),
    'PNG',
    0,
    0,
    PDF_WIDTH_MM,
    PDF_HEIGHT_MM,
    undefined,
    'FAST',
  );
  pdf.save(`${sanitizeFilename(certificate.certificateNumber)}.pdf`);
}

export async function renderCertificate(
  context: CanvasRenderingContext2D,
  certificate: LearningCertificate,
) {
  const completionDate = parseCertificateTimestamp(certificate.completedAt)
    ?? parseCertificateTimestamp(certificate.issuedAt)
    ?? new Date();
  const completionMoment = formatCompletionMoment(completionDate);
  const systemLogo = await loadSystemLogo();

  context.save();
  context.clearRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
  context.fillStyle = COLORS.ivory;
  context.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

  drawBackground(context);
  drawFrame(context);
  if (systemLogo) {
    drawSystemLogo(context, systemLogo, CANVAS_WIDTH / 2, 320);
  } else {
    drawBrandMark(context, CANVAS_WIDTH / 2, 330);
    drawLetterSpacedText(context, 'MANABIHUB', CANVAS_WIDTH / 2, 510, 15, {
      font: '700 42px Arial, sans-serif',
      color: COLORS.redDark,
    });
  }

  drawLetterSpacedText(
    context,
    'HỌC TẬP  •  TRƯỞNG THÀNH  •  KẾT NỐI',
    CANVAS_WIDTH / 2,
    550,
    5,
    { font: '500 24px Arial, sans-serif', color: COLORS.muted },
  );

  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.fillStyle = COLORS.navy;
  context.font = '700 98px Georgia, "Times New Roman", serif';
  context.fillText('CHỨNG CHỈ HOÀN THÀNH', CANVAS_WIDTH / 2, 760);

  drawOrnamentalDivider(context, 1020, 2488, 855);

  context.fillStyle = COLORS.muted;
  context.font = '400 34px Arial, sans-serif';
  context.fillText('ManabiHub trân trọng chứng nhận', CANVAS_WIDTH / 2, 955);

  const studentFontSize = fitFontSize(
    context,
    certificate.studentName,
    148,
    84,
    2780,
    '700',
    'Georgia, "Times New Roman", serif',
  );
  context.fillStyle = COLORS.redDark;
  context.font = `700 ${studentFontSize}px Georgia, "Times New Roman", serif`;
  context.fillText(certificate.studentName, CANVAS_WIDTH / 2, 1160);

  context.fillStyle = COLORS.muted;
  context.font = '400 34px Arial, sans-serif';
  context.fillText('đã hoàn thành xuất sắc khóa học', CANVAS_WIDTH / 2, 1315);

  const courseLines = wrapCenteredText(
    context,
    certificate.courseTitle,
    2740,
    70,
    48,
    '700',
    'Arial, sans-serif',
    2,
  );
  const courseStartY = courseLines.lines.length === 1 ? 1468 : 1432;
  context.fillStyle = COLORS.navy;
  context.font = `${courseLines.fontWeight} ${courseLines.fontSize}px ${courseLines.fontFamily}`;
  courseLines.lines.forEach((line, index) => {
    context.fillText(line, CANVAS_WIDTH / 2, courseStartY + index * 88);
  });

  drawCompletionBadge(context, CANVAS_WIDTH / 2, 1668 + (courseLines.lines.length - 1) * 55);
  drawCertificateFooter(context, completionMoment, certificate.certificateNumber);

  context.restore();
}

function loadSystemLogo() {
  return new Promise<HTMLImageElement | null>((resolve) => {
    const image = new Image();
    const timeout = window.setTimeout(() => resolve(null), 5000);

    image.onload = () => {
      window.clearTimeout(timeout);
      resolve(image);
    };
    image.onerror = () => {
      window.clearTimeout(timeout);
      resolve(null);
    };
    image.src = '/manabihub-header-logo.png';
  });
}

function drawSystemLogo(
  context: CanvasRenderingContext2D,
  logo: HTMLImageElement,
  centerX: number,
  centerY: number,
) {
  const width = 310;
  const height = width * (logo.naturalHeight / logo.naturalWidth);
  context.drawImage(logo, centerX - width / 2, centerY - height / 2, width, height);
}

function drawBackground(context: CanvasRenderingContext2D) {
  const glow = context.createRadialGradient(1754, 1110, 0, 1754, 1110, 1550);
  glow.addColorStop(0, '#ffffff');
  glow.addColorStop(1, '#fff9eb');
  context.fillStyle = glow;
  context.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

  context.save();
  context.globalAlpha = 0.06;
  context.strokeStyle = COLORS.red;
  context.lineWidth = 4;
  for (let y = 170; y < CANVAS_HEIGHT; y += 105) {
    context.beginPath();
    for (let x = 90; x < CANVAS_WIDTH; x += 150) {
      context.moveTo(x, y);
      context.arc(x + 37.5, y, 37.5, Math.PI, 0);
      context.arc(x + 112.5, y, 37.5, Math.PI, 0);
    }
    context.stroke();
  }
  context.restore();

  const leftWash = context.createLinearGradient(0, 0, 620, 620);
  leftWash.addColorStop(0, COLORS.paleRed);
  leftWash.addColorStop(1, 'rgba(249, 233, 236, 0)');
  context.fillStyle = leftWash;
  context.fillRect(0, 0, 760, 760);
}

function drawFrame(context: CanvasRenderingContext2D) {
  context.save();

  context.strokeStyle = COLORS.gold;
  context.lineWidth = 12;
  context.strokeRect(92, 92, CANVAS_WIDTH - 184, CANVAS_HEIGHT - 184);

  context.strokeStyle = COLORS.redDark;
  context.lineWidth = 4;
  context.strokeRect(120, 120, CANVAS_WIDTH - 240, CANVAS_HEIGHT - 240);

  context.strokeStyle = COLORS.goldLight;
  context.lineWidth = 2;
  context.strokeRect(142, 142, CANVAS_WIDTH - 284, CANVAS_HEIGHT - 284);

  drawCorner(context, 142, 142, 1, 1);
  drawCorner(context, CANVAS_WIDTH - 142, 142, -1, 1);
  drawCorner(context, 142, CANVAS_HEIGHT - 142, 1, -1);
  drawCorner(context, CANVAS_WIDTH - 142, CANVAS_HEIGHT - 142, -1, -1);
  context.restore();
}

function drawCorner(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  horizontalDirection: 1 | -1,
  verticalDirection: 1 | -1,
) {
  context.save();
  context.translate(x, y);
  context.scale(horizontalDirection, verticalDirection);
  context.strokeStyle = COLORS.redDark;
  context.fillStyle = COLORS.gold;
  context.lineWidth = 5;

  context.beginPath();
  context.moveTo(0, 112);
  context.lineTo(0, 0);
  context.lineTo(112, 0);
  context.stroke();

  context.beginPath();
  context.moveTo(22, 112);
  context.quadraticCurveTo(22, 22, 112, 22);
  context.stroke();

  context.beginPath();
  context.arc(22, 22, 10, 0, Math.PI * 2);
  context.fill();
  context.restore();
}

function drawBrandMark(context: CanvasRenderingContext2D, centerX: number, centerY: number) {
  context.save();

  const halo = context.createRadialGradient(centerX, centerY, 20, centerX, centerY, 118);
  halo.addColorStop(0, '#df3150');
  halo.addColorStop(1, COLORS.redDark);
  context.fillStyle = halo;
  context.beginPath();
  context.arc(centerX, centerY, 112, 0, Math.PI * 2);
  context.fill();

  context.strokeStyle = COLORS.gold;
  context.lineWidth = 7;
  context.beginPath();
  context.arc(centerX, centerY, 126, 0, Math.PI * 2);
  context.stroke();

  context.fillStyle = '#ffffff';
  context.fillRect(centerX - 72, centerY - 50, 144, 16);
  context.fillRect(centerX - 60, centerY - 25, 120, 13);
  context.fillRect(centerX - 52, centerY - 12, 15, 95);
  context.fillRect(centerX + 37, centerY - 12, 15, 95);
  context.fillRect(centerX - 65, centerY + 30, 130, 14);
  context.fillRect(centerX - 70, centerY + 79, 42, 10);
  context.fillRect(centerX + 28, centerY + 79, 42, 10);

  context.restore();
}

function drawOrnamentalDivider(
  context: CanvasRenderingContext2D,
  startX: number,
  endX: number,
  y: number,
) {
  context.save();
  const gradient = context.createLinearGradient(startX, y, endX, y);
  gradient.addColorStop(0, 'rgba(200, 164, 90, 0)');
  gradient.addColorStop(0.25, COLORS.gold);
  gradient.addColorStop(0.75, COLORS.gold);
  gradient.addColorStop(1, 'rgba(200, 164, 90, 0)');
  context.strokeStyle = gradient;
  context.lineWidth = 3;
  context.beginPath();
  context.moveTo(startX, y);
  context.lineTo(endX, y);
  context.stroke();

  context.fillStyle = COLORS.red;
  context.translate((startX + endX) / 2, y);
  context.rotate(Math.PI / 4);
  context.fillRect(-9, -9, 18, 18);
  context.restore();
}

function drawCompletionBadge(context: CanvasRenderingContext2D, centerX: number, centerY: number) {
  context.save();
  context.strokeStyle = COLORS.gold;
  context.lineWidth = 3;
  roundedRect(context, centerX - 310, centerY - 42, 620, 84, 42);
  context.stroke();

  context.fillStyle = COLORS.redDark;
  context.font = '700 27px Arial, sans-serif';
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.fillText('HOÀN THÀNH CHƯƠNG TRÌNH ĐÀO TẠO', centerX, centerY + 1);
  context.restore();
}

function drawCertificateFooter(
  context: CanvasRenderingContext2D,
  completionMoment: string,
  certificateNumber: string,
) {
  const footerY = 1955;

  context.save();
  context.textAlign = 'center';
  context.textBaseline = 'middle';

  context.fillStyle = COLORS.muted;
  context.font = '500 25px Arial, sans-serif';
  context.fillText('HOÀN THÀNH LÚC', 730, footerY);
  context.fillText('MÃ CHỨNG CHỈ', 1754, footerY);
  context.fillText('ĐẠI DIỆN MANABIHUB', 2778, footerY);

  context.fillStyle = COLORS.navy;
  context.font = '700 34px Arial, sans-serif';
  context.fillText(completionMoment, 730, footerY + 66);

  const certificateFontSize = fitFontSize(
    context,
    certificateNumber,
    30,
    20,
    780,
    '700',
    'Arial, sans-serif',
  );
  context.font = `700 ${certificateFontSize}px Arial, sans-serif`;
  context.fillText(certificateNumber, 1754, footerY + 66);

  context.font = 'italic 700 46px Georgia, "Times New Roman", serif';
  context.fillStyle = COLORS.redDark;
  context.fillText('ManabiHub', 2778, footerY + 52);

  context.strokeStyle = COLORS.gold;
  context.lineWidth = 2;
  context.beginPath();
  context.moveTo(420, footerY + 112);
  context.lineTo(1040, footerY + 112);
  context.moveTo(1320, footerY + 112);
  context.lineTo(2188, footerY + 112);
  context.moveTo(2468, footerY + 112);
  context.lineTo(3088, footerY + 112);
  context.stroke();

  context.fillStyle = COLORS.muted;
  context.font = '400 21px Arial, sans-serif';
  context.fillText('Chứng chỉ điện tử được phát hành bởi hệ thống ManabiHub', 1754, 2220);
  context.restore();
}

function wrapCenteredText(
  context: CanvasRenderingContext2D,
  value: string,
  maxWidth: number,
  preferredFontSize: number,
  minimumFontSize: number,
  fontWeight: string,
  fontFamily: string,
  maximumLines: number,
) {
  for (let fontSize = preferredFontSize; fontSize >= minimumFontSize; fontSize -= 2) {
    context.font = `${fontWeight} ${fontSize}px ${fontFamily}`;
    const lines = wrapText(context, value, maxWidth);
    if (lines.length <= maximumLines) {
      return { lines, fontSize, fontWeight, fontFamily };
    }
  }

  context.font = `${fontWeight} ${minimumFontSize}px ${fontFamily}`;
  const lines = wrapText(context, value, maxWidth).slice(0, maximumLines);
  if (lines.length === maximumLines) {
    while (context.measureText(`${lines[maximumLines - 1]}…`).width > maxWidth) {
      lines[maximumLines - 1] = lines[maximumLines - 1].slice(0, -1);
    }
    lines[maximumLines - 1] = `${lines[maximumLines - 1].trim()}…`;
  }
  return { lines, fontSize: minimumFontSize, fontWeight, fontFamily };
}

function wrapText(context: CanvasRenderingContext2D, value: string, maxWidth: number) {
  const words = value.trim().split(/\s+/);
  const lines: string[] = [];
  let currentLine = '';

  words.forEach((word) => {
    const candidate = currentLine ? `${currentLine} ${word}` : word;
    if (!currentLine || context.measureText(candidate).width <= maxWidth) {
      currentLine = candidate;
      return;
    }
    lines.push(currentLine);
    currentLine = word;
  });

  if (currentLine) lines.push(currentLine);
  return lines.length > 0 ? lines : [''];
}

function fitFontSize(
  context: CanvasRenderingContext2D,
  value: string,
  preferredSize: number,
  minimumSize: number,
  maxWidth: number,
  fontWeight: string,
  fontFamily: string,
) {
  for (let size = preferredSize; size >= minimumSize; size -= 2) {
    context.font = `${fontWeight} ${size}px ${fontFamily}`;
    if (context.measureText(value).width <= maxWidth) return size;
  }
  return minimumSize;
}

function drawLetterSpacedText(
  context: CanvasRenderingContext2D,
  value: string,
  centerX: number,
  y: number,
  spacing: number,
  style: { font: string; color: string },
) {
  context.save();
  context.font = style.font;
  context.fillStyle = style.color;
  context.textAlign = 'left';
  context.textBaseline = 'middle';

  const characters = [...value];
  const characterWidths = characters.map((character) => context.measureText(character).width);
  const totalWidth = characterWidths.reduce((sum, width) => sum + width, 0)
    + Math.max(0, characters.length - 1) * spacing;
  let x = centerX - totalWidth / 2;

  characters.forEach((character, index) => {
    context.fillText(character, x, y);
    x += characterWidths[index] + spacing;
  });
  context.restore();
}

function roundedRect(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
) {
  const safeRadius = Math.min(radius, width / 2, height / 2);
  context.beginPath();
  context.moveTo(x + safeRadius, y);
  context.lineTo(x + width - safeRadius, y);
  context.quadraticCurveTo(x + width, y, x + width, y + safeRadius);
  context.lineTo(x + width, y + height - safeRadius);
  context.quadraticCurveTo(x + width, y + height, x + width - safeRadius, y + height);
  context.lineTo(x + safeRadius, y + height);
  context.quadraticCurveTo(x, y + height, x, y + height - safeRadius);
  context.lineTo(x, y + safeRadius);
  context.quadraticCurveTo(x, y, x + safeRadius, y);
  context.closePath();
}

function formatCompletionMoment(date: Date) {
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function sanitizeFilename(value: string) {
  const sanitized = [...value.trim()]
    .map((character) => (
      character.charCodeAt(0) < 32 || /[<>:"/\\|?*]/.test(character) ? '-' : character
    ))
    .join('');
  return sanitized || 'chung-chi-manabihub';
}
