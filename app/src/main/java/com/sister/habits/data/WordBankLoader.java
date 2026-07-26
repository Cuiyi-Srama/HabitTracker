package com.sister.habits.data;

import com.sister.habits.data.models.Vocabulary;
import java.util.ArrayList;
import java.util.List;

public class WordBankLoader {

    public static List<Vocabulary> getAllWords() {
        List<Vocabulary> list = new ArrayList<>();
        String g1 = "grade1";
        list.add(make(g1,"animal","cat","猫","/kæt/",1)); list.add(make(g1,"animal","dog","狗","/dɒɡ/",1));
        list.add(make(g1,"animal","duck","鸭子","/dʌk/",1)); list.add(make(g1,"animal","pig","猪","/pɪɡ/",1));
        list.add(make(g1,"color","red","红色","/red/",1)); list.add(make(g1,"color","blue","蓝色","/bluː/",1));
        list.add(make(g1,"color","green","绿色","/ɡriːn/",1)); list.add(make(g1,"color","yellow","黄色","/ˈjeləʊ/",1));
        list.add(make(g1,"number","one","一","/wʌn/",1)); list.add(make(g1,"number","two","二","/tuː/",1));
        list.add(make(g1,"number","three","三","/θriː/",1)); list.add(make(g1,"number","four","四","/fɔː/",1));
        list.add(make(g1,"number","five","五","/faɪv/",1)); list.add(make(g1,"number","six","六","/sɪks/",1));
        list.add(make(g1,"school","book","书","/bʊk/",1)); list.add(make(g1,"school","pen","钢笔","/pen/",1));
        list.add(make(g1,"school","bag","书包","/bæɡ/",1)); list.add(make(g1,"school","desk","书桌","/desk/",1));
        list.add(make(g1,"body","eye","眼睛","/aɪ/",1)); list.add(make(g1,"body","ear","耳朵","/ɪə/",1));
        list.add(make(g1,"body","nose","鼻子","/nəʊz/",1)); list.add(make(g1,"body","head","头","/hed/",1));
        list.add(make(g1,"food","rice","米饭","/raɪs/",1)); list.add(make(g1,"food","milk","牛奶","/mɪlk/",1));
        list.add(make(g1,"food","egg","鸡蛋","/eɡ/",1)); list.add(make(g1,"food","bread","面包","/bred/",1));
        list.add(make(g1,"family","father","爸爸","/ˈfɑːðə/",1)); list.add(make(g1,"family","mother","妈妈","/ˈmʌðə/",1));
        list.add(make(g1,"family","sister","姐妹","/ˈsɪstə/",1)); list.add(make(g1,"family","baby","婴儿","/ˈbeɪbi/",1));
        String g2 = "grade2";
        list.add(make(g2,"animal","rabbit","兔子","/ˈræbɪt/",1)); list.add(make(g2,"animal","panda","熊猫","/ˈpændə/",1));
        list.add(make(g2,"school","teacher","老师","/ˈtiːtʃə/",1)); list.add(make(g2,"school","pencil","铅笔","/ˈpensəl/",1));
        list.add(make(g2,"food","apple","苹果","/ˈæpəl/",1)); list.add(make(g2,"food","banana","香蕉","/bəˈnɑːnə/",1));
        list.add(make(g2,"food","cake","蛋糕","/keɪk/",1)); list.add(make(g2,"food","candy","糖果","/ˈkændi/",1));
        list.add(make(g2,"verb","run","跑","/rʌn/",1)); list.add(make(g2,"verb","jump","跳","/dʒʌmp/",1));
        list.add(make(g2,"verb","sing","唱歌","/sɪŋ/",1)); list.add(make(g2,"verb","dance","跳舞","/dɑːns/",1));
        list.add(make(g2,"weather","sunny","晴朗的","/ˈsʌni/",1)); list.add(make(g2,"weather","rainy","下雨的","/ˈreɪni/",1));
        String g3 = "grade3";
        list.add(make(g3,"animal","mouse","老鼠","/maʊs/",2)); list.add(make(g3,"animal","snake","蛇","/sneɪk/",2));
        list.add(make(g3,"animal","bear","熊","/beə/",2)); list.add(make(g3,"animal","kangaroo","袋鼠","/ˌkæŋɡəˈruː/",2));
        list.add(make(g3,"food","sandwich","三明治","/ˈsænwɪtʃ/",2)); list.add(make(g3,"food","hamburger","汉堡包","/ˈhæmbɜːɡə/",2));
        list.add(make(g3,"food","pizza","比萨","/ˈpiːtsə/",2)); list.add(make(g3,"school","homework","作业","/ˈhəʊmwɜːk/",2));
        list.add(make(g3,"verb","draw","画画","/drɔː/",2)); list.add(make(g3,"verb","cook","烹饪","/kʊk/",2));
        list.add(make(g3,"time","today","今天","/təˈdeɪ/",2)); list.add(make(g3,"time","morning","早上","/ˈmɔːnɪŋ/",2));
        list.add(make(g3,"family","grandpa","爷爷","/ˈɡrænpɑː/",2)); list.add(make(g3,"place","park","公园","/pɑːk/",2));
        String g4 = "grade4";
        list.add(make(g4,"animal","whale","鲸鱼","/weɪl/",2)); list.add(make(g4,"animal","dolphin","海豚","/ˈdɒlfɪn/",2));
        list.add(make(g4,"food","lunch","午餐","/lʌntʃ/",2)); list.add(make(g4,"verb","remember","记住","/rɪˈmembə/",2));
        list.add(make(g4,"verb","learn","学习","/lɜːn/",2)); list.add(make(g4,"adj","beautiful","美丽的","/ˈbjuːtɪfəl/",2));
        String g5 = "grade5";
        list.add(make(g5,"nature","mountain","山","/ˈmaʊntɪn/",3)); list.add(make(g5,"nature","ocean","海洋","/ˈəʊʃən/",3));
        list.add(make(g5,"verb","explore","探索","/ɪkˈsplɔː/",3)); list.add(make(g5,"adj","important","重要的","/ɪmˈpɔːtənt/",3));
        String j1 = "junior1";
        list.add(make(j1,"person","friend","朋友","/frend/",3)); list.add(make(j1,"feeling","happy","快乐的","/ˈhæpi/",2));
        list.add(make(j1,"sport","basketball","篮球","/ˈbɑːskɪtbɔːl/",3));
        String phr = "phrase";
        list.add(make(phr,"phrase","get up","起床","/ɡet ʌp/",1)); list.add(make(phr,"phrase","go to bed","睡觉","/ɡəʊ tuː bed/",1));
        list.add(make(phr,"phrase","look at","看","/lʊk æt/",2)); list.add(make(phr,"phrase","wake up","醒来","/weɪk ʌp/",2));
        list.add(make(phr,"phrase","brush teeth","刷牙","/brʌʃ tiːθ/",1)); list.add(make(phr,"phrase","do homework","做作业","/duː ˈhəʊmwɜːk/",2));
        list.add(make(phr,"phrase","watch TV","看电视","/wɒtʃ tiː viː/",2));
        return list;
    }
    private static Vocabulary make(String grade, String cat, String word, String meaning, String phonetic, int level) {
        Vocabulary v = new Vocabulary();
        v.word = word; v.meaning = meaning; v.phonetic = phonetic;
        v.category = cat; v.gradeLevel = grade; v.level = level;
        v.active = true; v.mastered = false;
        return v;
    }
}
