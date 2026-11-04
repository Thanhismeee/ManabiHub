package com.manabihub.cohort.service;

import com.manabihub.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** Decode local audio instead of trusting client metadata. No shell or remote media inputs. */
@Component
public class CohortAudioValidator {
    @Value("${manabihub.cohort.ffmpeg-executable:}") private String executable;
    private final Semaphore slots = new Semaphore(2);
    private static final Pattern TIME = Pattern.compile("^out_time_us=(\\d+)$");
    private static final Pattern VIDEO = Pattern.compile("^\\s*Stream #.*: Video:.*");
    public int verify(Path file, String extension, int maxSeconds) {
        if (!slots.tryAcquire()) throw new BusinessException("COHORT_AUDIO_BUSY", "Đang xử lý nhiều bản ghi. Hãy thử lại sau ít phút.", HttpStatus.TOO_MANY_REQUESTS);
        Process process = null;
        ExecutorService reader = Executors.newVirtualThreadPerTaskExecutor();
        try {
            String demuxer = switch (extension) { case "webm" -> "matroska"; case "m4a" -> "mov"; default -> extension; };
            process = new ProcessBuilder(decoder(), "-hide_banner", "-nostdin", "-max_alloc", "67108864", "-threads", "1",
                    "-protocol_whitelist", "file,pipe", "-probesize", "8388608", "-analyzeduration", "5000000", "-f", demuxer,
                    "-xerror", "-err_detect", "explode", "-i", file.toAbsolutePath().toString(), "-map", "0:a:0", "-vn", "-t", Integer.toString(maxSeconds + 1),
                    "-ac", "1", "-ar", "16000", "-filter_threads", "1", "-progress", "pipe:1", "-f", "null", "-")
                    .redirectErrorStream(true).start();
            Process running = process;
            Future<long[]> result = reader.submit(() -> {
                long duration = 0, bytes = 0; boolean video = false;
                try (BufferedReader output = new BufferedReader(new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = output.readLine()) != null) {
                        bytes += line.length();
                        if (bytes > 1024 * 1024) { running.destroyForcibly(); throw new IOException("Media output exceeded bound"); }
                        if (VIDEO.matcher(line).matches()) video = true;
                        var time = TIME.matcher(line); if (time.matches()) duration = Math.max(duration, Long.parseLong(time.group(1)));
                    }
                }
                return new long[]{duration, video ? 1 : 0};
            });
            if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); throw invalid(); }
            long[] measured = result.get(2, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || measured[1] != 0 || measured[0] <= 0 || measured[0] > maxSeconds * 1_000_000L + 50_000L) throw invalid();
            return Math.min(maxSeconds, (int) Math.ceil(measured[0] / 1_000_000.0));
        } catch (BusinessException error) { throw error; }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw invalid(); }
        catch (IOException error) {
            throw new BusinessException("COHORT_AUDIO_DECODER_UNAVAILABLE", "Chưa xác minh được bản ghi. Liên hệ quản trị để kiểm tra bộ xử lý âm thanh.", HttpStatus.SERVICE_UNAVAILABLE, error);
        } catch (Exception error) { throw invalid(); }
        finally { if (process != null && process.isAlive()) process.destroyForcibly(); reader.shutdownNow(); slots.release(); }
    }
    private BusinessException invalid() { return new BusinessException("COHORT_AUDIO_INVALID", "Bản ghi không đọc được, có luồng video hoặc vượt thời lượng bài tập.", HttpStatus.BAD_REQUEST); }
    private String decoder() {
        if (executable != null && !executable.isBlank()) return executable;
        for (String candidate : java.util.List.of("tools/ffmpeg/ffmpeg.exe", "backend/tools/ffmpeg/ffmpeg.exe", "tools/ffmpeg/ffmpeg")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (java.nio.file.Files.isRegularFile(path)) return path.toString();
        }
        return "ffmpeg";
    }
}
