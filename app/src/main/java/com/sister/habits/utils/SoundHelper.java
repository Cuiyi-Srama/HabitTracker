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
                Log.w(TAG, "TTS " + (ttsReady ? "就绪" : "不支持"));
            } else {
                Log.w(TAG, "TTS 初始化失败, status=" + status);
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
        Log.w(TAG, "ToneGenerator 创建完成, stream=NOTIFICATION, volume=85");

        // 震动服务
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        Log.w(TAG, "Vibrator 获取状态: " + (vibrator != null ? "非空" : "NULL"));
        if (vibrator != null) {
            Log.w(TAG, "hasVibrator: " + vibrator.hasVibrator());
        }
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
            tts.setSpeechRate(speed);
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, null);
        } catch (Exception e) {
            Log.e(TAG, "TTS朗读失败: " + word, e);
        }
    }

    /**
     * 答对音效 + 愉悦确认震动
     * 手感：短促有力的确认感（30ms 单震）
     */
    public void playCorrectSound() {
        if (!soundEnabled) return;
        try {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 150);
            vibrate(30, 255);
        } catch (Exception e) {
            Log.e(TAG, "播放答对音效失败", e);
        }
    }

    /**
     * 按钮点击震动——极短点触感
     * 手感：干脆利落（12ms，类似 iOS light impact）
     */
    public void playClickSound() {
        if (!soundEnabled) return;
        try {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 80);
            vibrate(12, 255);
        } catch (Exception e) {
            Log.e(TAG, "播放点击音效失败", e);
        }
    }

    /**
     * Tab 切换震动——超轻点触
     * 手感：最轻最柔（8ms，几乎感觉不到但增加质感）
     */
    public void playTabClickSound() {
        if (!soundEnabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(8, 200));
            } else {
                vibrator.vibrate(8);
            }
        } catch (Exception e) {
            Log.e(TAG, "Tab点击震动失败", e);
        }
    }

    /**
     * 答错反馈震动——轻柔双震提醒
     * 手感：两次极短震动（类似 gently tap on shoulder），不惩罚不焦虑
     */
    public void playErrorVibration() {
        if (!soundEnabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 10, 30, 10}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 10, 30, 10}, -1);
            }
        } catch (Exception e) {
            Log.e(TAG, "错误反馈震动失败", e);
        }
    }

    /**
     * 连击奖励音效 + 递增强度的震动
     * 手感：连击数越高震动越强
     */
    public void playStreakSound(int count) {
        if (!soundEnabled) return;
        try {
            if (count >= 10) {
                toneGenerator.startTone(ToneGenerator.TONE_SUP_RINGTONE, 400);
                // 10连：强确认感（两次增强震）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 30, 50, 50}, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 30, 50, 50}, -1);
                }
            } else if (count >= 5) {
                toneGenerator.startTone(ToneGenerator.TONE_SUP_RINGTONE, 300);
                // 5连：愉悦的渐强
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 15, 30, 25}, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 15, 30, 25}, -1);
                }
            } else {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 200);
                vibrate(20, 255);
            }
        } catch (Exception e) {
            Log.e(TAG, "播放连击音效失败", e);
        }
    }

    /**
     * 打卡完成音效 + 庆祝节奏震动
     * 手感：渐强三连震（完成任务的成就感）
     */
    public void playCheckInSound() {
        if (!soundEnabled) return;
        try {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 20, 30, 30, 50, 50}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 20, 30, 30, 50, 50}, -1);
            }
        } catch (Exception e) {
            Log.e(TAG, "播放打卡音效失败", e);
        }
    }

    /**
     * 简单震动（指定振幅）
     */
    public void vibrate(long ms, int amplitude) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Exception e) {
            Log.e(TAG, "震动失败", e);
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