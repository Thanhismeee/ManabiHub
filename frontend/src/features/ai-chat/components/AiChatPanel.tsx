import { useEffect, useState } from 'react';
import axios from 'axios';
import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined';
import SendOutlinedIcon from '@mui/icons-material/SendOutlined';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { useAiChatEligibility } from '../hooks/useAiChatEligibility';
import { useSendAiChatMessage } from '../hooks/useSendAiChatMessage';

interface ChatMessage {
  id: string;
  sender: 'student' | 'assistant';
  content: string;
}

interface AiChatPanelProps {
  courseId: string;
  lessonBlockId: string;
  lessonTitle?: string;
  onClose?: () => void;
}

function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message ?? 'AI hiện chưa thể trả lời. Vui lòng thử lại sau.';
  }
  return 'AI hiện chưa thể trả lời. Vui lòng thử lại sau.';
}

export function AiChatPanel({ courseId, lessonBlockId, lessonTitle, onClose }: AiChatPanelProps) {
  const eligibility = useAiChatEligibility(courseId, lessonBlockId);
  const sendMessage = useSendAiChatMessage();
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [requestError, setRequestError] = useState<string | null>(null);

  // A chat belongs to one lesson context. Keep it while the panel is closed,
  // but never carry messages into a different lesson.
  useEffect(() => {
    setQuestion('');
    setMessages([]);
    setRequestError(null);
  }, [courseId, lessonBlockId]);

  const handleSend = async () => {
    const trimmedQuestion = question.trim();
    if (!trimmedQuestion || !eligibility.data?.eligible) {
      return;
    }

    const studentMessage: ChatMessage = {
      id: crypto.randomUUID(),
      sender: 'student',
      content: trimmedQuestion,
    };
    setMessages((current) => [...current, studentMessage]);
    setQuestion('');
    setRequestError(null);

    try {
      const history = messages.slice(-6).map((msg) => ({
        role: msg.sender === 'student' ? ('user' as const) : ('assistant' as const),
        content: msg.content,
      }));

      const response = await sendMessage.mutateAsync({
        courseId,
        lessonBlockId,
        question: trimmedQuestion,
        history,
      });
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          sender: 'assistant',
          content: response.answer,
        },
      ]);
    } catch (error) {
      setRequestError(errorMessage(error));
    }
  };

  if (eligibility.isLoading) {
    return (
      <Box sx={{ minHeight: 240, display: 'grid', placeItems: 'center', border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
        <CircularProgress size={28} />
      </Box>
    );
  }

  if (eligibility.isError) {
    return (
      <Alert
        severity="error"
        action={<Button color="inherit" size="small" onClick={() => eligibility.refetch()}>Thử lại</Button>}
      >
        Không thể kiểm tra khả năng sử dụng AI cho bài học này.
      </Alert>
    );
  }

  const available = eligibility.data?.eligible === true;

  return (
    <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, bgcolor: 'background.paper' }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ px: 2, py: 1.5, alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
      >
        <Stack direction="row" spacing={1} sx={{ minWidth: 0, alignItems: 'center', flexGrow: 1 }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Trợ lý AI</Typography>
            <Typography variant="body2" color="text.secondary" noWrap title={lessonTitle}>
              {lessonTitle ? `Đang hỗ trợ: ${lessonTitle}` : 'Chỉ sử dụng ngữ cảnh bài học hiện tại'}
            </Typography>
          </Box>
          <Chip
            label={available ? 'Đang bật' : 'Không khả dụng'}
            color={available ? 'success' : 'default'}
            size="small"
            sx={{ flexShrink: 0 }}
          />
        </Stack>
        {onClose && (
          <Tooltip title="Đóng trợ lý AI">
            <IconButton onClick={onClose} aria-label="Đóng trợ lý AI" size="small">
              <CloseOutlinedIcon />
            </IconButton>
          </Tooltip>
        )}
      </Stack>
      <Divider />

      {!available ? (
        <Box sx={{ p: 2 }}>
          <Alert severity="info">{eligibility.data?.message ?? 'AI hiện không khả dụng cho bài học này.'}</Alert>
        </Box>
      ) : (
        <Stack spacing={2} sx={{ p: 2 }}>
          <Box
            role="log"
            aria-live="polite"
            sx={{ minHeight: 280, maxHeight: 400, overflowY: 'auto', display: 'grid', alignContent: 'start', gap: 1.25 }}
          >
            {messages.length === 0 ? (
              <Typography variant="body2" color="text.secondary">Đặt câu hỏi về bài học hiện tại.</Typography>
            ) : messages.map((message) => (
              <Box
                key={message.id}
                sx={{
                  maxWidth: '82%',
                  justifySelf: message.sender === 'student' ? 'end' : 'start',
                  px: 1.5,
                  py: 1,
                  borderRadius: 1,
                  bgcolor: message.sender === 'student' ? 'primary.main' : 'action.hover',
                  color: message.sender === 'student' ? 'primary.contrastText' : 'text.primary',
                  whiteSpace: 'pre-wrap',
                  overflowWrap: 'anywhere',
                }}
              >
                <Typography variant="body2">{message.content}</Typography>
              </Box>
            ))}
            {sendMessage.isPending && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <CircularProgress size={16} />
                <Typography variant="body2" color="text.secondary">AI đang suy nghĩ...</Typography>
              </Box>
            )}
          </Box>

          {requestError && <Alert severity="error">{requestError}</Alert>}

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: 'stretch' }}>
            <TextField
              fullWidth
              multiline
              minRows={2}
              maxRows={5}
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Hỏi về bài học hiện tại"
              slotProps={{ htmlInput: { maxLength: 2000 } }}
            />
            <Button
              variant="contained"
              endIcon={<SendOutlinedIcon />}
              onClick={handleSend}
              disabled={!question.trim() || sendMessage.isPending}
              sx={{ minWidth: { sm: 112 }, alignSelf: { sm: 'flex-end' } }}
            >
              Gửi
            </Button>
          </Stack>
          <Typography variant="caption" color="text.secondary">
            Nội dung AI chỉ mang tính hỗ trợ học tập, không thay thế kết quả chính thức.
          </Typography>
        </Stack>
      )}
    </Box>
  );
}
