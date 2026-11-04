const BUSINESS_TIME_ZONE = 'Asia/Ho_Chi_Minh';

export function mondayOfCurrentWeek(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: BUSINESS_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
  }).formatToParts(now);
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? '';
  const weekdayIndex = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
    .indexOf(value('weekday'));
  const businessDate = new Date(Date.UTC(
    Number(value('year')),
    Number(value('month')) - 1,
    Number(value('day')),
  ));
  businessDate.setUTCDate(businessDate.getUTCDate() - Math.max(0, weekdayIndex));
  return businessDate.toISOString().slice(0, 10);
}

/**
 * Lùi một ngày bất kỳ về Thứ Hai của chính tuần đó.
 * Nhận và trả chuỗi 'YYYY-MM-DD'. Chuỗi rỗng hoặc không đúng định dạng được
 * trả nguyên vẹn, để người dùng vẫn xóa trắng được ô ngày trong lúc nhập.
 */
export function mondayOfWeek(isoDate: string): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(isoDate)) {
    return isoDate;
  }
  const [year, month, day] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (Number.isNaN(date.getTime())) {
    return isoDate;
  }
  const weekdayIndex = (date.getUTCDay() + 6) % 7; // Thứ Hai = 0 ... Chủ Nhật = 6
  date.setUTCDate(date.getUTCDate() - weekdayIndex);
  return date.toISOString().slice(0, 10);
}
