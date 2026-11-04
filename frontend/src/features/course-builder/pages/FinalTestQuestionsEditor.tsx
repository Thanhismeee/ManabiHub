import {
  Button, IconButton, Stack, TextField, Typography, Tooltip,
  Accordion, AccordionSummary, AccordionDetails, Radio, RadioGroup, FormControlLabel,
  Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Box,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import DownloadIcon from '@mui/icons-material/Download';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { useRef, useState } from 'react';
import type { FinalTestQuestion } from '../services/finalTestService';
import {
  createFinalTestCsvTemplate,
  FinalTestCsvError,
  MAX_FINAL_TEST_CSV_BYTES,
  parseFinalTestCsv,
} from '../utils/finalTestCsv';

interface Props {
  questions: FinalTestQuestion[];
  onChange: (questions: FinalTestQuestion[]) => void;
  expanded: number | false;
  setExpanded: (expanded: number | false) => void;
  onNotify: (msg: string, severity: 'success' | 'error' | 'warning' | 'info') => void;
}

export const FinalTestQuestionsEditor = ({ questions, onChange, expanded, setExpanded, onNotify }: Props) => {
  const fileInputRef = useRef<HTMLInputElement>(null);

  // State for the bounded, dependency-free CSV import preview.
  const [pendingImport, setPendingImport] = useState<FinalTestQuestion[]>([]);
  const [openDialog, setOpenDialog] = useState(false);

  const handleAccordionChange = (panel: number) => (_event: React.SyntheticEvent, isExpanded: boolean) => {
    setExpanded(isExpanded ? panel : false);
  };

  const handleDownloadTemplate = () => {
    const blob = new Blob([createFinalTestCsvTemplate()], { type: 'text/csv;charset=utf-8' });
    const downloadUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = 'Template_Cau_Hoi.csv';
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 0);
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      if (!file.name.toLocaleLowerCase('vi').endsWith('.csv')) {
        throw new FinalTestCsvError('Chỉ chấp nhận file CSV từ mẫu của hệ thống.');
      }
      if (file.size > MAX_FINAL_TEST_CSV_BYTES) {
        throw new FinalTestCsvError('File CSV vượt quá giới hạn 1 MB.');
      }

      const { duplicateCount, questions: newQuestions } = parseFinalTestCsv(
        await file.text(),
        questions,
      );

      if (newQuestions.length > 0) {
        if (duplicateCount > 0) {
          onNotify(`Hệ thống tự động bỏ qua ${duplicateCount} câu hỏi bị trùng lặp nội dung. Tìm thấy ${newQuestions.length} câu hỏi mới hợp lệ.`, 'warning');
        } else {
          onNotify(`Đã đọc thành công ${newQuestions.length} câu hỏi từ CSV.`, 'success');
        }
        setPendingImport(newQuestions);
        setOpenDialog(true);
      } else if (duplicateCount > 0) {
        onNotify(`Tất cả ${duplicateCount} câu hỏi trong file CSV đều đã có sẵn trong danh sách. Không có câu hỏi mới nào được thêm.`, 'info');
      } else {
        onNotify('Không tìm thấy câu hỏi hợp lệ nào trong file CSV.', 'warning');
      }
    } catch (error) {
      const message = error instanceof FinalTestCsvError
        ? error.message
        : 'Có lỗi xảy ra khi đọc file CSV.';
      onNotify(message, 'error');
    } finally {
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const handleConfirmImport = () => {
    onChange([...questions, ...pendingImport]);
    setPendingImport([]);
    setOpenDialog(false);
    if (fileInputRef.current) fileInputRef.current.value = '';

    // Expand the first newly imported question
    if (pendingImport.length > 0) {
      const newIndex = questions.length;
      setExpanded(newIndex);
      setTimeout(() => {
        document.getElementById(`question-accordion-${newIndex}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 100);
    }
  };

  const handleCancelImport = () => {
    setPendingImport([]);
    setOpenDialog(false);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleAddQuestion = () => {
    const newQuestion: FinalTestQuestion = {
      content: '',
      explanation: '',
      choices: [
        { content: '', isCorrect: true },
        { content: '', isCorrect: false },
        { content: '', isCorrect: false },
        { content: '', isCorrect: false },
      ],
    };
    const newIndex = questions.length;
    onChange([...questions, newQuestion]);
    setExpanded(newIndex); // Auto expand new question
    setTimeout(() => {
      document.getElementById(`question-accordion-${newIndex}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 100);
  };

  const handleRemoveQuestion = (e: React.MouseEvent, index: number) => {
    e.stopPropagation(); // Prevent accordion toggle
    onChange(questions.filter((_, i) => i !== index));
  };

  const handleChangeQuestion = (index: number, field: keyof FinalTestQuestion, value: string) => {
    const newQuestions = [...questions];
    newQuestions[index] = { ...newQuestions[index], [field]: value };
    onChange(newQuestions);
  };

  const handleChoiceContentChange = (qIndex: number, cIndex: number, value: string) => {
    const newQuestions = [...questions];
    const choices = [...newQuestions[qIndex].choices];
    choices[cIndex] = { ...choices[cIndex], content: value };
    newQuestions[qIndex].choices = choices;
    onChange(newQuestions);
  };

  const handleCorrectChoiceChange = (qIndex: number, cIndexStr: string) => {
    const cIndex = parseInt(cIndexStr, 10);
    const newQuestions = [...questions];
    const choices = newQuestions[qIndex].choices.map((c, idx) => ({
      ...c,
      isCorrect: idx === cIndex
    }));
    newQuestions[qIndex].choices = choices;
    onChange(newQuestions);
  };

  const handleAddChoice = (qIndex: number) => {
    const newQuestions = [...questions];
    if (newQuestions[qIndex].choices.length >= 10) {
      onNotify("Tối đa 10 lựa chọn cho mỗi câu hỏi.", "warning");
      return;
    }
    newQuestions[qIndex].choices.push({ content: '', isCorrect: false });
    onChange(newQuestions);
  };

  const handleRemoveChoice = (qIndex: number, cIndex: number) => {
    const newQuestions = [...questions];
    if (newQuestions[qIndex].choices.length <= 2) {
      onNotify("Tối thiểu phải có 2 lựa chọn.", "warning");
      return;
    }
    newQuestions[qIndex].choices.splice(cIndex, 1);
    // If the removed choice was the correct one, make the first one correct
    if (!newQuestions[qIndex].choices.some(c => c.isCorrect)) {
      newQuestions[qIndex].choices[0].isCorrect = true;
    }
    onChange(newQuestions);
  };

  return (
    <Stack spacing={3}>
      {questions.length === 0 ? (
        <Box
          sx={{
            p: 5,
            textAlign: 'center',
            bgcolor: '#f9f9f9',
            borderRadius: 2,
            border: '2px dashed #ccc'
          }}
        >
          <Typography variant="h6" color="text.secondary" gutterBottom>
            Chưa có câu hỏi nào trong đề thi này
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Hãy bấm "Thêm câu hỏi" để tự soạn, hoặc "Import CSV" để nạp hàng loạt câu hỏi một cách nhanh chóng!
          </Typography>
        </Box>
      ) : (
        questions.map((q, qIndex) => {
          // Find which index is correct
          const correctChoiceIndex = q.choices.findIndex(c => c.isCorrect);

          return (
            <Accordion
              id={`question-accordion-${qIndex}`}
              key={qIndex}
              expanded={expanded === qIndex}
              onChange={handleAccordionChange(qIndex)}
              variant="outlined"
              sx={{ '&:before': { display: 'none' } }}
            >
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', pr: 2 }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 'bold' }}>
                    Câu {qIndex + 1}: {q.content ? (q.content.length > 50 ? q.content.substring(0, 50) + '...' : q.content) : '(Chưa nhập nội dung)'}
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Tooltip title="Xóa câu hỏi">
                      <IconButton component="span" size="small" color="error" onClick={(e) => handleRemoveQuestion(e, qIndex)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                </Box>
              </AccordionSummary>

              <AccordionDetails sx={{ borderTop: '1px solid #e0e0e0', pt: 3 }}>
                <Stack spacing={3}>
                  <TextField
                    label="Nội dung câu hỏi"
                    multiline
                    rows={2}
                    fullWidth
                    value={q.content}
                    onChange={(e) => handleChangeQuestion(qIndex, 'content', e.target.value)}
                    required
                  />

                  <Box>
                    <Typography variant="subtitle2" sx={{ mb: 1 }}>Các lựa chọn (chọn 1 đáp án đúng):</Typography>
                    <RadioGroup
                      value={correctChoiceIndex.toString()}
                      onChange={(e) => handleCorrectChoiceChange(qIndex, e.target.value)}
                    >
                      <Stack spacing={1}>
                        {q.choices.map((choice, cIndex) => (
                          <Stack direction="row" spacing={2} key={cIndex} sx={{ alignItems: 'center' }}>
                            <FormControlLabel
                              value={cIndex.toString()}
                              control={<Radio />}
                              label=""
                              sx={{ mr: 0 }}
                            />
                            <TextField
                              size="small"
                              fullWidth
                              placeholder={`Lựa chọn ${cIndex + 1}`}
                              value={choice.content}
                              onChange={(e) => handleChoiceContentChange(qIndex, cIndex, e.target.value)}
                              required
                            />
                            {q.choices.length > 2 && (
                              <Tooltip title="Xóa lựa chọn">
                                <IconButton size="small" color="error" onClick={() => handleRemoveChoice(qIndex, cIndex)}>
                                  <DeleteIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            )}
                          </Stack>
                        ))}
                      </Stack>
                    </RadioGroup>
                    <Button
                      variant="text"
                      size="small"
                      startIcon={<AddIcon />}
                      onClick={() => handleAddChoice(qIndex)}
                      sx={{ alignSelf: 'flex-start', mt: 1, textTransform: 'none' }}
                    >
                      Thêm lựa chọn
                    </Button>
                  </Box>

                  <TextField
                    label="Giải thích đáp án"
                    multiline
                    rows={2}
                    fullWidth
                    value={q.explanation}
                    onChange={(e) => handleChangeQuestion(qIndex, 'explanation', e.target.value)}
                    required
                  />
                </Stack>
              </AccordionDetails>
            </Accordion>
          );
        }))}

      <Stack direction="row" spacing={2} sx={{ mt: 3 }}>
        <Button
          variant="outlined"
          startIcon={<AddIcon />}
          onClick={handleAddQuestion}
          sx={{ borderStyle: 'dashed' }}
        >
          Thêm câu hỏi
        </Button>
        <Button
          variant="contained"
          color="success"
          startIcon={<UploadFileIcon />}
          onClick={() => fileInputRef.current?.click()}
        >
          Import CSV
        </Button>
        <Button
          variant="text"
          color="secondary"
          startIcon={<DownloadIcon />}
          onClick={handleDownloadTemplate}
        >
          Tải file mẫu
        </Button>
        <input
          type="file"
          accept=".csv,text/csv"
          hidden
          ref={fileInputRef}
          onChange={handleFileUpload}
        />
      </Stack>

      <Dialog open={openDialog} onClose={handleCancelImport} maxWidth="md" fullWidth>
        <DialogTitle>Xác nhận Import CSV</DialogTitle>
        <DialogContent dividers>
          <DialogContentText sx={{ mb: 2 }}>
            Hệ thống đã quét và tìm thấy <strong>{pendingImport.length}</strong> câu hỏi hợp lệ từ file CSV.
            Dữ liệu mới sẽ được nối thêm vào cuối danh sách hiện tại.
          </DialogContentText>

          <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 'bold' }}>
            Xem trước dữ liệu ({Math.min(3, pendingImport.length)} câu đầu tiên):
          </Typography>

          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead sx={{ bgcolor: '#f5f5f5' }}>
                <TableRow>
                  <TableCell width={50}><strong>STT</strong></TableCell>
                  <TableCell><strong>Nội dung câu hỏi</strong></TableCell>
                  <TableCell width={100} align="center"><strong>Số đáp án</strong></TableCell>
                  <TableCell width={100} align="center"><strong>Đáp án đúng</strong></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {pendingImport.slice(0, 3).map((q, idx) => (
                  <TableRow key={idx}>
                    <TableCell>{idx + 1}</TableCell>
                    <TableCell sx={{ maxWidth: 300, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {q.content}
                    </TableCell>
                    <TableCell align="center">{q.choices.length}</TableCell>
                    <TableCell align="center">
                      Số {q.choices.findIndex(c => c.isCorrect) + 1}
                    </TableCell>
                  </TableRow>
                ))}
                {pendingImport.length > 3 && (
                  <TableRow>
                    <TableCell colSpan={4} align="center" sx={{ color: 'text.secondary', fontStyle: 'italic' }}>
                      Và {pendingImport.length - 3} câu hỏi khác...
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCancelImport} color="inherit">Hủy</Button>
          <Button onClick={handleConfirmImport} variant="contained" color="success" autoFocus>
            Xác nhận thêm
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
};
