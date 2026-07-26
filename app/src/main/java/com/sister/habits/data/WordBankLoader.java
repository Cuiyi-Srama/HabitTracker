package com.sister.habits.data;

import com.sister.habits.data.models.Vocabulary;
import java.util.ArrayList;
import java.util.List;

/**
 * 全阶段词库——小学1-6年级 + 初中 + 常用短语
 * 按年级和分类组织，家长可自由开关
 */
public class WordBankLoader {

    public static List&lt;Vocabulary&gt; getAllWords() {
        List&lt;Vocabulary&gt; list = new ArrayList&lt;&gt;();

        // ==================== 小学一年级 ====================
        String g1 = "grade1";
        list.add(make(g1, "animal", "cat", "猫", "/kæt/", 1));
        list.add(make(g1, "animal", "dog", "狗", "/dɒɡ/", 1));
        list.add(make(g1, "animal", "duck", "鸭子", "/dʌk/", 1));
        list.add(make(g1, "animal", "pig", "猪", "/pɪɡ/", 1));
        list.add(make(g1, "animal", "bird", "鸟", "/bɜːd/", 1));
        list.add(make(g1, "animal", "fish", "鱼", "/fɪʃ/", 1));
        list.add(make(g1, "animal", "hen", "母鸡", "/hen/", 1));
        list.add(make(g1, "animal", "cow", "牛", "/kaʊ/", 1));
        list.add(make(g1, "color", "red", "红色", "/red/", 1));
        list.add(make(g1, "color", "blue", "蓝色", "/bluː/", 1));
        list.add(make(g1, "color", "green", "绿色", "/ɡriːn/", 1));
        list.add(make(g1, "color", "yellow", "黄色", "/ˈjeləʊ/", 1));
        list.add(make(g1, "color", "white", "白色", "/waɪt/", 1));
        list.add(make(g1, "color", "black", "黑色", "/blæk/", 1));
        list.add(make(g1, "number", "one", "一", "/wʌn/", 1));
        list.add(make(g1, "number", "two", "二", "/tuː/", 1));
        list.add(make(g1, "number", "three", "三", "/θriː/", 1));
        list.add(make(g1, "number", "four", "四", "/fɔː/", 1));
        list.add(make(g1, "number", "five", "五", "/faɪv/", 1));
        list.add(make(g1, "number", "six", "六", "/sɪks/", 1));
        list.add(make(g1, "number", "seven", "七", "/ˈsev.ən/", 1));
        list.add(make(g1, "number", "eight", "八", "/eɪt/", 1));
        list.add(make(g1, "number", "nine", "九", "/naɪn/", 1));
        list.add(make(g1, "number", "ten", "十", "/ten/", 1));
        list.add(make(g1, "school", "book", "书", "/bʊk/", 1));
        list.add(make(g1, "school", "pen", "钢笔", "/pen/", 1));
        list.add(make(g1, "school", "bag", "书包", "/bæɡ/", 1));
        list.add(make(g1, "school", "desk", "书桌", "/desk/", 1));
        list.add(make(g1, "school", "chair", "椅子", "/tʃeə/", 1));
        list.add(make(g1, "body", "eye", "眼睛", "/aɪ/", 1));
        list.add(make(g1, "body", "ear", "耳朵", "/ɪə/", 1));
        list.add(make(g1, "body", "nose", "鼻子", "/nəʊz/", 1));
        list.add(make(g1, "body", "mouth", "嘴巴", "/maʊθ/", 1));
        list.add(make(g1, "body", "head", "头", "/hed/", 1));
        list.add(make(g1, "body", "hand", "手", "/hænd/", 1));
        list.add(make(g1, "body", "foot", "脚", "/fʊt/", 1));
        list.add(make(g1, "food", "rice", "米饭", "/raɪs/", 1));
        list.add(make(g1, "food", "milk", "牛奶", "/mɪlk/", 1));
        list.add(make(g1, "food", "egg", "鸡蛋", "/eɡ/", 1));
        list.add(make(g1, "food", "bread", "面包", "/bred/", 1));
        list.add(make(g1, "food", "water", "水", "/ˈwɔːtə/", 1));
        list.add(make(g1, "family", "father", "爸爸", "/ˈfɑːðə/", 1));
        list.add(make(g1, "family", "mother", "妈妈", "/ˈmʌðə/", 1));
        list.add(make(g1, "family", "brother", "兄弟", "/ˈbrʌðə/", 1));
        list.add(make(g1, "family", "sister", "姐妹", "/ˈsɪstə/", 1));
        list.add(make(g1, "family", "baby", "婴儿", "/ˈbeɪbi/", 1));

        // ==================== 小学二年级 ====================
        String g2 = "grade2";
        list.add(make(g2, "animal", "rabbit", "兔子", "/ˈræbɪt/", 1));
        list.add(make(g2, "animal", "horse", "马", "/hɔːs/", 1));
        list.add(make(g2, "animal", "sheep", "绵羊", "/ʃiːp/", 1));
        list.add(make(g2, "animal", "monkey", "猴子", "/ˈmʌŋki/", 1));
        list.add(make(g2, "animal", "panda", "熊猫", "/ˈpændə/", 1));
        list.add(make(g2, "animal", "tiger", "老虎", "/ˈtaɪɡə/", 1));
        list.add(make(g2, "animal", "lion", "狮子", "/ˈlaɪən/", 1));
        list.add(make(g2, "animal", "elephant", "大象", "/ˈelɪfənt/", 1));
        list.add(make(g2, "school", "teacher", "老师", "/ˈtiːtʃə/", 1));
        list.add(make(g2, "school", "student", "学生", "/ˈstjuːdənt/", 1));
        list.add(make(g2, "school", "classroom", "教室", "/ˈklɑːsruːm/", 1));
        list.add(make(g2, "school", "pencil", "铅笔", "/ˈpensəl/", 1));
        list.add(make(g2, "school", "ruler", "尺子", "/ˈruːlə/", 1));
        list.add(make(g2, "school", "eraser", "橡皮", "/ɪˈreɪzə/", 1));
        list.add(make(g2, "food", "apple", "苹果", "/ˈæpəl/", 1));
        list.add(make(g2, "food", "banana", "香蕉", "/bəˈnɑːnə/", 1));
        list.add(make(g2, "food", "orange", "橙子", "/ˈɒrɪndʒ/", 1));
        list.add(make(g2, "food", "grape", "葡萄", "/ɡreɪp/", 1));
        list.add(make(g2, "food", "cake", "蛋糕", "/keɪk/", 1));
        list.add(make(g2, "food", "candy", "糖果", "/ˈkændi/", 1));
        list.add(make(g2, "food", "cookie", "饼干", "/ˈkʊki/", 1));
        list.add(make(g2, "body", "arm", "手臂", "/ɑːm/", 1));
        list.add(make(g2, "body", "leg", "腿", "/leɡ/", 1));
        list.add(make(g2, "body", "hair", "头发", "/heə/", 1));
        list.add(make(g2, "body", "face", "脸", "/feɪs/", 1));
        list.add(make(g2, "clothes", "hat", "帽子", "/hæt/", 1));
        list.add(make(g2, "clothes", "shirt", "衬衫", "/ʃɜːt/", 1));
        list.add(make(g2, "clothes", "shoes", "鞋子", "/ʃuːz/", 1));
        list.add(make(g2, "clothes", "coat", "外套", "/kəʊt/", 1));
        list.add(make(g2, "clothes", "dress", "连衣裙", "/dres/", 1));
        list.add(make(g2, "verb", "run", "跑", "/rʌn/", 1));
        list.add(make(g2, "verb", "jump", "跳", "/dʒʌmp/", 1));
        list.add(make(g2, "verb", "walk", "走", "/wɔːk/", 1));
        list.add(make(g2, "verb", "swim", "游泳", "/swɪm/", 1));
        list.add(make(g2, "verb", "sing", "唱歌", "/sɪŋ/", 1));
        list.add(make(g2, "verb", "dance", "跳舞", "/dɑːns/", 1));
        list.add(make(g2, "verb", "read", "阅读", "/riːd/", 1));
        list.add(make(g2, "verb", "write", "写", "/raɪt/", 1));
        list.add(make(g2, "weather", "sunny", "晴朗的", "/ˈsʌni/", 1));
        list.add(make(g2, "weather", "rainy", "下雨的", "/ˈreɪni/", 1));
        list.add(make(g2, "weather", "cloudy", "多云的", "/ˈklaʊdi/", 1));
        list.add(make(g2, "weather", "windy", "有风的", "/ˈwɪndi/", 1));
        list.add(make(g2, "weather", "snowy", "下雪的", "/ˈsnəʊi/", 1));

        // ==================== 小学三年级 ====================
        String g3 = "grade3";
        list.add(make(g3, "animal", "mouse", "老鼠", "/maʊs/", 2));
        list.add(make(g3, "animal", "snake", "蛇", "/sneɪk/", 2));
        list.add(make(g3, "animal", "fox", "狐狸", "/fɒks/", 2));
        list.add(make(g3, "animal", "deer", "鹿", "/dɪə/", 2));
        list.add(make(g3, "animal", "bear", "熊", "/beə/", 2));
        list.add(make(g3, "animal", "kangaroo", "袋鼠", "/ˌkæŋɡəˈruː/", 2));
        list.add(make(g3, "food", "sandwich", "三明治", "/ˈsænwɪtʃ/", 2));
        list.add(make(g3, "food", "hamburger", "汉堡包", "/ˈhæmbɜːɡə/", 2));
        list.add(make(g3, "food", "pizza", "比萨", "/ˈpiːtsə/", 2));
        list.add(make(g3, "food", "noodle", "面条", "/ˈnuːdəl/", 2));
        list.add(make(g3, "food", "chicken", "鸡肉", "/ˈtʃɪkɪn/", 2));
        list.add(make(g3, "school", "dictionary", "词典", "/ˈdɪkʃənri/", 2));
        list.add(make(g3, "school", "library", "图书馆", "/ˈlaɪbrəri/", 2));
        list.add(make(g3, "school", "homework", "作业", "/ˈhəʊmwɜːk/", 2));
        list.add(make(g3, "school", "notebook", "笔记本", "/ˈnəʊtbʊk/", 2));
        list.add(make(g3, "school", "subject", "科目", "/ˈsʌbdʒɪkt/", 2));
        list.add(make(g3, "verb", "draw", "画画", "/drɔː/", 2));
        list.add(make(g3, "verb", "fly", "飞", "/flaɪ/", 2));
        list.add(make(g3, "verb", "climb", "爬", "/klaɪm/", 2));
        list.add(make(g3, "verb", "catch", "抓住", "/kætʃ/", 2));
        list.add(make(g3, "verb", "throw", "扔", "/θrəʊ/", 2));
        list.add(make(g3, "verb", "cook", "烹饪", "/kʊk/", 2));
        list.add(make(g3, "verb", "sleep", "睡觉", "/sliːp/", 2));
        list.add(make(g3, "verb", "wake", "醒来", "/weɪk/", 2));
        list.add(make(g3, "time", "today", "今天", "/təˈdeɪ/", 2));
        list.add(make(g3, "time", "tomorrow", "明天", "/təˈmɒrəʊ/", 2));
        list.add(make(g3, "time", "yesterday", "昨天", "/ˈjestədeɪ/", 2));
        list.add(make(g3, "time", "morning", "早上", "/ˈmɔːnɪŋ/", 2));
        list.add(make(g3, "time", "afternoon", "下午", "/ˌɑːftəˈnuːn/", 2));
        list.add(make(g3, "time", "evening", "傍晚", "/ˈiːvnɪŋ/", 2));
        list.add(make(g3, "time", "week", "星期", "/wiːk/", 2));
        list.add(make(g3, "time", "month", "月份", "/mʌnθ/", 2));
        list.add(make(g3, "family", "grandpa", "爷爷", "/ˈɡrænpɑː/", 2));
        list.add(make(g3, "family", "grandma", "奶奶", "/ˈɡrænmɑː/", 2));
        list.add(make(g3, "family", "uncle", "叔叔", "/ˈʌŋkəl/", 2));
        list.add(make(g3, "family", "aunt", "阿姨", "/ɑːnt/", 2));
        list.add(make(g3, "family", "cousin", "表兄妹", "/ˈkʌzən/", 2));
        list.add(make(g3, "place", "park", "公园", "/pɑːk/", 2));
        list.add(make(g3, "place", "hospital", "医院", "/ˈhɒspɪtəl/", 2));
        list.add(make(g3, "place", "supermarket", "超市", "/ˈsuːpəˌmɑːkɪt/", 2));
        list.add(make(g3, "place", "restaurant", "餐厅", "/ˈrestərɒnt/", 2));
        list.add(make(g3, "place", "library", "图书馆", "/ˈlaɪbrəri/", 2));

        // ==================== 小学四年级 ====================
        String g4 = "grade4";
        list.add(make(g4, "animal", "whale", "鲸鱼", "/weɪl/", 2));
        list.add(make(g4, "animal", "dolphin", "海豚", "/ˈdɒlfɪn/", 2));
        list.add(make(g4, "animal", "eagle", "鹰", "/ˈiːɡəl/", 2));
        list.add(make(g4, "animal", "parrot", "鹦鹉", "/ˈpærət/", 2));
        list.add(make(g4, "animal", "butterfly", "蝴蝶", "/ˈbʌtəflaɪ/", 2));
        list.add(make(g4, "food", "vegetable", "蔬菜", "/ˈvedʒtəbəl/", 2));
        list.add(make(g4, "food", "fruit", "水果", "/fruːt/", 2));
        list.add(make(g4, "food", "breakfast", "早餐", "/ˈbrekfəst/", 2));
        list.add(make(g4, "food", "lunch", "午餐", "/lʌntʃ/", 2));
        list.add(make(g4, "food", "dinner", "晚餐", "/ˈdɪnə/", 2));
        list.add(make(g4, "school", "science", "科学", "/ˈsaɪəns/", 2));
        list.add(make(g4, "school", "history", "历史", "/ˈhɪstəri/", 2));
        list.add(make(g4, "school", "exercise", "练习", "/ˈeksəsaɪz/", 2));
        list.add(make(g4, "verb", "remember", "记住", "/rɪˈmembə/", 2));
        list.add(make(g4, "verb", "forget", "忘记", "/fəˈɡet/", 2));
        list.add(make(g4, "verb", "teach", "教", "/tiːtʃ/", 2));
        list.add(make(g4, "verb", "learn", "学习", "/lɜːn/", 2));
        list.add(make(g4, "verb", "begin", "开始", "/bɪˈɡɪn/", 2));
        list.add(make(g4, "verb", "finish", "完成", "/ˈfɪnɪʃ/", 2));
        list.add(make(g4, "adj", "beautiful", "美丽的", "/ˈbjuːtɪfəl/", 2));
        list.add(make(g4, "adj", "handsome", "英俊的", "/ˈhænsəm/", 2));
        list.add(make(g4, "adj", "hungry", "饿的", "/ˈhʌŋɡri/", 2));
        list.add(make(g4, "adj", "thirsty", "渴的", "/ˈθɜːsti/", 2));
        list.add(make(g4, "adj", "angry", "生气的", "/ˈæŋɡri/", 2));
        list.add(make(g4, "adj", "excited", "兴奋的", "/ɪkˈsaɪtɪd/", 2));
        list.add(make(g4, "place", "museum", "博物馆", "/mjuːˈziːəm/", 2));
        list.add(make(g4, "place", "theatre", "剧院", "/ˈθɪətə/", 2));
        list.add(make(g4, "place", "bridge", "桥", "/brɪdʒ/", 2));

        // ==================== 小学五年级 ====================
        String g5 = "grade5";
        list.add(make(g5, "animal", "penguin", "企鹅", "/ˈpeŋɡwɪn/", 3));
        list.add(make(g5, "animal", "crocodile", "鳄鱼", "/ˈkrɒkədaɪl/", 3));
        list.add(make(g5, "animal", "giraffe", "长颈鹿", "/dʒəˈrɑːf/", 3));
        list.add(make(g5, "animal", "squirrel", "松鼠", "/ˈskwɪrəl/", 3));
        list.add(make(g5, "nature", "mountain", "山", "/ˈmaʊntɪn/", 3));
        list.add(make(g5, "nature", "ocean", "海洋", "/ˈəʊʃən/", 3));
        list.add(make(g5, "nature", "forest", "森林", "/ˈfɒrɪst/", 3));
        list.add(make(g5, "nature", "river", "河流", "/ˈrɪvə/", 3));
        list.add(make(g5, "nature", "island", "岛屿", "/ˈaɪlənd/", 3));
        list.add(make(g5, "body", "stomach", "胃", "/ˈstʌmək/", 3));
        list.add(make(g5, "body", "shoulder", "肩膀", "/ˈʃəʊldə/", 3));
        list.add(make(g5, "body", "finger", "手指", "/ˈfɪŋɡə/", 3));
        list.add(make(g5, "body", "knee", "膝盖", "/niː/", 3));
        list.add(make(g5, "body", "tongue", "舌头", "/tʌŋ/", 3));
        list.add(make(g5, "verb", "explore", "探索", "/ɪkˈsplɔː/", 3));
        list.add(make(g5, "verb", "discover", "发现", "/dɪˈskʌvə/", 3));
        list.add(make(g5, "verb", "invent", "发明", "/ɪnˈvent/", 3));
        list.add(make(g5, "verb", "imagine", "想象", "/ɪˈmædʒɪn/", 3));
        list.add(make(g5, "verb", "protect", "保护", "/prəˈtekt/", 3));
        list.add(make(g5, "verb", "collect", "收集", "/kəˈlekt/", 3));
        list.add(make(g5, "verb", "travel", "旅行", "/ˈtrævəl/", 3));
        list.add(make(g5, "adj", "important", "重要的", "/ɪmˈpɔːtənt/", 3));
        list.add(make(g5, "adj", "dangerous", "危险的", "/ˈdeɪndʒərəs/", 3));
        list.add(make(g5, "adj", "different", "不同的", "/ˈdɪfərənt/", 3));
        list.add(make(g5, "adj", "wonderful", "精彩的", "/ˈwʌndəfəl/", 3));
        list.add(make(g5, "adj", "difficult", "困难的", "/ˈdɪfɪkəlt/", 3));
        list.add(make(g5, "adj", "popular", "流行的", "/ˈpɒpjʊlə/", 3));
        list.add(make(g5, "time", "season", "季节", "/ˈsiːzən/", 3));
        list.add(make(g5, "time", "spring", "春天", "/sprɪŋ/", 2));
        list.add(make(g5, "time", "summer", "夏天", "/ˈsʌmə/", 2));
        list.add(make(g5, "time", "autumn", "秋天", "/ˈɔːtəm/", 2));
        list.add(make(g5, "time", "winter", "冬天", "/ˈwɪntə/", 2));

        // ==================== 初中小部分 ====================
        String j1 = "junior1";
        list.add(make(j1, "person", "friend", "朋友", "/frend/", 3));
        list.add(make(j1, "person", "neighbour", "邻居", "/ˈneɪbə/", 3));
        list.add(make(j1, "feeling", "happy", "快乐的", "/ˈhæpi/", 2));
        list.add(make(j1, "feeling", "sad", "悲伤的", "/sæd/", 2));
        list.add(make(j1, "feeling", "brave", "勇敢的", "/breɪv/", 3));
        list.add(make(j1, "feeling", "proud", "自豪的", "/praʊd/", 3));
        list.add(make(j1, "feeling", "nervous", "紧张的", "/ˈnɜːvəs/", 3));
        list.add(make(j1, "sport", "basketball", "篮球", "/ˈbɑːskɪtbɔːl/", 3));
        list.add(make(j1, "sport", "football", "足球", "/ˈfʊtbɔːl/", 2));
        list.add(make(j1, "sport", "volleyball", "排球", "/ˈvɒlibɔːl/", 3));
        list.add(make(j1, "sport", "tennis", "网球", "/ˈtenɪs/", 3));
        list.add(make(j1, "verb", "encourage", "鼓励", "/ɪnˈkʌrɪdʒ/", 3));
        list.add(make(j1, "verb", "celebrate", "庆祝", "/ˈselɪbreɪt/", 3));
        list.add(make(j1, "verb", "communicate", "交流", "/kəˈmjuːnɪkeɪt/", 4));

        // ==================== 常用短语 ====================
        String phr = "phrase";
        list.add(make(phr, "phrase", "get up", "起床", "/ɡet ʌp/", 1));
        list.add(make(phr, "phrase", "go to bed", "上床睡觉", "/ɡəʊ tuː bed/", 1));
        list.add(make(phr, "phrase", "go home", "回家", "/ɡəʊ həʊm/", 1));
        list.add(make(phr, "phrase", "come here", "过来", "/kʌm hɪə/", 1));
        list.add(make(phr, "phrase", "look at", "看", "/lʊk æt/", 2));
        list.add(make(phr, "phrase", "listen to", "听", "/ˈlɪsən tuː/", 2));
        list.add(make(phr, "phrase", "wait for", "等待", "/weɪt fɔː/", 2));
        list.add(make(phr, "phrase", "talk about", "谈论", "/tɔːk əˈbaʊt/", 3));
        list.add(make(phr, "phrase", "think about", "思考", "/θɪŋk əˈbaʊt/", 3));
        list.add(make(phr, "phrase", "look for", "寻找", "/lʊk fɔː/", 3));
        list.add(make(phr, "phrase", "take care of", "照顾", "/teɪk keər ɒv/", 3));
        list.add(make(phr, "phrase", "have fun", "玩得开心", "/hæv fʌn/", 2));
        list.add(make(phr, "phrase", "come back", "回来", "/kʌm bæk/", 2));
        list.add(make(phr, "phrase", "go out", "出去", "/ɡəʊ aʊt/", 2));
        list.add(make(phr, "phrase", "wake up", "醒来", "/weɪk ʌp/", 2));
        list.add(make(phr, "phrase", "brush teeth", "刷牙", "/brʌʃ tiːθ/", 1));
        list.add(make(phr, "phrase", "wash face", "洗脸", "/wɒʃ feɪs/", 1));
        list.add(make(phr, "phrase", "do homework", "做作业", "/duː ˈhəʊmwɜːk/", 2));
        list.add(make(phr, "phrase", "watch TV", "看电视", "/wɒtʃ tiː viː/", 2));

        return list;
    }

    private static Vocabulary make(String grade, String cat, String word, String meaning, String phonetic, int level) {
        Vocabulary v = new Vocabulary();
        v.word = word;
        v.meaning = meaning;
        v.phonetic = phonetic;
        v.category = cat;
        v.gradeLevel = grade;
        v.level = level;
        v.active = true;
        v.mastered = false;
        return v;
    }
}