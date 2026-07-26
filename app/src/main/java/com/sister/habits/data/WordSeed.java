package com.sister.habits.data;

import com.sister.habits.data.models.Vocabulary;
import java.util.ArrayList;
import java.util.List;

/**
 * 三年级词库种子数据
 * 按主题分类，适合8-9岁小学生
 */
public class WordSeed {

    public static List<Vocabulary> getWords() {
        List<Vocabulary> list = new ArrayList<>();

        // === 动物 ===
        list.add(makeWord("cat", "猫", "/kæt/", "animal", 1));
        list.add(makeWord("dog", "狗", "/dɒɡ/", "animal", 1));
        list.add(makeWord("bird", "鸟", "/bɜːrd/", "animal", 1));
        list.add(makeWord("fish", "鱼", "/fɪʃ/", "animal", 1));
        list.add(makeWord("rabbit", "兔子", "/ˈræbɪt/", "animal", 1));
        list.add(makeWord("pig", "猪", "/pɪɡ/", "animal", 1));
        list.add(makeWord("duck", "鸭子", "/dʌk/", "animal", 1));
        list.add(makeWord("cow", "奶牛", "/kaʊ/", "animal", 1));
        list.add(makeWord("horse", "马", "/hɔːrs/", "animal", 2));
        list.add(makeWord("sheep", "绵羊", "/ʃiːp/", "animal", 2));
        list.add(makeWord("monkey", "猴子", "/ˈmʌŋki/", "animal", 2));
        list.add(makeWord("tiger", "老虎", "/ˈtaɪɡər/", "animal", 2));
        list.add(makeWord("lion", "狮子", "/ˈlaɪən/", "animal", 2));
        list.add(makeWord("panda", "熊猫", "/ˈpændə/", "animal", 1));
        list.add(makeWord("elephant", "大象", "/ˈelɪfənt/", "animal", 2));
        list.add(makeWord("bear", "熊", "/ber/", "animal", 2));

        // === 颜色 ===
        list.add(makeWord("red", "红色", "/red/", "color", 1));
        list.add(makeWord("blue", "蓝色", "/bluː/", "color", 1));
        list.add(makeWord("green", "绿色", "/ɡriːn/", "color", 1));
        list.add(makeWord("yellow", "黄色", "/ˈjeloʊ/", "color", 1));
        list.add(makeWord("white", "白色", "/waɪt/", "color", 1));
        list.add(makeWord("black", "黑色", "/blæk/", "color", 1));
        list.add(makeWord("pink", "粉色", "/pɪŋk/", "color", 1));
        list.add(makeWord("purple", "紫色", "/ˈpɜːrpl/", "color", 2));
        list.add(makeWord("orange", "橙色", "/ˈɔːrɪndʒ/", "color", 2));
        list.add(makeWord("brown", "棕色", "/braʊn/", "color", 2));

        // === 数字 ===
        list.add(makeWord("one", "一", "/wʌn/", "number", 1));
        list.add(makeWord("two", "二", "/tuː/", "number", 1));
        list.add(makeWord("three", "三", "/θriː/", "number", 1));
        list.add(makeWord("four", "四", "/fɔːr/", "number", 1));
        list.add(makeWord("five", "五", "/faɪv/", "number", 1));
        list.add(makeWord("six", "六", "/sɪks/", "number", 1));
        list.add(makeWord("seven", "七", "/ˈsevən/", "number", 1));
        list.add(makeWord("eight", "八", "/eɪt/", "number", 1));
        list.add(makeWord("nine", "九", "/naɪn/", "number", 1));
        list.add(makeWord("ten", "十", "/ten/", "number", 1));

        // === 食物 ===
        list.add(makeWord("apple", "苹果", "/ˈæpl/", "food", 1));
        list.add(makeWord("banana", "香蕉", "/bəˈnænə/", "food", 1));
        list.add(makeWord("bread", "面包", "/bred/", "food", 1));
        list.add(makeWord("milk", "牛奶", "/mɪlk/", "food", 1));
        list.add(makeWord("egg", "鸡蛋", "/eɡ/", "food", 1));
        list.add(makeWord("rice", "米饭", "/raɪs/", "food", 1));
        list.add(makeWord("water", "水", "/ˈwɔːtər/", "food", 1));
        list.add(makeWord("cake", "蛋糕", "/keɪk/", "food", 1));
        list.add(makeWord("candy", "糖果", "/ˈkændi/", "food", 1));
        list.add(makeWord("juice", "果汁", "/dʒuːs/", "food", 1));

        // === 学校 ===
        list.add(makeWord("book", "书", "/bʊk/", "school", 1));
        list.add(makeWord("pen", "钢笔", "/pen/", "school", 1));
        list.add(makeWord("pencil", "铅笔", "/ˈpensl/", "school", 1));
        list.add(makeWord("bag", "书包", "/bæɡ/", "school", 1));
        list.add(makeWord("teacher", "老师", "/ˈtiːtʃər/", "school", 1));
        list.add(makeWord("student", "学生", "/ˈstuːdnt/", "school", 2));
        list.add(makeWord("class", "班级", "/klæs/", "school", 1));
        list.add(makeWord("desk", "书桌", "/desk/", "school", 1));
        list.add(makeWord("chair", "椅子", "/tʃer/", "school", 1));
        list.add(makeWord("door", "门", "/dɔːr/", "school", 1));

        // === 身体 ===
        list.add(makeWord("head", "头", "/hed/", "body", 1));
        list.add(makeWord("hand", "手", "/hænd/", "body", 1));
        list.add(makeWord("eye", "眼睛", "/aɪ/", "body", 1));
        list.add(makeWord("ear", "耳朵", "/ɪr/", "body", 1));
        list.add(makeWord("nose", "鼻子", "/noʊz/", "body", 1));
        list.add(makeWord("mouth", "嘴巴", "/maʊθ/", "body", 1));
        list.add(makeWord("arm", "手臂", "/ɑːrm/", "body", 1));
        list.add(makeWord("leg", "腿", "/leɡ/", "body", 1));
        list.add(makeWord("foot", "脚", "/fʊt/", "body", 2));
        list.add(makeWord("hair", "头发", "/her/", "body", 1));

        // === 家庭 ===
        list.add(makeWord("father", "爸爸", "/ˈfɑːðər/", "family", 2));
        list.add(makeWord("mother", "妈妈", "/ˈmʌðər/", "family", 2));
        list.add(makeWord("brother", "兄弟", "/ˈbrʌðər/", "family", 2));
        list.add(makeWord("sister", "姐妹", "/ˈsɪstər/", "family", 2));
        list.add(makeWord("grandpa", "爷爷/外公", "/ˈɡrænpɑː/", "family", 2));
        list.add(makeWord("grandma", "奶奶/外婆", "/ˈɡrænmɑː/", "family", 2));
        list.add(makeWord("uncle", "叔叔/舅舅", "/ˈʌŋkl/", "family", 2));
        list.add(makeWord("aunt", "阿姨/姑姑", "/ænt/", "family", 2));
        list.add(makeWord("family", "家庭", "/ˈfæməli/", "family", 2));

        // === 天气 ===
        list.add(makeWord("sun", "太阳", "/sʌn/", "weather", 1));
        list.add(makeWord("moon", "月亮", "/muːn/", "weather", 1));
        list.add(makeWord("star", "星星", "/stɑːr/", "weather", 1));
        list.add(makeWord("rain", "雨", "/reɪn/", "weather", 1));
        list.add(makeWord("snow", "雪", "/snoʊ/", "weather", 1));
        list.add(makeWord("cloud", "云", "/klaʊd/", "weather", 1));
        list.add(makeWord("wind", "风", "/wɪnd/", "weather", 1));
        list.add(makeWord("warm", "温暖的", "/wɔːrm/", "weather", 2));
        list.add(makeWord("cold", "冷的", "/koʊld/", "weather", 1));

        // === 常用动词 ===
        list.add(makeWord("run", "跑", "/rʌn/", "verb", 1));
        list.add(makeWord("jump", "跳", "/dʒʌmp/", "verb", 1));
        list.add(makeWord("walk", "走", "/wɔːk/", "verb", 1));
        list.add(makeWord("swim", "游泳", "/swɪm/", "verb", 2));
        list.add(makeWord("sing", "唱歌", "/sɪŋ/", "verb", 1));
        list.add(makeWord("dance", "跳舞", "/dæns/", "verb", 1));
        list.add(makeWord("read", "阅读", "/riːd/", "verb", 1));
        list.add(makeWord("write", "写", "/raɪt/", "verb", 1));
        list.add(makeWord("draw", "画画", "/drɔː/", "verb", 1));
        list.add(makeWord("play", "玩", "/pleɪ/", "verb", 1));
        list.add(makeWord("eat", "吃", "/iːt/", "verb", 1));
        list.add(makeWord("drink", "喝", "/drɪŋk/", "verb", 1));
        list.add(makeWord("sleep", "睡觉", "/sliːp/", "verb", 1));
        list.add(makeWord("wash", "洗", "/wɑːʃ/", "verb", 2));
        list.add(makeWord("clean", "打扫", "/kliːn/", "verb", 2));
        list.add(makeWord("help", "帮助", "/help/", "verb", 1));
        list.add(makeWord("love", "爱", "/lʌv/", "verb", 1));
        list.add(makeWord("like", "喜欢", "/laɪk/", "verb", 1));
        list.add(makeWord("thank", "感谢", "/θæŋk/", "verb", 2));

        return list;
    }

    private static Vocabulary makeWord(String word, String meaning, String phonetic,
                                        String category, int level) {
        Vocabulary v = new Vocabulary();
        v.word = word;
        v.meaning = meaning;
        v.phonetic = phonetic;
        v.category = category;
        v.level = level;
        return v;
    }
}
