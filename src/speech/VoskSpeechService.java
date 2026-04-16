package speech;

import java.io.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional Vosk-based offline STT wrapper. Uses reflection if Vosk binding is present.
 */
public class VoskSpeechService {
    private static final Logger LOGGER = Logger.getLogger(VoskSpeechService.class.getName());
    private final boolean available;
    private final AudioRecorder recorder = new AudioRecorder();
    private static final String AUDIO_FILE = "temp_audio.wav";

    public VoskSpeechService() {
        boolean ok;
        try {
            Class.forName("org.vosk.Model");
            ok = true;
        } catch (ClassNotFoundException e) {
            ok = false;
        }
        this.available = ok;
    }

    public boolean isAvailable() { return available; }
    public boolean isRecording() { return recorder.isRecording(); }
    public void startRecording() { recorder.startRecording(); }

    public void stopAndRecognize(Consumer<String> callback) {
        recorder.stopRecording();
        new Thread(() -> {
            String result = null;
            if (!available) {
                if (callback != null) callback.accept(null);
                return;
            }

            try {
                Class<?> Model = Class.forName("org.vosk.Model");
                Class<?> KaldiRecognizer = Class.forName("org.vosk.KaldiRecognizer");

                Object model = Model.getConstructor(String.class).newInstance("model");
                Object recognizer = KaldiRecognizer.getConstructor(Model, float.class).newInstance(model, 16000.0f);

                try (InputStream ais = new FileInputStream(AUDIO_FILE)) {
                    byte[] buffer = new byte[4096];
                    int n;
                    java.lang.reflect.Method acceptWave = KaldiRecognizer.getMethod("acceptWaveForm", byte[].class, int.class);
                    java.lang.reflect.Method getFinal = KaldiRecognizer.getMethod("getFinalResult");
                    while ((n = ais.read(buffer)) > 0) {
                        acceptWave.invoke(recognizer, buffer, n);
                    }
                    Object finalRes = getFinal.invoke(recognizer);
                    if (finalRes != null) {
                        result = finalRes.toString();
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Vosk recognition failed: " + e.getMessage(), e);
            }

            if (callback != null) callback.accept(result);
        }, "vosk-asr-thread").start();
    }

    // Simple audio recorder
    private static class AudioRecorder {
        private javax.sound.sampled.TargetDataLine line;
        private javax.sound.sampled.AudioFormat format;
        private boolean isRecording = false;
        private Thread recordingThread;

        AudioRecorder() {
            format = new javax.sound.sampled.AudioFormat(16000, 16, 1, true, false);
        }

        void startRecording() {
            javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(javax.sound.sampled.TargetDataLine.class, format);
            if (!javax.sound.sampled.AudioSystem.isLineSupported(info)) {
                LOGGER.warning("不支持的音频格式");
                return;
            }

            try {
                line = (javax.sound.sampled.TargetDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                line.open(format);
                line.start();
                isRecording = true;

                recordingThread = new Thread(() -> {
                    try (javax.sound.sampled.AudioInputStream ais = new javax.sound.sampled.AudioInputStream(line)) {
                        javax.sound.sampled.AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, new File(AUDIO_FILE));
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "录音写入失败: " + e.getMessage(), e);
                    }
                }, "vosk-audio-record-thread");
                recordingThread.start();
            } catch (javax.sound.sampled.LineUnavailableException e) {
                LOGGER.log(Level.WARNING, "音频行不可用: " + e.getMessage(), e);
            }
        }

        void stopRecording() {
            if (isRecording && line != null) {
                isRecording = false;
                line.stop();
                line.close();
                try {
                    if (recordingThread != null) recordingThread.join();
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "录音线程中断: " + e.getMessage(), e);
                }
            }
        }

        boolean isRecording() {
            return isRecording;
        }
    }
}
