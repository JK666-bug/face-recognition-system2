package com.face.recognition.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class DetectionTaskService {

    private static final int MAX_LOG_LINES = 500;
    private static final AtomicLong THREAD_COUNTER = new AtomicLong(1);

    private final Object lock = new Object();
    private final Deque<String> logBuffer = new ArrayDeque<>();
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2, daemonThreadFactory());

    private Process process;
    private TaskState state = TaskState.IDLE;
    private Instant startedAt;
    private String mode = "IDLE";
    private String source = "0";
    private String location = "meetingroom";
    private boolean showWindow = true;
    private String lastError;

    public Map<String, Object> startWebcamTask(String requestedLocation, boolean showWindow) {
        return startTask("webcam", "0", requestedLocation, showWindow);
    }

    public Map<String, Object> startStreamTask(String streamUrl, String requestedLocation, boolean showWindow) {
        String safeSource = (streamUrl == null || streamUrl.isBlank()) ? "" : streamUrl.trim();
        if (safeSource.isEmpty()) {
            synchronized (lock) {
                state = TaskState.ERROR;
                lastError = "Stream URL cannot be empty.";
                appendLog(lastError);
                return buildStatusResponse();
            }
        }
        return startTask("stream", safeSource, requestedLocation, showWindow);
    }

    private Map<String, Object> startTask(String taskMode, String taskSource, String requestedLocation, boolean requestedShowWindow) {
        synchronized (lock) {
            if (process != null && process.isAlive()) {
                appendLog("Start ignored: a detection task is already running.");
                return buildStatusResponse();
            }

            String safeLocation = (requestedLocation == null || requestedLocation.isBlank())
                    ? "meetingroom"
                    : requestedLocation.trim();

            state = TaskState.STARTING;
            lastError = null;
            location = safeLocation;
            mode = taskMode;
            source = taskSource;
            showWindow = requestedShowWindow;
            appendLog("Starting " + taskMode + " task at location: " + safeLocation + ", source: " + taskSource);

            try {
                Path repoRoot = findRepoRoot();
                Path pythonPath = resolvePythonExecutable(repoRoot);
                Path scriptPath = repoRoot.resolve("backend").resolve("algorithms").resolve("demo.py");

                List<String> command = new ArrayList<>();
                command.add(pythonPath.toString());
                command.add(scriptPath.toString());
                command.add("--source");
                command.add(taskSource);
                command.add("--location");
                command.add(safeLocation);
                if (!requestedShowWindow) {
                    command.add("--no-display");
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(repoRoot.toFile());
                pb.redirectErrorStream(true);

                process = pb.start();
                startedAt = Instant.now();
                state = TaskState.RUNNING;

                appendLog("Task started. mode=" + taskMode + ", showWindow=" + requestedShowWindow);
                streamLogs(process.getInputStream());
                watchProcessExit(process);
            } catch (Exception e) {
                state = TaskState.ERROR;
                lastError = "Failed to start detection task: " + e.getMessage();
                appendLog(lastError);
                log.error("Failed to start detection task", e);
            }

            return buildStatusResponse();
        }
    }

    public Map<String, Object> stopWebcamTask() {
        synchronized (lock) {
            if (process == null || !process.isAlive()) {
                state = TaskState.IDLE;
                appendLog("Stop ignored: detection task not running.");
                return buildStatusResponse();
            }

            state = TaskState.STOPPING;
            appendLog("Stopping detection task...");
            Process current = process;
            current.destroy();

            try {
                if (!current.waitFor(5, TimeUnit.SECONDS)) {
                    appendLog("Force stopping detection task.");
                    current.destroyForcibly();
                    current.waitFor(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                appendLog("Stop interrupted, forcing process termination.");
                current.destroyForcibly();
            } finally {
                process = null;
                startedAt = null;
                state = TaskState.IDLE;
                mode = "IDLE";
                source = "0";
            }

            return buildStatusResponse();
        }
    }

    public Map<String, Object> getStatus() {
        synchronized (lock) {
            if (process != null && !process.isAlive() && state == TaskState.RUNNING) {
                state = TaskState.IDLE;
                process = null;
                startedAt = null;
            }
            return buildStatusResponse();
        }
    }

    public Map<String, Object> getLogs(int tail) {
        synchronized (lock) {
            int safeTail = Math.max(1, Math.min(tail, MAX_LOG_LINES));
            List<String> lines = new ArrayList<>(logBuffer);
            int from = Math.max(0, lines.size() - safeTail);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("lines", lines.subList(from, lines.size()));
            response.put("totalLines", lines.size());
            response.put("tail", safeTail);
            response.put("state", state.name());
            return response;
        }
    }

    private void streamLogs(InputStream inputStream) {
        ioExecutor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLog(line);
                }
            } catch (IOException e) {
                appendLog("Log stream error: " + e.getMessage());
            }
        });
    }

    private void watchProcessExit(Process runningProcess) {
        ioExecutor.submit(() -> {
            try {
                int exitCode = runningProcess.waitFor();
                synchronized (lock) {
                    if (process == runningProcess) {
                        process = null;
                        startedAt = null;
                        if (state != TaskState.STOPPING) {
                            state = exitCode == 0 ? TaskState.IDLE : TaskState.ERROR;
                            if (exitCode != 0) {
                                lastError = "Detection task exited unexpectedly, exitCode=" + exitCode;
                            }
                        } else {
                            state = TaskState.IDLE;
                        }
                        if (state == TaskState.IDLE) {
                            mode = "IDLE";
                            source = "0";
                        }
                    }
                    appendLog("Detection task exited with code: " + exitCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                appendLog("Process monitor interrupted.");
            }
        });
    }

    private Map<String, Object> buildStatusResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("state", state.name());
        response.put("running", process != null && process.isAlive());
        response.put("mode", mode);
        response.put("source", source);
        response.put("location", location);
        response.put("showWindow", showWindow);
        response.put("startedAt", startedAt != null ? startedAt.toString() : null);
        response.put("uptimeSec", startedAt != null ? Duration.between(startedAt, Instant.now()).toSeconds() : 0);
        response.put("lastError", lastError);
        return response;
    }

    private void appendLog(String line) {
        synchronized (lock) {
            logBuffer.addLast(line);
            while (logBuffer.size() > MAX_LOG_LINES) {
                logBuffer.removeFirst();
            }
        }
    }

    private Path findRepoRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path demoScript = current.resolve("backend").resolve("algorithms").resolve("demo.py");
            if (Files.exists(demoScript)) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root containing backend/algorithms/demo.py");
    }

    private Path resolvePythonExecutable(Path repoRoot) {
        Path venvPython = repoRoot.resolve(".venv").resolve("Scripts").resolve("python.exe");
        if (Files.exists(venvPython)) {
            return venvPython;
        }
        return Paths.get("python");
    }

    private ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("detection-task-" + THREAD_COUNTER.getAndIncrement());
            return thread;
        };
    }

    enum TaskState {
        IDLE,
        STARTING,
        RUNNING,
        STOPPING,
        ERROR
    }
}
