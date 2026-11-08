package com.face.recognition.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 仓库根目录。IDE 经常把工作目录设在 backend/springboot，阈值文件和虚拟环境都在上两级。
 */
public final class ProjectRoot {

    private ProjectRoot() {
    }

    public static Path resolve() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            Path script = dir.resolve("backend").resolve("algorithms").resolve("extract_cli.py");
            if (Files.isRegularFile(script)) {
                return dir;
            }
            dir = dir.getParent();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    }

    public static String pythonExecutable(Path projectRoot) {
        Path venv = projectRoot.resolve(".venv");
        Path[] candidates = new Path[] {
                venv.resolve("Scripts").resolve("python.exe"),
                venv.resolve("bin").resolve("python"),
                venv.resolve("bin").resolve("python3")
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "python" : "python3";
    }
}
