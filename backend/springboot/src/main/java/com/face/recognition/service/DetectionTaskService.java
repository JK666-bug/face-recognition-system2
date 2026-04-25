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
import java.util.Comparator;
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
    private static final AtomicLong TASK_COUNTER = new AtomicLong(1);

    private final Object lock = new Object();
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2, daemonThreadFactory());
    private final Map<String, TaskRuntime> taskRuntimes = new LinkedHashMap<>();

    public Map<String, Object> startWebcamTask(String requestedLocation, boolean showWindow) {
        return startTask("webcam", "0", requestedLocation, showWindow);
    }

    public Map<String, Object> startStreamTask(String streamUrl, String requestedLocation, boolean showWindow) {
        String safeSource = (streamUrl == null || streamUrl.isBlank()) ? "" : streamUrl.trim();
        if (safeSource.isEmpty()) {
            return Map.of(
                    "state", "ERROR",
                    "running", false,
                    "lastError", "Stream URL cannot be empty."
            );
        }
        return startTask("stream", safeSource, requestedLocation, showWindow);
    }

    private Map<String, Object> startTask(String taskMode, String taskSource, String requestedLocation, boolean requestedShowWindow) {
        synchronized (lock) {
            String safeLocation = (requestedLocation == null || requestedLocation.isBlank())
                    ? "meetingroom"
                    : requestedLocation.trim();
            String taskId = "task-" + TASK_COUNTER.getAndIncrement();
            TaskRuntime taskRuntime = new TaskRuntime(taskId, taskMode, taskSource, safeLocation, requestedShowWindow);
            taskRuntime.state = TaskState.STARTING;
            taskRuntimes.put(taskId, taskRuntime);
            appendLog(taskRuntime, "Starting " + taskMode + " task at location: " + safeLocation + ", source: " + taskSource);

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

                taskRuntime.process = pb.start();
                taskRuntime.startedAt = Instant.now();
                taskRuntime.state = TaskState.RUNNING;

                appendLog(taskRuntime, "Task started. mode=" + taskMode + ", showWindow=" + requestedShowWindow);
                streamLogs(taskRuntime, taskRuntime.process.getInputStream());
                watchProcessExit(taskRuntime);
            } catch (Exception e) {
                taskRuntime.state = TaskState.ERROR;
                taskRuntime.lastError = "Failed to start detection task: " + e.getMessage();
                appendLog(taskRuntime, taskRuntime.lastError);
                log.error("Failed to start detection task", e);
            }

            return buildTaskResponse(taskRuntime);
        }
    }

    public Map<String, Object> stopWebcamTask(String taskId) {
        synchronized (lock) {
            TaskRuntime taskRuntime = taskRuntimes.get(taskId);
            if (taskRuntime == null) {
                return Map.of("error", "Task not found", "taskId", taskId);
            }
            if (taskRuntime.process == null || !taskRuntime.process.isAlive()) {
                taskRuntime.state = TaskState.IDLE;
                appendLog(taskRuntime, "Stop ignored: detection task not running.");
                return buildTaskResponse(taskRuntime);
            }

            taskRuntime.state = TaskState.STOPPING;
            appendLog(taskRuntime, "Stopping detection task...");
            Process current = taskRuntime.process;
            current.destroy();

            try {
                if (!current.waitFor(5, TimeUnit.SECONDS)) {
                    appendLog(taskRuntime, "Force stopping detection task.");
                    current.destroyForcibly();
                    current.waitFor(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                appendLog(taskRuntime, "Stop interrupted, forcing process termination.");
                current.destroyForcibly();
            } finally {
                taskRuntime.process = null;
                taskRuntime.startedAt = null;
                taskRuntime.state = TaskState.IDLE;
            }

            return buildTaskResponse(taskRuntime);
        }
    }

    public List<Map<String, Object>> listTasks() {
        synchronized (lock) {
            List<Map<String, Object>> response = new ArrayList<>();
            for (TaskRuntime taskRuntime : taskRuntimes.values()) {
                if (taskRuntime.process != null && !taskRuntime.process.isAlive() && taskRuntime.state == TaskState.RUNNING) {
                    taskRuntime.state = TaskState.IDLE;
                    taskRuntime.process = null;
                    taskRuntime.startedAt = null;
                }
                response.add(buildTaskResponse(taskRuntime));
            }
            response.sort(Comparator.comparing(
                    item -> (String) item.getOrDefault("startedAt", ""),
                    Comparator.nullsLast(Comparator.reverseOrder())
            ));
            return response;
        }
    }

    public Map<String, Object> getTaskStatus(String taskId) {
        synchronized (lock) {
            TaskRuntime taskRuntime = taskRuntimes.get(taskId);
            if (taskRuntime == null) {
                return Map.of("error", "Task not found", "taskId", taskId);
            }
            return buildTaskResponse(taskRuntime);
        }
    }

    public Map<String, Object> getLogs(String taskId, int tail) {
        synchronized (lock) {
            TaskRuntime taskRuntime = taskRuntimes.get(taskId);
            if (taskRuntime == null) {
                return Map.of("error", "Task not found", "taskId", taskId, "lines", List.of());
            }
            int safeTail = Math.max(1, Math.min(tail, MAX_LOG_LINES));
            List<String> lines = new ArrayList<>(taskRuntime.logBuffer);
            int from = Math.max(0, lines.size() - safeTail);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("taskId", taskId);
            response.put("lines", lines.subList(from, lines.size()));
            response.put("totalLines", lines.size());
            response.put("tail", safeTail);
            response.put("state", taskRuntime.state.name());
            return response;
        }
    }

    public Map<String, Object> deleteTask(String taskId) {
        synchronized (lock) {
            TaskRuntime taskRuntime = taskRuntimes.get(taskId);
            if (taskRuntime == null) {
                return Map.of("error", "Task not found", "taskId", taskId);
            }
            if (taskRuntime.process != null && taskRuntime.process.isAlive()) {
                return Map.of("error", "Task is running. Stop it before deletion.", "taskId", taskId);
            }
            taskRuntimes.remove(taskId);
            return Map.of("deleted", true, "taskId", taskId);
        }
    }

    private void streamLogs(TaskRuntime taskRuntime, InputStream inputStream) {
        ioExecutor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLog(taskRuntime, line);
                }
            } catch (IOException e) {
                appendLog(taskRuntime, "Log stream error: " + e.getMessage());
            }
        });
    }

    private void watchProcessExit(TaskRuntime taskRuntime) {
        ioExecutor.submit(() -> {
            try {
                Process runningProcess = taskRuntime.process;
                if (runningProcess == null) {
                    return;
                }
                int exitCode = runningProcess.waitFor();
                synchronized (lock) {
                    if (taskRuntime.process == runningProcess) {
                        taskRuntime.process = null;
                        taskRuntime.startedAt = null;
                        if (taskRuntime.state != TaskState.STOPPING) {
                            taskRuntime.state = exitCode == 0 ? TaskState.IDLE : TaskState.ERROR;
                            if (exitCode != 0) {
                                taskRuntime.lastError = "Detection task exited unexpectedly, exitCode=" + exitCode;
                            }
                        } else {
                            taskRuntime.state = TaskState.IDLE;
                        }
                    }
                    appendLog(taskRuntime, "Detection task exited with code: " + exitCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                appendLog(taskRuntime, "Process monitor interrupted.");
            }
        });
    }

    private Map<String, Object> buildTaskResponse(TaskRuntime taskRuntime) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskRuntime.taskId);
        response.put("state", taskRuntime.state.name());
        response.put("running", taskRuntime.process != null && taskRuntime.process.isAlive());
        response.put("mode", taskRuntime.mode);
        response.put("source", taskRuntime.source);
        response.put("location", taskRuntime.location);
        response.put("showWindow", taskRuntime.showWindow);
        response.put("startedAt", taskRuntime.startedAt != null ? taskRuntime.startedAt.toString() : null);
        response.put("uptimeSec", taskRuntime.startedAt != null ? Duration.between(taskRuntime.startedAt, Instant.now()).toSeconds() : 0);
        response.put("lastError", taskRuntime.lastError);
        return response;
    }

    private void appendLog(TaskRuntime taskRuntime, String line) {
        synchronized (lock) {
            taskRuntime.logBuffer.addLast(line);
            while (taskRuntime.logBuffer.size() > MAX_LOG_LINES) {
                taskRuntime.logBuffer.removeFirst();
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

    private static final class TaskRuntime {
        private final String taskId;
        private final String mode;
        private final String source;
        private final String location;
        private final boolean showWindow;
        private final Deque<String> logBuffer = new ArrayDeque<>();
        private Process process;
        private TaskState state = TaskState.IDLE;
        private Instant startedAt;
        private String lastError;

        private TaskRuntime(String taskId, String mode, String source, String location, boolean showWindow) {
            this.taskId = taskId;
            this.mode = mode;
            this.source = source;
            this.location = location;
            this.showWindow = showWindow;
        }
    }
}
