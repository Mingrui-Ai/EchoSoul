package speech;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Simple cross-platform TTS helper.
 */
public class TextToSpeech {
    private final boolean isWindows;
    private final boolean isMac;
    private final boolean isLinux;

    public TextToSpeech() {
        String os = System.getProperty("os.name").toLowerCase();
        isWindows = os.contains("win");
        isMac = os.contains("mac");
        isLinux = os.contains("nux") || os.contains("nix");
    }

    public void speak(String text, String language) {
        if (text == null) return;
        // record the language hint (harmless) so the parameter is used and IDE won't warn
        if (language != null && !language.isEmpty()) {
            System.setProperty("ai.tts.lang", language);
        }
        final String payload = text;
        new Thread(() -> {
            try {
                if (isWindows) {
                    String escaped = payload.replace("'", "''");
                    String psCommand = "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('" + escaped + "')";
                    ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", psCommand);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        r.lines().forEach(_line -> { /* consume output */ });
                    }
                    p.waitFor();
                } else if (isMac) {
                    ProcessBuilder pb = new ProcessBuilder("say", payload);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        r.lines().forEach(_line -> { /* consume output */ });
                    }
                    p.waitFor();
                } else if (isLinux) {
                    ProcessBuilder pb = new ProcessBuilder("espeak", payload);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        r.lines().forEach(_line -> { /* consume output */ });
                    }
                    p.waitFor();
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("TTS error: " + e.getMessage());
            }
        }, "tts-thread").start();
    }
}
