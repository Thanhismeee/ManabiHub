import React, { useState, useEffect, useRef } from 'react';
import { Box, Container, Grid, Typography, Paper } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import LaptopMacIcon from '@mui/icons-material/LaptopMac';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';

const FEATURES = [
  {
    icon: <SearchIcon sx={{ fontSize: 32, color: '#C41E3A' }} />,
    title: 'Tìm kiếm khóa học',
    description: 'Dễ dàng tìm kiếm và lựa chọn khóa học phù hợp với mục tiêu JLPT hoặc kỹ năng của bạn.',
  },
  {
    icon: <LaptopMacIcon sx={{ fontSize: 32, color: '#C41E3A' }} />,
    title: 'Đăng ký và học tập',
    description: 'Đăng ký khóa học và học qua video, tài liệu cùng các hoạt động được giảng viên thiết kế.',
  },
  {
    icon: <TaskAltIcon sx={{ fontSize: 32, color: '#C41E3A' }} />,
    title: 'Thực hành bài học',
    description: 'Làm bài tập trắc nghiệm và thực hành đa dạng để củng cố kiến thức ngay sau mỗi bài học.',
  },
  {
    icon: <SmartToyOutlinedIcon sx={{ fontSize: 32, color: '#C41E3A' }} />,
    title: 'Trợ lý AI hỗ trợ',
    description: 'Ở khóa học và tính năng đủ điều kiện, AI có thể đưa ra gợi ý hỗ trợ học tập.',
  }
];

export const FeaturesSection: React.FC = () => {
  const [isVisible, setIsVisible] = useState(false);
  const sectionRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
        }
      },
      { threshold: 0.15, rootMargin: '0px' }
    );
    if (sectionRef.current) observer.observe(sectionRef.current);
    return () => observer.disconnect();
  }, []);

  return (
    <Box
      ref={sectionRef}
      sx={{
        py: 12,
        background: 'linear-gradient(180deg, #FBF9F5 0%, #FFFFFF 100%)',
        perspective: '1000px'
      }}
    >
      <Container maxWidth="lg">
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1.5 }}>
          <Box sx={{ width: 4, height: 20, bgcolor: '#C41E3A', borderRadius: 2, mr: 1.5 }} />
          <Typography variant="overline" sx={{ color: '#C41E3A', fontWeight: 700, letterSpacing: '0.08em', fontSize: '0.85rem' }}>
            TẠI SAO CHỌN MANABIHUB
          </Typography>
        </Box>
        <Typography variant="h3" sx={{ fontWeight: 800, color: '#1A1A2E', mb: 2, textAlign: 'left' }}>
          {'Lý do chọn chúng tôi'}
        </Typography>
        <Typography variant="h6" sx={{ color: '#64748b', mb: 8, fontWeight: 400, textAlign: 'left' }}>
          {'Tại sao bạn nên đồng hành cùng ManabiHub'}
        </Typography>

        <Grid container spacing={4}>
          {FEATURES.map((feature, index) => {
            const isAI = index === 3;
            
            return (
              <Grid
                size={{ xs: 12, sm: 6, md: 3 }}
                key={index}
                sx={{
                  opacity: isVisible ? 1 : 0,
                  transform: isVisible
                    ? 'translateZ(0) translateY(0) rotateX(0deg) scale(1)'
                    : 'translateZ(-200px) translateY(80px) rotateX(-20deg) scale(0.9)',
                  transition: `all 1.2s cubic-bezier(0.16, 1, 0.3, 1) ${index * 0.15}s`,
                  transformStyle: 'preserve-3d'
                }}
              >
                <Paper
                  elevation={0}
                  sx={{
                    p: 4,
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'flex-start',
                    textAlign: 'left',
                    borderRadius: '20px',
                    bgcolor: '#ffffff',
                    border: '1px solid #f1f5f9',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.02)',
                    transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
                    position: 'relative',
                    overflow: 'hidden',
                    '&:hover': {
                      transform: 'translateY(-8px)',
                      borderColor: '#e2e8f0',
                      boxShadow: `0 20px 40px rgba(0,0,0,0.08)`,
                    },
                  }}
                >
                  <Box
                    sx={{
                      width: 64, height: 64, borderRadius: '16px', bgcolor: '#FFF5F5',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', mb: 3,
                      transition: 'transform 0.3s ease',
                      '.MuiPaper-root:hover &': { transform: 'scale(1.1) rotate(-5deg)' }
                    }}
                  >
                    {feature.icon}
                  </Box>
                  <Typography variant="h6" sx={{ fontWeight: 800, color: '#1A1A2E', mb: 1.5, fontSize: '1.1rem' }}>
                    {feature.title}
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#64748b', lineHeight: 1.7, flexGrow: 1 }}>
                    {feature.description}
                  </Typography>
                  
                  {isAI && (
                    <Box sx={{ mt: 2, display: 'inline-flex', bgcolor: '#f1f5f9', px: 1.5, py: 0.5, borderRadius: 1 }}>
                      <Typography sx={{ fontSize: '0.7rem', color: '#64748b', fontWeight: 600 }}>
                        *Chỉ tham khảo, không chấm điểm
                      </Typography>
                    </Box>
                  )}
                </Paper>
              </Grid>
            );
          })}
        </Grid>
      </Container>
    </Box>
  );
};
