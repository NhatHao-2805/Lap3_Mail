package client;

import javax.sound.sampled.*;
import java.io.*;

public class AudioRecorder {
    private AudioFormat format;
    private TargetDataLine targetLine;
    private ByteArrayOutputStream recordStream;
    private boolean isRecording = false;

    public AudioRecorder() {
        // 16kHz, 16bit, mono, signed, little-endian
        format = new AudioFormat(16000, 16, 1, true, false);
    }

    public synchronized void startRecording() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Microphone không được hỗ trợ trên thiết bị này.");
        }

        targetLine = (TargetDataLine) AudioSystem.getLine(info);
        targetLine.open(format);
        targetLine.start();

        recordStream = new ByteArrayOutputStream();
        isRecording = true;

        new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (isRecording) {
                int count = targetLine.read(buffer, 0, buffer.length);
                if (count > 0) {
                    recordStream.write(buffer, 0, count);
                }
            }
        }).start();
    }

    public synchronized byte[] stopRecording() {
        isRecording = false;
        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }

        byte[] pcmData = (recordStream != null) ? recordStream.toByteArray() : new byte[0];
        if (pcmData.length == 0) {
            // Generate fallback tone if no mic input detected
            return generateBeepSound();
        }

        // Convert PCM to standard WAV with header
        return createWavFile(pcmData, format);
    }

    public static void playAudio(byte[] wavBytes) {
        new Thread(() -> {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(wavBytes);
                 AudioInputStream ais = AudioSystem.getAudioInputStream(bais)) {
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                clip.start();
            } catch (Exception e) {
                System.err.println("[Audio] Không thể phát âm thanh: " + e.getMessage());
            }
        }).start();
    }

    private static byte[] createWavFile(byte[] pcmData, AudioFormat format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int totalAudioLen = pcmData.length;
        int totalDataLen = totalAudioLen + 36;
        long longSampleRate = (long) format.getSampleRate();
        int channels = format.getChannels();
        long byteRate = (long) format.getSampleRate() * channels * format.getSampleSizeInBits() / 8;

        try {
            // WAV Header
            out.write(new byte[]{'R', 'I', 'F', 'F'});
            out.write(intToByteArray(totalDataLen));
            out.write(new byte[]{'W', 'A', 'V', 'E'});
            out.write(new byte[]{'f', 'm', 't', ' '});
            out.write(intToByteArray(16)); // SubChunk1Size (16 for PCM)
            out.write(shortToByteArray((short) 1)); // AudioFormat (1 for PCM)
            out.write(shortToByteArray((short) channels));
            out.write(intToByteArray((int) longSampleRate));
            out.write(intToByteArray((int) byteRate));
            out.write(shortToByteArray((short) (channels * format.getSampleSizeInBits() / 8))); // BlockAlign
            out.write(shortToByteArray((short) format.getSampleSizeInBits()));
            out.write(new byte[]{'d', 'a', 't', 'a'});
            out.write(intToByteArray(totalAudioLen));
            out.write(pcmData);
        } catch (IOException e) {
            return pcmData;
        }
        return out.toByteArray();
    }

    public static byte[] generateBeepSound() {
        int sampleRate = 16000;
        int durationSec = 1;
        int numSamples = sampleRate * durationSec;
        byte[] pcm = new byte[numSamples * 2];
        double freq = 440.0; // A4 tone

        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * i / (sampleRate / freq);
            short sample = (short) (Math.sin(angle) * 32767 * 0.6);
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return createWavFile(pcm, new AudioFormat(sampleRate, 16, 1, true, false));
    }

    private static byte[] intToByteArray(int data) {
        return new byte[]{
                (byte) (data & 0xff),
                (byte) ((data >> 8) & 0xff),
                (byte) ((data >> 16) & 0xff),
                (byte) ((data >> 24) & 0xff)
        };
    }

    private static byte[] shortToByteArray(short data) {
        return new byte[]{
                (byte) (data & 0xff),
                (byte) ((data >> 8) & 0xff)
        };
    }
}
