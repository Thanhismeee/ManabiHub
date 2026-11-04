import { describe, expect, it } from 'vitest';
import { parseJlptOcrText } from './certificateOcr';

describe('parseJlptOcrText', () => {
  it('extracts the certificate fields from a noisy JLPT transcript', () => {
    const result = parseJlptOcrText(`
      N3
      CERTIFICATE
      JAPANESE-LANGUAGE PROFICIENCY
      Name
      THAN VAN THANH
      Date of Birth
      2004/08/12
      Test Site Vietnam
      This is to certify that the person named above has passed
      25B2080102-33745
      N3A568591A
    `);

    expect(result.holderName).toBe('THAN VAN THANH');
    expect(result.dateOfBirth).toBe('2004-08-12');
    expect(result.level).toBe('N3');
    expect(result.certificateCode).toBe('25B2080102-33745');
    expect(result.rawText).toBe([
      'Name: THAN VAN THANH',
      'Date of Birth: 2004-08-12',
      'Level: N3',
      'Certificate No: 25B2080102-33745',
    ].join('\n'));
    expect(result.rawText).not.toContain('JAPANESE-LANGUAGE');
  });

  it('does not treat the certificate heading as a certificate number', () => {
    const result = parseJlptOcrText('CERTIFICATE JAPANESE-LANGUAGE PROFICIENCY\nN3\nName\nTHAN VAN THANH');
    expect(result.certificateCode).toBe('');
    expect(result.holderName).toBe('THAN VAN THANH');
  });

  it('handles OCR with Japanese labels (氏名, 生年月日) from jpn+eng', () => {
    const result = parseJlptOcrText(`
      N3
      日本語能力認定書
      CERTIFICATE
      JAPANESE-LANGUAGE PROFICIENCY
      氏名
      THAN VAN THANH
      Name
      生年月日(y/m/d) 2004/08/12
      Date of Birth
      受験地 ベトナム Vietnam
      Test Site
      25B2080102-33745
      N3A568591A
    `);

    expect(result.holderName).toBe('THAN VAN THANH');
    expect(result.dateOfBirth).toBe('2004-08-12');
    expect(result.level).toBe('N3');
    expect(result.certificateCode).toBe('25B2080102-33745');
  });

  it('rejects garbage name like "HH y m d" (single-char words)', () => {
    // When OCR garbles 氏名 into ASCII, it often produces short single-char tokens.
    const result = parseJlptOcrText(`
      N3
      HH y m d
      THAN VAN THANH
      2004/08/12
      25B2080102-33745
    `);

    expect(result.holderName).toBe('THAN VAN THANH');
    expect(result.holderName).not.toBe('HH y m d');
  });

  it('parses 生年月日 label followed by date', () => {
    const result = parseJlptOcrText(`
      N2
      氏名 NGUYEN THI HOA
      生年月日 1998/03/15
      25A1990301-12345
    `);

    expect(result.holderName).toBe('NGUYEN THI HOA');
    expect(result.dateOfBirth).toBe('1998-03-15');
    expect(result.level).toBe('N2');
    expect(result.certificateCode).toBe('25A1990301-12345');
  });

  it('handles Vietnamese diacritics in names', () => {
    const result = parseJlptOcrText(`
      N1
      Name
      TRẦN VĂN ĐỨC
      Date of Birth 1995/11/20
      24B1234567-98765
    `);

    expect(result.holderName).toBe('TRẦN VĂN ĐỨC');
    expect(result.dateOfBirth).toBe('1995-11-20');
    expect(result.level).toBe('N1');
  });

  it('extracts the lower-left serial when registration number is missing', () => {
    const result = parseJlptOcrText(`
      N4
      Name
      LE MINH TAI
      2001/06/05
      N4B123456A
    `);

    expect(result.certificateCode).toBe('N4B123456A');
  });

  it('extracts spaced fields from focused OCR passes and prefers the registration number', () => {
    const result = parseJlptOcrText(`
      N 3
      Name THAN VAN THANH
      Date of Birth 2004 / 08 / 12
      N 3 A 5 6 8 5 9 1 A
      25 B 2080102 — 33745
    `);

    expect(result.level).toBe('N3');
    expect(result.certificateCode).toBe('25B2080102-33745');
  });

  it('repairs common OCR letter substitutions inside the lower-left serial', () => {
    const result = parseJlptOcrText(`
      N3 A S68S9IA
      Name THAN VAN THANH
      Date of Birth 2004/08/12
    `);

    expect(result.level).toBe('N3');
    expect(result.certificateCode).toBe('N3A568591A');
  });

  it('strips leaked CJK characters from name lines', () => {
    // OCR sometimes partially reads the kanji label into the name value line.
    const result = parseJlptOcrText(`
      N3
      氏名 THAN VAN THANH
      生年月日 2004/08/12
    `);

    expect(result.holderName).toBe('THAN VAN THANH');
    expect(result.holderName).not.toMatch(/[\u3000-\u9FFF]/);
  });

  it('returns empty fields gracefully for completely unreadable input', () => {
    const result = parseJlptOcrText('||| $$$ %%% @@@ 123');
    expect(result.holderName).toBe('');
    expect(result.dateOfBirth).toBe('');
    expect(result.level).toBe('');
    expect(result.certificateCode).toBe('');
    expect(result.rawText).toBe('');
  });
});
