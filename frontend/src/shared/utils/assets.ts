// Tự động load tất cả các file ảnh trong thư mục assets
const images = import.meta.glob('../../assets/**/*.{png,jpg,jpeg,svg,gif,webp}', { eager: true, import: 'default' }) as Record<string, string>;

/**
 * Hàm hỗ trợ lấy đường dẫn ảnh từ thư mục assets mà không cần import thủ công
 * @param fileName Tên file ảnh (ví dụ: 'anh1.png', 'hero.png')
 * @returns Đường dẫn đã được hash của file ảnh, hoặc string rỗng nếu không tìm thấy
 */
export const getAsset = (fileName: string) => {
  const path = `../../assets/${fileName}`;
  return images[path] || '';
};
