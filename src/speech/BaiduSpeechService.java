package speech;

import service.ConfigManager;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import com.baidu.aip.speech.AipSpeech;
import com.baidu.aip.speech.TtsResponse;
import org.json.JSONObject;

import javax.sound.sampled.*;
import java.io.*;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Baidu speech service using the official SDK (direct dependency). Requires the Baidu SDK jar on classpath.
 */
public class BaiduSpeechService {
    private static final Logger LOGGER = Logger.getLogger(BaiduSpeechService.class.getName());

    private final String appId;
    private final String apiKey;
    private final String secretKey;

    private static final String AUDIO_FILE_PATH = "temp_audio.wav";
    private static final String RESPONSE_AUDIO_PATH = "response_audio.mp3";

    private final AipSpeech client;
    private final AudioRecorder recorder = new AudioRecorder();

    public BaiduSpeechService() throws Exception {
        this.appId = ConfigManager.getInstance().getBaiduAppId();
        this.apiKey = ConfigManager.getInstance().getBaiduApiKey();
        this.secretKey = ConfigManager.getInstance().getBaiduSecretKey();

        AipSpeech tmp = null;
        if (apiKey == null || apiKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            LOGGER.warning("Baidu credentials not configured; Baidu speech disabled. Set baidu.app.id, baidu.api.key and baidu.secret.key in config.properties or via Settings.");
        } else {
            try {
                tmp = new AipSpeech(appId, apiKey, secretKey);
                tmp.setConnectionTimeoutInMillis(2000);
                tmp.setSocketTimeoutInMillis(60000);
                // do not run a network test here to avoid blocking startup
            } catch (NoSuchMethodError nsme) {
                LOGGER.log(Level.WARNING, "Baidu SDK appears incompatible (method signature mismatch). Please verify the Baidu SDK jar version in lib/. Error: " + nsme.getMessage(), nsme);
                tmp = null;
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Failed to initialize Baidu speech client: " + t.getMessage(), t);
                tmp = null;
            }
        }
        client = tmp;
    }

    public boolean isAvailable() {
        return client != null;
    }

    public boolean isRecording() {
        return recorder.isRecording();
    }

    public void startRecording() {
        recorder.startRecording();
    }

    public void stopAndRecognize(Consumer<String> callback) {
        recorder.stopRecording();
        new Thread(() -> {
            try {
                if (client == null) {
                    LOGGER.warning("Baidu client not initialized; cannot perform ASR");
                    if (callback != null) Platform.runLater(() -> callback.accept(null));
                    return;
                }

                JSONObject res = null;
                try {
                    // first try using filepath-based call (some SDK versions support this)
                    res = client.asr(AUDIO_FILE_PATH, "wav", 16000, null);
                } catch (NoSuchMethodError | IllegalArgumentException e) {
                    // fallback: read file bytes and call byte[] signature
                    try {
                        byte[] data = readFileToBytes(AUDIO_FILE_PATH);
                        if (data != null) {
                            res = client.asr(data, "wav", 16000, null);
                        }
                    } catch (Throwable t) {
                        LOGGER.log(Level.WARNING, "Fallback ASR by bytes failed: " + t.getMessage(), t);
                    }
                }

                String txt = null;
                if (res != null) {
                    if (res.has("result")) {
                        txt = res.getJSONArray("result").getString(0);
                    } else if (res.has("error")) {
                        LOGGER.warning("Baidu ASR returned error: " + res.toString());
                    }
                }
                final String out = txt;
                if (callback != null) Platform.runLater(() -> callback.accept(out));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Baidu ASR failed: " + e.getMessage(), e);
                if (callback != null) Platform.runLater(() -> callback.accept(null));
            }
        }, "baidu-asr-thread").start();
    }

    private static byte[] readFileToBytes(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            try (FileInputStream fis = new FileInputStream(f); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int r;
                while ((r = fis.read(buf)) != -1) baos.write(buf, 0, r);
                return baos.toByteArray();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "读取音频文件失败: " + e.getMessage(), e);
            return null;
        }
    }

    public boolean textToSpeech(String text, String outputPath) {
        try {
            HashMap<String, Object> options = new HashMap<>();
            options.put("spd", 6);
            options.put("per", 4);
            options.put("pit", 6);
            options.put("vol", 7);
            try { options.put("emotion", "happiness"); } catch (Exception ignore) {}

            TtsResponse res = client.synthesis(text, "zh", 1, options);
            byte[] data = res.getData();
            if (data != null) {
                try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                    fos.write(data);
                }
                return true;
            } else {
                if (res.getResult() != null) {
                    LOGGER.warning("Baidu TTS returned no data: " + res.getResult());
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Baidu TTS failed: " + e.getMessage(), e);
        }
        return false;
    }

    public void speakAndPlay(String text) {
        new Thread(() -> {
            boolean ok = textToSpeech(text, RESPONSE_AUDIO_PATH);
            if (ok) {
                Platform.runLater(() -> {
                    try {
                        Media media = new Media(new File(RESPONSE_AUDIO_PATH).toURI().toString());
                        MediaPlayer player = new MediaPlayer(media);
                        player.play();
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "播放合成音频失败: " + e.getMessage(), e);
                    }
                });
            }
        }, "baidu-tts-thread").start();
    }

    // Simple audio recorder (mono 16kHz PCM WAV)
    private static class AudioRecorder {
        private TargetDataLine line;
        private AudioFormat format;
        private boolean isRecording = false;
        private Thread recordingThread;

        AudioRecorder() {
            format = new AudioFormat(16000, 16, 1, true, false);
        }

        void startRecording() {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.warning("音频格式不被支持");
                return;
            }

            try {
                line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();
                isRecording = true;

                recordingThread = new Thread(() -> {
                    try (AudioInputStream ais = new AudioInputStream(line)) {
                        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(AUDIO_FILE_PATH));
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "录音写入失败: " + e.getMessage(), e);
                    }
                }, "audio-record-thread");
                recordingThread.start();
            } catch (LineUnavailableException e) {
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
