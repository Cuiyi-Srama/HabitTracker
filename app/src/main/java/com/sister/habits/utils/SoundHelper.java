package com.sister.habits.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * 音效 + TTS朗读 + 震动 统一管理
 * 零额外资源依赖，纯代码实现
 */
public class SoundHelper {

    private static final String TAG = "SoundHelper";

    private TextToSpeech tts;
    private SoundPool soundPool;
    private Vibrator vibrator;
    private boolean ttsReady = false;
    private boolean soundEnabled = true;
    private boolean ttsEnabled = true;
    private float ttsSpeed = 1.0f; // 1.0=正常, 0.5=慢速

    // 预加载音效ID（用 ToneGenerator 替代 SoundPool 的加载声音，避免依赖音频文件）
    private final ToneGenerator toneGenerator;

    public SoundHelper(Context context) {
        // TTS 朗读
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                ttsReady = (result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED);
                Log.d(TAG, "TTS " + (ttsReady ? "就绪" : "不支持"));
            }
        });

        // SoundPool（保留以备将来使用音频资源）
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(attrs)
                .build();

        // ToneGenerator 用于简单提示音
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);

        // 震动服务
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * TTS朗读单词
     */
    public void speakWord(String word) {
        speakWord(word, ttsSpeed);
    }

    public void speakWord(String word, float speed) {
        if (!ttsEnabled || !ttsReady || tts == null) return;
        try {
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_RATE, speed);
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, params, null);
        } catch (Exception e) {
            Log.e(TAG, "TTS朗读失败: " + word, e);
        }
    }

    /**
     * 答对音效 + 震动
     */
    public void playCorrectSound() {
        if (!soundEnabled) return;
        try {
            // 用 ToneGenerator 播放一个欢快的双音提示（类似叮咚）
            toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 150);
            vibrate(50);
        } catch (Exception e) {
            Log.e(TAG, "播放答对音效失败", e);
        }
    }

    /**
     * 点击按钮音效 + 震动
     */
    public void playClickSound() {
        if (!soundEnabled) return;
        try {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 80);
            vibrate(20);
        } catch (Exception e) {
            Log.e(TAG, "播放点击音效失败", e);
        }
    }

    /**
     * 连击奖励音效（更响亮，更愉悦）
     */
    public void playStreakSound(int count) {
        if (!soundEnabled) return;
        try {
            if (count >= 5) {
                toneGenerator.startTone(ToneGenerator.TONE_SUP_RINGTONE, 300);
                vibrate(100);
            } else {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 200);
                vibrate(60);
            }
        } catch (Exception e) {
            Log.e(TAG, "播放连击音效失败", e);
        }
    }

    /**
     * 打卡完成音效
     */
    public void playCheckInSound() {
        if (!soundEnabled) return;
        try {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400);
            // 节奏震动：短-短-长
            vibratePattern(new long[]{0, 50, 100, 50, 100, 150});
        } catch (Exception e) {
            Log.e(TAG, "播放打卡音效失败", e);
        }
    }

    /**
     * 简单震动
     */
    public void vibrate(long ms) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Exception e) {
            Log.e(TAG, "震动失败", e);
        }
    }

    /**
     * 节奏震动
     */
    public void vibratePattern(long[] pattern) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception e) {
            Log.e(TAG, "节奏震动失败", e);
        }
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    public void setTtsEnabled(boolean enabled) {
        this.ttsEnabled = enabled;
    }

    public void setTtsSpeed(float speed) {
        this.ttsSpeed = speed;
    }

    /**
     * 释放资源
     */
    public void shutdown() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
            if (soundPool != null) {
                soundPool.release();
            }
            if (toneGenerator != null) {
                toneGenerator.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "释放资源失败", e);
        }
    }
}