package com.sister.habits.data;

import com.sister.habits.data.models.Vocabulary;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WordBankLoader {
    public static List<Vocabulary> getAllWords() {
        List<Vocabulary> list = new ArrayList<>();
        // grade1
        add(list,"grade1","animal","cat","猫","/kæt/",1);
        add(list,"grade1","animal","dog","狗","/dɒɡ/",1);
        add(list,"grade1","animal","fish","鱼","/fɪʃ/",1);
        add(list,"grade1","animal","bird","鸟","/bɜːrd/",1);
        add(list,"grade1","color","red","红色","/red/",1);
        add(list,"grade1","color","blue","蓝色","/bluː/",1);
        add(list,"grade1","color","green","绿色","/ɡriːn/",1);
        add(list,"grade1","number","one","一","/wʌn/",1);
        add(list,"grade1","number","two","二","/tuː/",1);
        add(list,"grade1","body","eye","眼睛","/aɪ/",1);
        add(list,"grade1","body","ear","耳朵","/ɪr/",1);
        // grade2
        add(list,"grade2","food","apple","苹果","/ˈæpəl/",1);
        add(list,"grade2","food","banana","香蕉","/bəˈnænə/",1);
        add(list,"grade2","food","orange","橙子","/ˈɔːrɪndʒ/",1);
        add(list,"grade2","family","father","爸爸","/ˈfɑːðər/",1);
        add(list,"grade2","family","mother","妈妈","/ˈmʌðər/",1);
        add(list,"grade2","family","sister","姐妹","/ˈsɪstər/",1);
        add(list,"grade2","school","book","书","/bʊk/",1);
        add(list,"grade2","school","teacher","老师","/ˈtiːtʃər/",1);
        // grade3
        add(list,"grade3","verb","run","跑","/rʌn/",2);
        add(list,"grade3","verb","jump","跳","/dʒʌmp/",2);
        add(list,"grade3","verb","swim","游泳","/swɪm/",2);
        add(list,"grade3","verb","read","阅读","/riːd/",2);
        add(list,"grade3","verb","eat","吃","/iːt/",2);
        add(list,"grade3","adj","big","大的","/bɪɡ/",2);
        add(list,"grade3","adj","small","小的","/smɔːl/",2);
        add(list,"grade3","adj","happy","开心的","/ˈhæpi/",2);
        add(list,"grade3","place","home","家","/hoʊm/",2);
        add(list,"grade3","place","school","学校","/skuːl/",2);
        // grade4
        add(list,"grade4","verb","cook","做饭","/kʊk/",2);
        add(list,"grade4","verb","clean","打扫","/kliːn/",2);
        add(list,"grade4","verb","play","玩耍","/pleɪ/",2);
        add(list,"grade4","verb","study","学习","/ˈstʌdi/",2);
        add(list,"grade4","time","morning","早上","/ˈmɔːrnɪŋ/",2);
        add(list,"grade4","time","night","夜晚","/naɪt/",2);
        add(list,"grade4","time","today","今天","/təˈdeɪ/",2);
        add(list,"grade4","weather","sunny","晴天","/ˈsʌni/",2);
        add(list,"grade4","weather","rainy","下雨的","/ˈreɪni/",2);
        // grade5
        add(list,"grade5","nature","flower","花","/ˈflaʊər/",3);
        add(list,"grade5","nature","tree","树","/triː/",3);
        add(list,"grade5","nature","moon","月亮","/muːn/",3);
        add(list,"grade5","nature","star","星星","/stɑːr/",3);
        add(list,"grade5","subject","English","英语","/ˈɪŋɡlɪʃ/",3);
        add(list,"grade5","subject","math","数学","/mæθ/",3);
        add(list,"grade5","abstract","love","爱","/lʌv/",3);
        add(list,"grade5","abstract","friend","朋友","/frend/",3);
        add(list,"grade5","abstract","dream","梦想","/driːm/",3);
        add(list,"grade5","abstract","family","家庭","/ˈfæməli/",3);
        // junior
        add(list,"junior","verb","encourage","鼓励","/ɪnˈkɜːrɪdʒ/",4);
        add(list,"junior","verb","achieve","实现","/əˈtʃiːv/",4);
        add(list,"junior","verb","improve","提高","/ɪmˈpruːv/",4);
        add(list,"junior","adj","important","重要的","/ɪmˈpɔːrtnt/",4);
        add(list,"junior","adj","beautiful","美丽的","/ˈbjuːtɪfl/",4);
        add(list,"junior","noun","knowledge","知识","/ˈnɒlɪdʒ/",4);
        add(list,"junior","abstract","honesty","诚实","/ˈɒnɪsti/",4);
        add(list,"junior","abstract","courage","勇气","/ˈkʌrɪdʒ/",4);
        add(list,"junior","phrase","be proud of","以……为荣","/biː praʊd ʌv/",4);
        add(list,"junior","phrase","look forward to","期待","/lʊk ˈfɔːrwərd tuː/",4);
        return list;
    }
    private static void add(List<Vocabulary> list, String grade, String cat, String word, String meaning, String phone, int level) {
        Vocabulary v = new Vocabulary();
        v.id = UUID.randomUUID().toString();
        v.word = word;
        v.meaning = meaning;
        v.phonetic = phone;
        v.category = cat;
        v.gradeLevel = grade;
        v.level = level;
        v.mastered = false;
        v.active = true;
        v.bankId = "builtin";
        list.add(v);
    }
}
