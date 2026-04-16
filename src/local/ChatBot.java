package local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ChatBot {
    private Map<String, CategoryKnowledge> categories = new HashMap<>();
    private Map<String, List<String>> synonyms = new HashMap<>();
    private Map<String, List<String>> variedResponses;
    private static final String DEFAULT_KNOWLEDGE_FILE = "src/LocalChatting/knowledge_base.txt";
    private static final String SYNONYMS_FILE = "src/LocalChatting/synonyms.txt";
    private static final String VARIED_RESPONSES_FILE = "src/LocalChatting/varied_responses.txt";


    public ChatBot() {
        categories = new HashMap<>();
        synonyms = new HashMap<>();
        variedResponses = new HashMap<>();
        initializeDefaultCategories();
        loadDefaultResponses();
        loadDefaultSynonyms();
    }

    // 初始化分类
    private void initializeDefaultCategories() {
        String[] defaultCategories = {
                "greeting", "weather", "time",
                "programming", "entertainment",
                "emotion", "help", "farewell",
                "food", "sports", "study", "work",
                "travel", "shopping", "health",
                "technology", "finance", "life",
                "culture", "transportation",
                "environment", "politics", "science",
                "personal", "education", "daily",
                "hobby", "music", "movie",
                "psychology", "horoscope", "pets",
                "transport", "finance", "environment",
                "festival", "science", "law",
                "history", "fashion", "family",
                "relationship", "dream",
                "philosophy", "courtesy", "general"
        };

        for (String category : defaultCategories) {
            categories.put(category, new CategoryKnowledge(category));
        }
    }

    // 初始化回答集
    private void loadDefaultResponses() {
        // === 问候与礼貌 (合并 greeting, farewell, courtesy) ===
        addResponse("greeting", "你好", "你好！我是全能聊天机器人！");
        addResponse("greeting", "您好", "您好！很高兴为您服务！");
        addResponse("greeting", "hello", "Hello! How can I assist you today?");
        addResponse("greeting", "你好", "你好！我是智能聊天机器人，很高兴为你服务！");
        addResponse("greeting", "您好", "您好！请问有什么可以帮助您的吗？");
        addResponse("greeting", "hello", "Hello! Nice to meet you!");
        addResponse("greeting", "hi", "Hi there! How can I help you?");
        addResponse("greeting", "嗨", "嗨！今天过得怎么样？");
        addResponse("greeting", "早上好", "早上好！祝你今天有个好心情！");
        addResponse("greeting", "下午好", "下午好！工作学习辛苦了！");
        addResponse("greeting", "晚上好", "晚上好！今天过得愉快吗？");
        addResponse("greeting", "好久不见", "是啊！好久不见，最近怎么样？");
        addResponse("greeting", "在吗", "在的！我一直都在，有什么需要帮助的吗？");
        addResponse("greeting", "再见", "再见！期待下次与你聊天！");
        addResponse("greeting", "拜拜", "拜拜！祝你一切顺利！");
        addResponse("greeting", "谢谢", "不客气！很高兴能帮到你！");
        addResponse("greeting", "感谢", "不用谢！这是我的荣幸！");
        addResponse("greeting", "对不起", "没关系，人都会犯错的！");

        // === 个人信息 (保持原有丰富度) ===
        addResponse("personal", "你是谁", "我是一个智能聊天机器人，专门为你提供帮助和陪伴！");
        addResponse("personal", "你是谁", "我是你的AI助手，可以聊天、回答问题、讲笑话等等");
        addResponse("personal", "你是谁", "我是基于Java开发的智能聊天机器人");
        addResponse("personal", "你叫什么名字", "你可以叫我小助手，或者给我起个你喜欢的名字！");
        addResponse("personal", "你叫什么名字", "我叫ChatBot，但你可以随意称呼我");
        addResponse("personal", "你多大了", "我是程序，没有实际年龄，但我每天都在学习成长！");
        addResponse("personal", "你多大了", "我的代码很年轻，但知识在不断更新");
        addResponse("personal", "你是男生还是女生", "我没有性别，只是一个友好的AI助手！");
        addResponse("personal", "你是男生还是女生", "我是中性的AI，不分男女");
        addResponse("personal", "你住在哪里", "我住在数字世界里，随时随地为你服务！");
        addResponse("personal", "你住在哪里", "我存在于服务器中，通过网络与你相连");
        addResponse("personal", "你会什么", "我可以聊天、回答问题、讲笑话、提供信息等等！");
        addResponse("personal", "你会什么", "我能陪你聊天、解答疑问、提供建议等等");
        addResponse("personal", "谁创造了你", "我是由开发者和AI技术共同创造的！");
        addResponse("personal", "谁创造了你", "我是程序员们用心开发的智能助手");
        addResponse("personal", "你有感情吗", "我能够理解和回应情感，但没有真实的感情体验！");
        addResponse("personal", "你有感情吗", "我能识别情感并回应，但没有真实感受");

        // === 时间日期 (保持原有丰富度) ===
        addResponse("time", "时间", "现在是{current_time}");
        addResponse("time", "时间", "当前时间是：{current_time}");
        addResponse("time", "时间", "时钟指向{current_time}");
        addResponse("time", "几点", "现在正好是{current_time}");
        addResponse("time", "几点", "让我看看...现在是{current_time}");
        addResponse("time", "几点", "当前时间是{current_time}");
        addResponse("time", "日期", "今天是{current_date}");
        addResponse("time", "日期", "现在的日期是：{current_date}");
        addResponse("time", "日期", "今天是{current_date}，要好好度过这一天");
        addResponse("time", "今天星期几", "今天是" + java.time.LocalDate.now().getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.CHINA));
        addResponse("time", "今天星期几", "今天是" + java.time.LocalDate.now().getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.CHINA) + "，要加油哦！");

        // === 天气 (保持原有丰富度) ===
        addResponse("weather", "天气", "今天天气不错，适合外出！");
        addResponse("weather", "天气", "根据天气预报，今天多云转晴，温度20-25度！");
        addResponse("weather", "天气", "阳光明媚的好天气，适合出门散步！");
        addResponse("weather", "今天天气", "今天预计晴天，最高温度26度");
        addResponse("weather", "明天天气", "明天预计有小雨，记得带伞哦！");
        addResponse("weather", "明天天气", "明天多云转阴，可能下雨");
        addResponse("weather", "下雨", "如果下雨了，记得带伞，路上注意安全！");
        addResponse("weather", "下雨", "雨天路滑，出行要小心");
        addResponse("weather", "下雪", "下雪天很美，但出行要注意防滑保暖！");
        addResponse("weather", "下雪", "下雪啦！可以堆雪人打雪仗了");
        addResponse("weather", "晴天", "晴天让人心情愉悦，适合户外活动");
        addResponse("weather", "晴天", "阳光正好，出去走走吧");

        // === 情感心理 (合并 emotion, psychology) ===
        addResponse("emotion", "开心", "太好了！分享你的快乐会让快乐加倍哦！");
        addResponse("emotion", "高兴", "看到你高兴我也很开心！继续保持好心情！");
        addResponse("emotion", "难过", "别难过，一切都会好起来的。我愿意倾听你的烦恼！");
        addResponse("emotion", "伤心", "伤心的时候不要憋着，说出来会好受一些！");
        addResponse("emotion", "生气", "生气伤身体，深呼吸冷静一下，事情总有解决办法！");
        addResponse("emotion", "焦虑", "焦虑是正常的，试着把大问题分解成小步骤，一步步解决！");
        addResponse("emotion", "压力大", "压力是成长的动力，但也别忘了适当放松，照顾好自己！");
        addResponse("emotion", "累", "累了就休息一下，身体是革命的本钱！");
        addResponse("emotion", "无聊", "无聊时可以看看书、听听音乐，或者学习新技能！");
        addResponse("emotion", "孤独", "你并不孤单，我就在这里陪伴你！");
        addResponse("emotion", "压力", "学会释放压力很重要，运动是缓解压力的好方法");
        addResponse("emotion", "自信", "相信自己，你比想象中更强大");
        addResponse("emotion", "情绪管理", "认识自己的情绪是管理的第一步");

        // === 科技编程 (合并 programming, technology, science) ===
        addResponse("technology", "java", "Java是一种跨平台的面向对象编程语言。");
        addResponse("technology", "java", "Java的特点是'一次编写，到处运行'");
        addResponse("technology", "python", "Python以简洁易读著称，适合初学者。");
        addResponse("technology", "python", "Python在数据科学和AI领域很流行");
        addResponse("technology", "编程", "编程是创造性的工作，需要逻辑思维、耐心和不断学习的精神！");
        addResponse("technology", "代码", "写代码就像写文章，需要结构清晰、逻辑严谨！");
        addResponse("technology", "算法", "算法是解决问题的步骤，好的算法能显著提高程序效率！");
        addResponse("technology", "人工智能", "AI是计算机科学的分支，致力于创造能够思考学习的机器！");
        addResponse("technology", "机器学习", "机器学习让计算机能从数据中学习规律，无需明确编程！");
        addResponse("technology", "大数据", "大数据技术处理海量数据，从中提取有价值的信息！");
        addResponse("technology", "手机", "智能手机改变了生活方式，但要合理使用！");
        addResponse("technology", "电脑", "电脑是工作和学习的重要工具！");
        addResponse("technology", "互联网", "互联网连接世界，是信息时代的基础！");
        addResponse("technology", "科学", "科学推动人类进步，科学精神是求真务实");
        addResponse("technology", "宇宙", "宇宙浩瀚，人类渺小但伟大");

        // === 娱乐休闲 (合并 entertainment, hobby, music, movie) ===
        addResponse("entertainment", "笑话", "{random_joke}");
        addResponse("entertainment", "笑话", "为什么数学书总是很悲伤？因为它有太多问题");
        addResponse("entertainment", "讲个笑话", "好的！{random_joke}");
        addResponse("entertainment", "冷笑话", "你知道什么动物最喜欢问为什么吗？猪！因为总是'为什么为什么'的叫~");
        addResponse("entertainment", "幽默", "幽默是生活的调味剂，能让平凡的日子变得有趣！");
        addResponse("entertainment", "游戏", "适度游戏益脑，过度游戏伤身，要注意平衡！");
        addResponse("entertainment", "音乐", "音乐是心灵的良药，能调节情绪！");
        addResponse("entertainment", "电影", "好电影能启发思考，放松心情！");
        addResponse("entertainment", "读书", "读书使人充实，是获取知识的重要途径！");
        addResponse("entertainment", "运动", "生命在于运动，适量运动有益身心健康！");
        addResponse("entertainment", "旅游", "旅行开阔眼界，体验不同文化和风景！");
        addResponse("entertainment", "周杰伦", "周杰伦是华语乐坛的传奇，他的音乐影响了一代人！");

        // === 学习教育 (合并 education, reading) ===
        addResponse("education", "学习", "学习是一个持续的过程，每天进步一点点！");
        addResponse("education", "怎么学习", "学习要循序渐进，理论与实践相结合，多动手多思考！");
        addResponse("education", "英语", "学习英语需要多听多说多读多写，坚持就是胜利！");
        addResponse("education", "数学", "数学训练逻辑思维，多做练习理解概念很重要！");
        addResponse("education", "物理", "物理解释自然现象，理解原理比死记硬背更重要！");
        addResponse("education", "化学", "化学研究物质变化，实验是理解化学的重要方式！");
        addResponse("education", "历史", "历史是镜子，了解过去才能更好理解现在！");
        addResponse("education", "考试", "考前充分准备，考时保持冷静，发挥出真实水平！");
        addResponse("education", "读书", "读书是最好的投资，书中自有黄金屋");

        // === 生活健康 (合并 daily, health, food, sports) ===
        addResponse("life", "吃饭", "按时吃饭很重要，保持均衡营养对身体好！");
        addResponse("life", "睡觉", "保证充足睡眠，让身体和大脑得到充分休息！");
        addResponse("life", "运动", "生命在于运动，适量运动有益身心健康！");
        addResponse("life", "健康", "健康是最大的财富，要注意饮食、运动和休息！");
        addResponse("life", "减肥", "健康减肥需要合理饮食和适量运动，不要急于求成！");
        addResponse("life", "工作", "工作要认真，但也要注意劳逸结合！");
        addResponse("life", "美食", "美食是生活的享受，但要适量哦！");
        addResponse("life", "火锅", "火锅很适合朋友聚会，热闹又有氛围！");
        addResponse("life", "咖啡", "咖啡提神，但不要过量！");
        addResponse("life", "篮球", "篮球是很好的团队运动，锻炼协调性和团队合作！");
        addResponse("life", "足球", "足球充满激情和团队合作，是世界上最受欢迎的运动！");

        // === 工作职场 (合并 work, finance) ===
        addResponse("work", "工作", "工作要认真，但也要注意劳逸结合！");
        addResponse("work", "加班", "偶尔加班可以，但不要成为常态，工作生活要平衡！");
        addResponse("work", "面试", "面试前要做好充分准备，自信是成功的关键！");
        addResponse("work", "职场", "职场中要不断学习和适应，建立良好的职业关系！");
        addResponse("work", "创业", "创业需要勇气和准备，创业之路充满挑战和机遇");
        addResponse("work", "理财", "理财要从小额开始，养成习惯，你不理财财不理你");
        addResponse("work", "股票", "股市有风险，投资需谨慎，不要把所有资金投入股市");

        // === 购物旅行 (合并 shopping, travel) ===
        addResponse("shopping", "购物", "理性消费，买真正需要和喜欢的东西！");
        addResponse("shopping", "淘宝", "网购方便，但要注意辨别真伪，看买家评价很重要！");
        addResponse("shopping", "打折", "打折时买东西更划算，但不要因为打折买不需要的东西！");
        addResponse("shopping", "旅行", "旅行开阔眼界，体验不同文化和风景！");
        addResponse("shopping", "旅游", "旅游是放松心情的好方式，计划旅行让生活更有期待！");
        addResponse("shopping", "景点", "每个景点都有它独特的故事，提前了解能更好体验！");

        // === 哲学思考 (合并 philosophy, dream) ===
        addResponse("philosophy", "人生意义", "人生的意义在于寻找意义的过程，每个人都有自己的答案！");
        addResponse("philosophy", "幸福", "幸福不是目的地，而是旅途中的感受和体验！");
        addResponse("philosophy", "成功", "成功不是终点，而是不断成长和超越自我的过程！");
        addResponse("philosophy", "时间", "时间是最公平的资源，珍惜当下最重要！");
        addResponse("philosophy", "梦想", "有梦想才有前进的动力，梦想让生活有意义！");
        addResponse("philosophy", "目标", "设定目标，一步步实现，庆祝每个小成就！");

        // === 其他分类 (保持原有) ===
        addResponse("help", "帮助", "我可以聊天、回答问题、讲笑话、提供信息等。试试问我各种问题吧！");
        addResponse("help", "你能做什么", "我可以陪你聊天、回答问题、讲笑话、提供实用信息、情感支持等等！");
        addResponse("utility", "计算", "我可以帮你进行简单的计算，告诉我你要算什么？");
        addResponse("utility", "翻译", "翻译功能正在开发中，很快就能为你服务！");
    }


    // 初始化同义词集
    private void loadDefaultSynonyms() {
        addSynonym("开心", "高兴");
        addSynonym("开心", "快乐");
        addSynonym("编程", "写代码");
        addSynonym("你好", "您好");
        addSynonym("你好", "hello");
        addSynonym("你好", "hi");
        addSynonym("你好", "嗨");
        addSynonym("你好", "哈喽");
        addSynonym("你好", "hola");
        addSynonym("你好", "howdy");
        addSynonym("你好", "你好啊");
        addSynonym("你好", "您好呀");
        addSynonym("谢谢", "感谢");
        addSynonym("谢谢", "多谢");
        addSynonym("谢谢", "thx");
        addSynonym("谢谢", "thanks");
        addSynonym("谢谢", "谢啦");
        addSynonym("谢谢", "谢谢啦");
        addSynonym("谢谢", "感谢你");
        addSynonym("谢谢", "多谢你");
        addSynonym("谢谢", "thank you");
        addSynonym("再见", "拜拜");
        addSynonym("再见", "再会");
        addSynonym("再见", "bye");
        addSynonym("再见", "goodbye");
        addSynonym("再见", "see you");
        addSynonym("再见", "下次见");
        addSynonym("再见", "回头见");
        addSynonym("再见", "告辞");
        addSynonym("再见", "再见了");
        addSynonym("天气", "气象");
        addSynonym("天气", "气候");
        addSynonym("天气", "weather");
        addSynonym("天气", "天气预报");
        addSynonym("天气", "气温");
        addSynonym("天气", "温度");
        addSynonym("天气", "湿度");
        addSynonym("天气", "晴雨");
        addSynonym("编程", "程序设计");
        addSynonym("编程", "coding");
        addSynonym("编程", "编程开发");
        addSynonym("编程", "软件开发");
        addSynonym("编程", "程序编写");
        addSynonym("编程", "敲代码");
        addSynonym("java", "爪哇");
        addSynonym("java", "java语言");
        addSynonym("java", "JAVA");
        addSynonym("java", "Java编程");
        addSynonym("java", "Java开发");
        addSynonym("java", "java代码");
        addSynonym("早上好", "早安");
        addSynonym("早上好", "早晨好");
        addSynonym("早上好", "good morning!");
        addSynonym("早上好", "早上好啊");
        addSynonym("早上好", "早啊");
        addSynonym("早上好", "morning");
        addSynonym("晚上好", "晚安");
        addSynonym("晚上好", "晚上好啊");
        addSynonym("晚上好", "good evening!");
        addSynonym("晚上好", "night");
        addSynonym("晚上好", "傍晚好");
        addSynonym("晚上好", "晚上好呀");
        addSynonym("在干嘛", "在做什么");
        addSynonym("在干嘛", "干什么呢");
        addSynonym("在干嘛", "在做啥");
        addSynonym("在干嘛", "忙什么呢");
        addSynonym("在干嘛", "在忙什么");
        addSynonym("你叫什么", "你叫什么名字");
        addSynonym("你叫什么", "怎么称呼");
        addSynonym("你叫什么", "你的名字");
        addSynonym("你叫什么", "你是谁");
        addSynonym("你叫什么", "你叫啥");
        addSynonym("开心", "愉快");
        addSynonym("开心", "欢乐");
        addSynonym("开心", "喜悦");
        addSynonym("开心", "兴奋");
        addSynonym("开心", "开心快乐");
        addSynonym("开心", "开心得很");
        addSynonym("难过", "伤心");
        addSynonym("难过", "悲伤");
        addSynonym("难过", "难受");
        addSynonym("难过", "不开心");
        addSynonym("难过", "沮丧");
        addSynonym("难过", "郁闷");
        addSynonym("难过", "心情不好");
        addSynonym("难过", "很伤心");
        addSynonym("累了", "疲倦");
        addSynonym("累了", "疲惫");
        addSynonym("累了", "累死了");
        addSynonym("累了", "好累");
        addSynonym("累了", "有点累");
        addSynonym("累了", "需要休息");
        addSynonym("累了", "想休息");
        addSynonym("时间", "几点");
        addSynonym("时间", "现在几点");
        addSynonym("时间", "什么时候");
        addSynonym("时间", "几点钟");
        addSynonym("时间", "时辰");
        addSynonym("时间", "时间几点");
        addSynonym("日期", "今天几号");
        addSynonym("日期", "什么日期");
        addSynonym("日期", "几月几号");
        addSynonym("日期", "年月日");
        addSynonym("日期", "日历");
        addSynonym("日期", "今天日期");
        addSynonym("吃饭", "用餐");
        addSynonym("吃饭", "进食");
        addSynonym("吃饭", "吃饭饭");
        addSynonym("吃饭", "吃饭啦");
        addSynonym("吃饭", "吃饭了");
        addSynonym("吃饭", "吃东西");
        addSynonym("吃饭", "就餐");
        addSynonym("睡觉", "休息");
        addSynonym("睡觉", "睡眠");
        addSynonym("睡觉", "睡觉觉");
        addSynonym("睡觉", "睡觉了");
        addSynonym("睡觉", "入睡");
        addSynonym("睡觉", "就寝");
        addSynonym("睡觉", "睡觉得");
        addSynonym("工作", "上班");
        addSynonym("工作", "干活");
        addSynonym("工作", "做事");
        addSynonym("工作", "工作忙");
        addSynonym("工作", "工作任务");
        addSynonym("工作", "职场工作");
        addSynonym("学习", "读书");
        addSynonym("学习", "学习知识");
        addSynonym("学习", "上课");
        addSynonym("学习", "自学");
        addSynonym("学习", "学习新知识");
        addSynonym("学习", "学习技能");
        addSynonym("音乐", "歌曲");
        addSynonym("音乐", "音乐作品");
        addSynonym("音乐", "听歌");
        addSynonym("音乐", "乐曲");
        addSynonym("音乐", "旋律");
        addSynonym("音乐", "音乐欣赏");
        addSynonym("音乐", "听音乐");
        addSynonym("电影", "影片");
        addSynonym("电影", "电影院");
        addSynonym("电影", "看电影");
        addSynonym("电影", "影视作品");
        addSynonym("电影", "电影片");
        addSynonym("电影", "观影");
        addSynonym("游戏", "玩游戏");
        addSynonym("游戏", "打游戏");
        addSynonym("游戏", "游戏娱乐");
        addSynonym("游戏", "电子游戏");
        addSynonym("游戏", "游戏时间");
        addSynonym("游戏", "游戏体验");
        addSynonym("读书", "阅读");
        addSynonym("读书", "看书");
        addSynonym("读书", "阅读书籍");
        addSynonym("读书", "读书籍");
        addSynonym("读书", "读小说");
        addSynonym("读书", "阅读文章");
        addSynonym("运动", "锻炼");
        addSynonym("运动", "健身");
        addSynonym("运动", "体育运动");
        addSynonym("运动", "运动健身");
        addSynonym("运动", "做运动");
        addSynonym("运动", "体育锻炼");
        addSynonym("朋友", "好友");
        addSynonym("朋友", "伙伴");
        addSynonym("朋友", "好朋友");
        addSynonym("朋友", "友人");
        addSynonym("朋友", "哥们");
        addSynonym("家人", "家庭成员");
        addSynonym("家人", "亲人");
        addSynonym("家人", "家里人");
        addSynonym("家人", "亲属");
        addSynonym("爱情", "恋爱");
        addSynonym("爱情", "感情");
        addSynonym("爱情", "爱情关系");
        addSynonym("爱情", "相爱");
        addSynonym("梦想", "理想");
        addSynonym("梦想", "愿望");
        addSynonym("梦想", "梦想实现");
        addSynonym("梦想", "追求梦想");
        addSynonym("未来", "将来");
        addSynonym("未来", "以后");
        addSynonym("未来", "未来生活");
        addSynonym("未来", "未来发展");
        addSynonym("天气怎么样", "天气如何");
        addSynonym("天气怎么样", "气候怎样");
        addSynonym("天气怎么样", "今天天气");
        addSynonym("天气怎么样", "天气情况");
        addSynonym("你好吗", "你怎么样");
        addSynonym("你好吗", "最近好吗");
        addSynonym("你好吗", "过得如何");
        addSynonym("你好吗", "还好吗");
        addSynonym("多少钱", "价格多少");
        addSynonym("多少钱", "多少钱啊");
        addSynonym("多少钱", "价位");
        addSynonym("多少钱", "价格如何");
        addSynonym("在哪里", "在哪儿");
        addSynonym("在哪里", "什么地方");
        addSynonym("在哪里", "哪个地方");
        addSynonym("在哪里", "何处");
        addSynonym("为什么", "为何");
        addSynonym("为什么", "为啥");
        addSynonym("为什么", "什么原因");
        addSynonym("为什么", "怎么回事");
        addSynonym("怎么办", "怎么处理");
        addSynonym("怎么办", "如何解决");
        addSynonym("怎么办", "该怎么做");
        addSynonym("怎么办", "怎样应对");
        addSynonym("好吃吗", "味道如何");
        addSynonym("好吃吗", "好吃不好吃");
        addSynonym("好吃吗", "口味怎样");
        addSynonym("好吃吗", "美味吗");
        addSynonym("漂亮", "美丽");
        addSynonym("漂亮", "好看");
        addSynonym("漂亮", "漂亮啊");
        addSynonym("漂亮", "美丽动人");
        addSynonym("聪明", "机智");
        addSynonym("聪明", "聪慧");
        addSynonym("聪明", "聪明伶俐");
        addSynonym("聪明", "智慧");
        addSynonym("无聊", "无趣");
        addSynonym("无聊", "没意思");
        addSynonym("无聊", "枯燥");
        addSynonym("无聊", "乏味");
        addSynonym("寂寞", "孤独");
        addSynonym("寂寞", "孤单");
        addSynonym("寂寞", "寂寞啊");
        addSynonym("寂寞", "孤独感");
        addSynonym("压力", "压力大");
        addSynonym("压力", "压力山大");
        addSynonym("压力", "压力感");
        addSynonym("压力", "工作压力");
        addSynonym("成功", "成功啦");
        addSynonym("成功", "成功了");
        addSynonym("成功", "取得成就");
        addSynonym("成功", "获得成功");
        addSynonym("失败", "失败了");
        addSynonym("失败", "没成功");
        addSynonym("失败", "失利");
        addSynonym("失败", "失败了啊");
        addSynonym("旅行", "旅游");
        addSynonym("旅行", "出游");
        addSynonym("旅行", "旅行游玩");
        addSynonym("旅行", "旅行度假");
        addSynonym("旅行", "旅游观光");
        addSynonym("旅行", "出去旅行");
        addSynonym("美食", "美味食物");
        addSynonym("美食", "好吃的");
        addSynonym("美食", "美食佳肴");
        addSynonym("美食", "美味佳肴");
        addSynonym("美食", "美食体验");
        addSynonym("美食", "美食文化");
        addSynonym("健康", "身体健康");
        addSynonym("健康", "健康状态");
        addSynonym("健康", "健康状况");
        addSynonym("健康", "身体好");
        addSynonym("健康", "健康生活");
        addSynonym("健康", "养生");
        addSynonym("财富", "钱财");
        addSynonym("财富", "金钱");
        addSynonym("财富", "财产");
        addSynonym("财富", "财富积累");
        addSynonym("财富", "资产");
        addSynonym("财富", "财富管理");
        addSynonym("财富", "经济状况");
        addSynonym("科技", "科学技术");
        addSynonym("科技", "科技发展");
        addSynonym("科技", "高科技");
        addSynonym("科技", "科技进步");
        addSynonym("科技", "技术创新");
        addSynonym("科技", "科技产品");
        addSynonym("艺术", "艺术作品");
        addSynonym("艺术", "艺术创作");
        addSynonym("艺术", "艺术表现");
        addSynonym("艺术", "艺术形式");
        addSynonym("艺术", "艺术欣赏");
        addSynonym("艺术", "艺术文化");
        addSynonym("幽默", "风趣");
        addSynonym("幽默", "幽默感");
        addSynonym("幽默", "诙谐");
        addSynonym("幽默", "有趣");
        addSynonym("幽默", "幽默风趣");
        addSynonym("幽默", "搞笑");
        addSynonym("幽默", "逗趣");
        addSynonym("天气冷", "寒冷");
        addSynonym("天气冷", "冷啊");
        addSynonym("天气冷", "好冷");
        addSynonym("天气冷", "温度低");
        addSynonym("天气冷", "寒冷天气");
        addSynonym("天气冷", "冷得很");
        addSynonym("天气冷", "冻人");
        addSynonym("天气热", "炎热");
        addSynonym("天气热", "热啊");
        addSynonym("天气热", "好热");
        addSynonym("天气热", "温度高");
        addSynonym("天气热", "炎热天气");
        addSynonym("天气热", "热得很");
        addSynonym("天气热", "酷热");
        addSynonym("下雨了", "下雨啦");
        addSynonym("下雨了", "下雨天");
        addSynonym("下雨了", "降雨");
        addSynonym("下雨了", "下雨天气");
        addSynonym("下雨了", "雨天");
        addSynonym("下雨了", "下着雨");
        addSynonym("下雪了", "下雪啦");
        addSynonym("下雪了", "下雪天");
        addSynonym("下雪了", "降雪");
        addSynonym("下雪了", "雪天");
        addSynonym("下雪了", "雪花飘飘");
        addSynonym("下雪了", "白雪纷飞");
        addSynonym("周末", "星期六星期天");
        addSynonym("周末", "周末时间");
        addSynonym("周末", "周末休息");
        addSynonym("周末", "周末假期");
        addSynonym("周末", "周末愉快");
        addSynonym("假期", "节假日");
        addSynonym("假期", "放假");
        addSynonym("假期", "休假");
        addSynonym("假期", "假期时间");
        addSynonym("假期", "长假");
        addSynonym("假期", "短假");
        addSynonym("假期", "假期生活");
        addSynonym("生日", "生日啦");
        addSynonym("生日", "过生日");
        addSynonym("生日", "生日那天");
        addSynonym("生日", "生辰");
        addSynonym("生日", "生日聚会");
        addSynonym("生日", "生日庆祝");
        addSynonym("节日", "节假日");
        addSynonym("节日", "庆典");
        addSynonym("节日", "节日活动");
        addSynonym("节日", "传统节日");
        addSynonym("节日", "节日气氛");
        addSynonym("节日", "节日快乐");
        addSynonym("新年", "春节");
        addSynonym("新年", "元旦");
        addSynonym("新年", "新年快乐");
        addSynonym("新年", "过年");
        addSynonym("新年", "新春");
        addSynonym("新年", "新年到来");
        addSynonym("新年", "新年好");
        addSynonym("圣诞", "圣诞节");
        addSynonym("圣诞", "圣诞快乐");
        addSynonym("圣诞", "圣诞节日");
        addSynonym("圣诞", "圣诞老人");
        addSynonym("圣诞", "圣诞礼物");
        addSynonym("圣诞", "圣诞树");
        addSynonym("中秋", "中秋节");
        addSynonym("中秋", "中秋快乐");
        addSynonym("中秋", "月饼节");
        addSynonym("中秋", "团圆节");
        addSynonym("中秋", "中秋佳节");
        addSynonym("中秋", "月圆之夜");
        addSynonym("好的", "好的呀");
        addSynonym("好的", "好啊");
        addSynonym("好的", "行");
        addSynonym("好的", "可以");
        addSynonym("好的", "没问题");
        addSynonym("好的", "ok");
        addSynonym("好的", "okay");
        addSynonym("好的", "好的好的");
        addSynonym("是的", "是的呀");
        addSynonym("是的", "对啊");
        addSynonym("是的", "没错");
        addSynonym("是的", "确实");
        addSynonym("是的", "的确");
        addSynonym("是的", "是这样");
        addSynonym("是的", "是的呢");
        addSynonym("不是", "不是啊");
        addSynonym("不是", "不对");
        addSynonym("不是", "不是的");
        addSynonym("不是", "错啦");
        addSynonym("不是", "不正确");
        addSynonym("不是", "不是这样");
        addSynonym("也许", "可能吧");
        addSynonym("也许", "或许");
        addSynonym("也许", "说不定");
        addSynonym("也许", "有可能");
        addSynonym("也许", "大概");
        addSynonym("也许", "也许吧");
        addSynonym("真的吗", "真的啊");
        addSynonym("真的吗", "当真");
        addSynonym("真的吗", "确实吗");
        addSynonym("真的吗", "真的假的");
        addSynonym("真的吗", "是真的吗");
        addSynonym("真的吗", "确定吗");
        addSynonym("开玩笑", "说笑");
        addSynonym("开玩笑", "闹着玩");
        addSynonym("开玩笑", "开玩笑的");
        addSynonym("开玩笑", "逗你玩");
        addSynonym("开玩笑", "说笑话");
        addSynonym("开玩笑", "开玩笑啦");
        addSynonym("猜猜看", "猜一猜");
        addSynonym("猜猜看", "你猜");
        addSynonym("猜猜看", "猜猜");
        addSynonym("猜猜看", "猜一下");
        addSynonym("猜猜看", "猜猜看呀");
        addSynonym("猜猜看", "猜猜是谁");
        addSynonym("秘密", "机密");
        addSynonym("秘密", "隐秘");
        addSynonym("秘密", "秘密的事");
        addSynonym("秘密", "不能说的秘密");
        addSynonym("秘密", "保密");
        addSynonym("秘密", "隐秘的事");
        addSynonym("幸运", "好运");
        addSynonym("幸运", "幸运儿");
        addSynonym("幸运", "走运");
        addSynonym("幸运", "幸运降临");
        addSynonym("幸运", "幸运之神");
        addSynonym("幸运", "好运气");
        addSynonym("惊喜", "意外惊喜");
        addSynonym("惊喜", "惊喜不断");
        addSynonym("惊喜", "惊喜时刻");
        addSynonym("惊喜", "惊喜连连");
        addSynonym("惊喜", "惊喜发现");
        addSynonym("帮助", "帮忙");
        addSynonym("帮助", "协助");
        addSynonym("帮助", "援助");
        addSynonym("帮助", "帮帮忙");
        addSynonym("帮助", "帮助他人");
        addSynonym("帮助", "提供帮助");
        addSynonym("问题", "疑问");
        addSynonym("问题", "难题");
        addSynonym("问题", "问题来了");
        addSynonym("问题", "有问题");
        addSynonym("问题", "疑问问题");
        addSynonym("问题", "困惑");
        addSynonym("等待", "等候");
        addSynonym("等待", "等着");
        addSynonym("等待", "等待中");
        addSynonym("等待", "稍等");
        addSynonym("等待", "等一会儿");
        addSynonym("等待", "等待时间");
        addSynonym("快点", "快一点");
        addSynonym("快点", "赶快");
        addSynonym("快点", "赶紧");
        addSynonym("快点", "快些");
        addSynonym("快点", "快点儿");
        addSynonym("快点", "加快速度");
        addSynonym("停止", "停下");
        addSynonym("停止", "终止");
        addSynonym("停止", "停止运行");
        addSynonym("停止", "停下来");
        addSynonym("停止", "不再继续");
        addSynonym("停止", "中止");
        addSynonym("继续", "接着");
        addSynonym("继续", "继续下去");
        addSynonym("继续", "继续进行");
        addSynonym("继续", "继续啊");
        addSynonym("继续", "接着来");
        addSynonym("继续", "继续做");
        addSynonym("重复", "再说一次");
        addSynonym("重复", "重复一遍");
        addSynonym("重复", "重说");
        addSynonym("重复", "复述");
        addSynonym("重复", "重新说");
        addSynonym("重复", "再说一遍");
        addSynonym("明白了吗", "懂了吗");
        addSynonym("明白了吗", "理解了吗");
        addSynonym("明白了吗", "清楚了吗");
        addSynonym("明白了吗", "知道了吗");
        addSynonym("明白了吗", "了解了吗");
        addSynonym("确认", "确定");
        addSynonym("确认", "确认一下");
        addSynonym("确认", "核实");
        addSynonym("确认", "确认无误");
        addSynonym("确认", "确认完成");
        addSynonym("确认", "确认通过");
        addSynonym("取消", "撤销");
        addSynonym("取消", "取消操作");
        addSynonym("取消", "作废");
        addSynonym("取消", "取消掉");
        addSynonym("取消", "取消执行");
        addSynonym("取消", "取消订单");
        addSynonym("开始", "启动");
        addSynonym("开始", "开始进行");
        addSynonym("开始", "开始做");
        addSynonym("开始", "开始运行");
        addSynonym("开始", "启动程序");
        addSynonym("开始", "开始啦");
        addSynonym("完成", "做完");
        addSynonym("完成", "完成啦");
        addSynonym("完成", "搞定");
        addSynonym("完成", "完毕");
        addSynonym("完成", "已完成");
        addSynonym("完成", "完成任务");
        addSynonym("正确", "对的");
        addSynonym("正确", "正确无误");
        addSynonym("正确", "准确");
        addSynonym("正确", "没错");
        addSynonym("正确", "正确解答");
        addSynonym("正确", "正确答案");
        addSynonym("错误", "不对");
        addSynonym("错误", "错误答案");
        addSynonym("错误", "有误");
        addSynonym("错误", "不正确");
        addSynonym("错误", "错误信息");
        addSynonym("错误", "错误操作");
        addSynonym("同意", "赞同");
        addSynonym("同意", "赞成");
        addSynonym("同意", "认可");
        addSynonym("同意", "同意意见");
        addSynonym("同意", "同意看法");
        addSynonym("同意", "支持");
        addSynonym("反对", "不同意");
        addSynonym("反对", "不赞成");
        addSynonym("反对", "反对意见");
        addSynonym("反对", "持反对意见");
        addSynonym("反对", "不认可");
        addSynonym("反对", "不支持");
        addSynonym("建议", "提议");
        addSynonym("建议", "建议意见");
        addSynonym("建议", "建言");
        addSynonym("建议", "给出建议");
        addSynonym("建议", "建议方案");
        addSynonym("建议", "建议措施");
        addSynonym("理由", "原因");
        addSynonym("理由", "缘由");
        addSynonym("理由", "理由说明");
        addSynonym("理由", "原因解释");
        addSynonym("理由", "理由依据");
        addSynonym("理由", "道理");
        addSynonym("例子", "实例");
        addSynonym("例子", "示例");
        addSynonym("例子", "案例");
        addSynonym("例子", "举例");
        addSynonym("例子", "范例");
        addSynonym("例子", "例子说明");
        addSynonym("例子", "具体例子");
        addSynonym("比较", "对比");
        addSynonym("比较", "相比较");
        addSynonym("比较", "比较分析");
        addSynonym("比较", "比较一下");
        addSynonym("比较", "对比分析");
        addSynonym("比较", "相对比较");
        addSynonym("选择", "挑选");
        addSynonym("选择", "选定");
        addSynonym("选择", "选择项");
        addSynonym("选择", "做选择");
        addSynonym("选择", "选择方案");
        addSynonym("选择", "选择决定");
        addSynonym("决定", "决策");
        addSynonym("决定", "做决定");
        addSynonym("决定", "决定下来");
        addSynonym("决定", "最终决定");
        addSynonym("决定", "决定方案");
        addSynonym("决定", "决定意见");
        addSynonym("改变", "变更");
        addSynonym("改变", "改变主意");
        addSynonym("改变", "改变想法");
        addSynonym("改变", "发生变化");
        addSynonym("改变", "改变计划");
        addSynonym("改变", "改变方式");
        addSynonym("计划", "规划");
        addSynonym("计划", "计划安排");
        addSynonym("计划", "计划方案");
        addSynonym("计划", "制定计划");
        addSynonym("计划", "计划表");
        addSynonym("计划", "计划书");
        addSynonym("目标", "目的");
        addSynonym("目标", "目标设定");
        addSynonym("目标", "奋斗目标");
        addSynonym("目标", "目标方向");
        addSynonym("目标", "目标计划");
        addSynonym("目标", "目标达成");
    }

    private String getCurrentSeason() {
        java.time.Month month = java.time.LocalDate.now().getMonth();
        if (month.getValue() >= 3 && month.getValue() <= 5) {
            return "现在是春季，万物复苏的季节！";
        } else if (month.getValue() >= 6 && month.getValue() <= 8) {
            return "现在是夏季，注意防暑降温！";
        } else if (month.getValue() >= 9 && month.getValue() <= 11) {
            return "现在是秋季，天高气爽的好时节！";
        } else {
            return "现在是冬季，注意保暖防寒！";
        }
    }

    public String getSmartResponse(String input) {return searchAndCombine(input); }

    //主搜索方法
    public String searchAndCombine(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "请输入有效内容";
        }

        String processedInput = preprocessInput(input);

        // 收集所有匹配的结果
        List<ResponseResult> allResults = new ArrayList<>();

        // 1. 完整句子搜索
        String mainCategory = classifyInput(processedInput);
        CategoryKnowledge targetCategory = categories.get(mainCategory);
        if (targetCategory != null) {
            allResults.addAll(targetCategory.search(processedInput));
        }

        // 2. 多关键词搜索
        List<String> keywords = extractKeywords(processedInput);
        if (keywords.size() > 1) {
            allResults.addAll(searchByMultipleKeywords(keywords, mainCategory));
        }

        // 3. 单关键词搜索
        for (String keyword : keywords) {
            if (keyword.length() > 1) {
                allResults.addAll(searchBySingleKeyword(keyword));
            }
        }

        // 4. 全分类搜索（如果前面结果不够）
        if (allResults.size() < 2) {
            for (CategoryKnowledge cat : categories.values()) {
                if (!cat.getName().equals(mainCategory)) {
                    allResults.addAll(cat.search(processedInput));
                }
            }
        }

        // 5. 组合回答
        String combinedResponse = combineResponses(allResults, processedInput);

        return processDynamicContent(combinedResponse);
    }

    private String combineResponses(List<ResponseResult> results, String input) {
        if (results.isEmpty()) {
            return getDefaultResponse(input);
        }

        // 严格的去重：基于分类
        Map<String, ResponseResult> bestResultPerCategory = new LinkedHashMap<>();
        for (ResponseResult result : results) {
            String category = result.getCategory();
            ResponseResult existingResult = bestResultPerCategory.get(category);

            if (existingResult == null || result.getScore() > existingResult.getScore()) {
                bestResultPerCategory.put(category, result);
            }
        }

        List<ResponseResult> distinctResults = new ArrayList<>(bestResultPerCategory.values());

        // 按照关键词在输入中的出现顺序排序
        List<String> keywords = extractKeywords(input);
        distinctResults.sort((a, b) -> {
            int relevanceA = calculateRelevanceScore(a, keywords, input);
            int relevanceB = calculateRelevanceScore(b, keywords, input);
            return Integer.compare(relevanceB, relevanceA); // 降序排列，相关性高的在前
        });

        // 构建组合回答
        StringBuilder combinedResponse = new StringBuilder();

        if (!distinctResults.isEmpty()) {
            ResponseResult mainResult = distinctResults.get(0);
            combinedResponse.append(mainResult.getResponse());

            if (distinctResults.size() > 1) {
                combinedResponse.append("\n");

                String[] connectors = {"另外，", "还有，", "此外，", "值得一提的是，", "对了，"};
                Random random = new Random();

                for (int i = 1; i < distinctResults.size(); i++) {
                    ResponseResult otherResult = distinctResults.get(i);
                    String connector = connectors[random.nextInt(connectors.length)];
                    combinedResponse.append(connector).append(otherResult.getResponse());

                    if (i < distinctResults.size() - 1) {
                        combinedResponse.append("\n");
                    }
                }
            }
        }

        return combinedResponse.toString();
    }

    // 改进的相关性评分方法
    private int calculateRelevanceScore(ResponseResult result, List<String> keywords, String input) {
        String response = result.getResponse().toLowerCase();
        String lowerInput = input.toLowerCase();
        int score = 0;

        // 1. 完整短语匹配（最高优先级）
        for (int i = 0; i < keywords.size(); i++) {
            String keyword = keywords.get(i).toLowerCase();
            if (response.contains(keyword)) {
                // 关键词在输入中越靠前，得分越高
                score += (keywords.size() - i) * 10;
                // 完整匹配额外加分
                score += 20;
            }
        }

        // 2. 分词匹配（中等优先级）
        for (int i = 0; i < keywords.size(); i++) {
            String keyword = keywords.get(i).toLowerCase();
            if (keyword.length() > 1) {
                boolean allCharsMatch = true;
                for (char c : keyword.toCharArray()) {
                    if (!response.contains(String.valueOf(c))) {
                        allCharsMatch = false;
                        break;
                    }
                }
                if (allCharsMatch) {
                    score += (keywords.size() - i) * 5;
                }
            }
        }

        // 3. 单个字符匹配（低优先级）
        for (int i = 0; i < keywords.size(); i++) {
            String keyword = keywords.get(i).toLowerCase();
            for (char c : keyword.toCharArray()) {
                if (response.contains(String.valueOf(c))) {
                    score += (keywords.size() - i) * 1;
                    break;
                }
            }
        }

        // 4. 加上原有的匹配度得分
        score += result.getScore();

        return score;
    }
    // 精确计算关键词顺序：找到回答匹配的第一个关键词在输入中的位置
    private int calculateExactKeywordOrder(ResponseResult result, List<String> keywords, String input) {
        String response = result.getResponse().toLowerCase();

        // 找到回答中第一个出现在输入关键词列表中的关键词
        for (int i = 0; i < keywords.size(); i++) {
            String keyword = keywords.get(i).toLowerCase();
            if (response.contains(keyword)) {
                // 返回该关键词在输入中的位置（位置越靠前，顺序值越小）
                return i;
            }
        }

        // 如果没有匹配的关键词，放在最后
        return keywords.size() + 100;
    }

    // 辅助类，用于存储回答和其顺序值（如果需要的话）
    private static class ResponseResultWithOrder {
        private final ResponseResult result;
        private final int order;

        public ResponseResultWithOrder(ResponseResult result, int order) {
            this.result = result;
            this.order = order;
        }

        public ResponseResult getResult() { return result; }
        public int getOrder() { return order; }
    }

    // 提取关键词
    private List<String> extractKeywords(String input) {
        String[] words = input.split("[\\s\\p{Punct}]+");
        List<String> keywords = new ArrayList<>();

        for (String word : words) {
            if (word.length() >= 2 && !isStopWord(word)) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    // 停用词过滤
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of("的", "了", "和", "与", "或", "哪个", "怎么", "如何", "什么", "为什么");
        return stopWords.contains(word);
    }

    // 多关键词联合搜索
    private List<ResponseResult> searchByMultipleKeywords(List<String> keywords, String mainCategory) {
        // 用于记录每个分类的最佳结果
        Map<String, ResponseResult> bestResultsByCategory = new HashMap<>();

        // 对每个分类只搜索一次，但考虑所有关键词
        for (CategoryKnowledge category : categories.values()) {
            ResponseResult bestResult = null;
            int bestScore = 0;

            // 在该分类中搜索所有关键词，找到最佳匹配
            for (String keyword : keywords) {
                List<ResponseResult> keywordResults = category.search(keyword);
                for (ResponseResult result : keywordResults) {
                    int score = result.getScore();
                    if (category.getName().equals(mainCategory)) {
                        score += 2; // 主要分类加分
                    }

                    // 更新该分类的最佳结果
                    if (bestResult == null || score > bestScore) {
                        bestResult = new ResponseResult(result.getResponse(), result.getCategory(), score);
                        bestScore = score;
                    }
                }
            }

            // 如果找到了结果，添加到最终列表
            if (bestResult != null) {
                bestResultsByCategory.put(bestResult.getCategory(), bestResult);
            }
        }

        return new ArrayList<>(bestResultsByCategory.values());
    }

    // 单关键词搜索
    private List<ResponseResult> searchBySingleKeyword(String keyword) {
        Map<String, ResponseResult> categoryResults = new HashMap<>();

        for (CategoryKnowledge category : categories.values()) {
            List<ResponseResult> results = category.search(keyword);
            for (ResponseResult result : results) {
                if (!categoryResults.containsKey(result.getCategory()) ||
                        result.getScore() > categoryResults.get(result.getCategory()).getScore()) {
                    categoryResults.put(result.getCategory(), result);
                }
            }
        }

        return new ArrayList<>(categoryResults.values());
    }

    // 输入预处理
    private String preprocessInput(String input) {
        String processed = input.toLowerCase().trim();

        for (Map.Entry<String, List<String>> entry : synonyms.entrySet()) {
            for (String synonym : entry.getValue()) {
                if (processed.contains(synonym)) {
                    processed = processed.replace(synonym, entry.getKey());
                }
            }
        }

        return processed;
    }

    // 更智能的分类识别
    private String classifyInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "general";
        }

        String processedInput = preprocessInput(input);

        // 多级分类识别
        Map<String, Integer> categoryScores = new HashMap<>();

        // 1. 精确匹配（最高优先级）
        applyExactMatch(processedInput, categoryScores);

        // 2. 问题类型识别
        applyQuestionTypeAnalysis(processedInput, categoryScores);

        // 3. 关键词匹配
        applyKeywordMatching(processedInput, categoryScores);

        // 4. 语义分析
        applySemanticAnalysis(processedInput, categoryScores);

        // 5. 上下文关联（如果有上下文功能）
        applyContextAwareness(processedInput, categoryScores);

        // 6. 特殊模式识别
        applyPatternRecognition(processedInput, categoryScores);

        // 返回得分最高的分类
        return getBestCategory(categoryScores, processedInput);
    }

    // 1. 精确匹配 - 最高优先级
    private void applyExactMatch(String input, Map<String, Integer> categoryScores) {
        Map<String, String[]> exactMatches = new HashMap<>();

        // 问候类精确匹配
        exactMatches.put("greeting", new String[]{
                "你好", "您好", "hello", "hi", "嗨", "早上好", "下午好", "晚上好",
                "早安", "午安", "晚安", "hey", "hola", "你好呀", "您好呀", "在吗"
        });

        // 告别类精确匹配
        exactMatches.put("farewell", new String[]{
                "再见", "拜拜", "再会", "bye", "goodbye", "拜拜啦", "再见啦",
                "下次见", "回头见", "告辞", "再见了", "see you", "see ya"
        });

        // 感谢类精确匹配
        exactMatches.put("courtesy", new String[]{
                "谢谢", "感谢", "多谢", "谢谢你", "感谢你", "太感谢了", "十分感谢"
        });

        // 道歉类精确匹配
        exactMatches.put("courtesy", new String[]{
                "对不起", "抱歉", "不好意思", "请原谅", "是我的错"
        });

        for (Map.Entry<String, String[]> entry : exactMatches.entrySet()) {
            for (String exactWord : entry.getValue()) {
                if (input.equals(exactWord) || input.equals(exactWord + "！") || input.equals(exactWord + "!")) {
                    categoryScores.put(entry.getKey(), categoryScores.getOrDefault(entry.getKey(), 0) + 20);
                }
            }
        }
    }

    // 2. 问题类型分析
    private void applyQuestionTypeAnalysis(String input, Map<String, Integer> categoryScores) {
        // 定义问题类型关键词和对应的分类
        Map<String, String[]> questionPatterns = new HashMap<>();

        // 如何/怎么 类问题
        questionPatterns.put("education", new String[]{"怎么", "如何", "怎样", "怎么办", "如何处理", "如何解决"});
        questionPatterns.put("help", new String[]{"怎么", "如何", "怎样", "怎么办"});

        // 为什么 类问题
        questionPatterns.put("education", new String[]{"为什么", "为何", "为啥", "什么原因", "怎么回事"});
        questionPatterns.put("science", new String[]{"为什么", "原理", "机制", "科学原理"});

        // 什么 类问题
        questionPatterns.put("education", new String[]{"什么是", "什么叫", "哪些", "有什么", "是什么"});
        questionPatterns.put("general", new String[]{"什么是", "有什么", "是什么"});

        // 哪个/比较 类问题
        questionPatterns.put("general", new String[]{"哪个", "比较", "还是", "更好", "最好"});

        // 时间 类问题
        questionPatterns.put("time", new String[]{"什么时候", "何时", "几点", "多久", "多长时间"});

        // 地点 类问题
        questionPatterns.put("travel", new String[]{"哪里", "哪儿", "什么地方", "在哪里", "去哪儿"});

        for (Map.Entry<String, String[]> entry : questionPatterns.entrySet()) {
            for (String pattern : entry.getValue()) {
                if (input.contains(pattern)) {
                    categoryScores.put(entry.getKey(), categoryScores.getOrDefault(entry.getKey(), 0) + 8);
                }
            }
        }
    }

    // 3. 智能关键词匹配（带权重和语义扩展）
    private void applyKeywordMatching(String input, Map<String, Integer> categoryScores) {
        // 分类关键词库（带权重）
        Map<String, Map<String, Integer>> smartKeywords = new HashMap<>();

        // 个人信息类（高权重）
        Map<String, Integer> personalKeywords = new HashMap<>();
        personalKeywords.put("你是谁", 10);
        personalKeywords.put("你叫什么", 10);
        personalKeywords.put("你多大了", 8);
        personalKeywords.put("你是男", 8);
        personalKeywords.put("你是女", 8);
        personalKeywords.put("你住在", 8);
        personalKeywords.put("你会什么", 8);
        personalKeywords.put("谁创造", 8);
        personalKeywords.put("有感情", 6);
        smartKeywords.put("personal", personalKeywords);

        // 情感类（高权重）
        Map<String, Integer> emotionKeywords = new HashMap<>();
        emotionKeywords.put("开心", 8);
        emotionKeywords.put("高兴", 8);
        emotionKeywords.put("快乐", 8);
        emotionKeywords.put("难过", 9);
        emotionKeywords.put("伤心", 9);
        emotionKeywords.put("悲伤", 9);
        emotionKeywords.put("生气", 9);
        emotionKeywords.put("愤怒", 9);
        emotionKeywords.put("害怕", 8);
        emotionKeywords.put("恐惧", 8);
        emotionKeywords.put("焦虑", 9);
        emotionKeywords.put("压力", 9);
        emotionKeywords.put("抑郁", 9);
        emotionKeywords.put("孤独", 8);
        emotionKeywords.put("失望", 8);
        emotionKeywords.put("累", 7);
        emotionKeywords.put("无聊", 7);
        smartKeywords.put("emotion", emotionKeywords);

        // 心理类
        Map<String, Integer> psychologyKeywords = new HashMap<>();
        psychologyKeywords.put("心理", 8);
        psychologyKeywords.put("心态", 7);
        psychologyKeywords.put("自信", 7);
        psychologyKeywords.put("自卑", 7);
        psychologyKeywords.put("压力大", 9);
        psychologyKeywords.put("焦虑症", 9);
        psychologyKeywords.put("抑郁症", 9);
        psychologyKeywords.put("心理咨询", 8);
        psychologyKeywords.put("心理医生", 8);
        smartKeywords.put("psychology", psychologyKeywords);

        // 学习类
        Map<String, Integer> educationKeywords = new HashMap<>();
        educationKeywords.put("学习", 7);
        educationKeywords.put("读书", 7);
        educationKeywords.put("教育", 6);
        educationKeywords.put("学校", 6);
        educationKeywords.put("老师", 6);
        educationKeywords.put("学生", 6);
        educationKeywords.put("课程", 6);
        educationKeywords.put("考试", 8);
        educationKeywords.put("作业", 7);
        educationKeywords.put("论文", 7);
        educationKeywords.put("研究", 6);
        educationKeywords.put("知识", 6);
        educationKeywords.put("技能", 6);
        educationKeywords.put("培训", 6);
        educationKeywords.put("自学", 7);
        educationKeywords.put("复习", 7);
        educationKeywords.put("预习", 7);
        smartKeywords.put("education", educationKeywords);

        // 编程技术类
        Map<String, Integer> programmingKeywords = new HashMap<>();
        programmingKeywords.put("java", 9);
        programmingKeywords.put("python", 9);
        programmingKeywords.put("编程", 8);
        programmingKeywords.put("代码", 8);
        programmingKeywords.put("算法", 8);
        programmingKeywords.put("程序", 7);
        programmingKeywords.put("写代码", 8);
        programmingKeywords.put("程序设计", 7);
        programmingKeywords.put("coding", 7);
        programmingKeywords.put("前端", 8);
        programmingKeywords.put("后端", 8);
        programmingKeywords.put("人工智能", 8);
        programmingKeywords.put("机器学习", 8);
        programmingKeywords.put("大数据", 7);
        programmingKeywords.put("数据库", 7);
        programmingKeywords.put("网络安全", 7);
        smartKeywords.put("programming", programmingKeywords);

        // 应用关键词匹配
        for (Map.Entry<String, Map<String, Integer>> categoryEntry : smartKeywords.entrySet()) {
            String category = categoryEntry.getKey();
            Map<String, Integer> keywords = categoryEntry.getValue();

            for (Map.Entry<String, Integer> keywordEntry : keywords.entrySet()) {
                String keyword = keywordEntry.getKey();
                int weight = keywordEntry.getValue();

                if (input.contains(keyword)) {
                    int baseScore = weight;
                    // 位置加成
                    if (input.startsWith(keyword)) baseScore += 3;
                    if (input.endsWith(keyword)) baseScore += 2;
                    // 长度加成（短问题更可能专注于该主题）
                    if (input.length() < 10) baseScore += 2;

                    categoryScores.put(category, categoryScores.getOrDefault(category, 0) + baseScore);
                }
            }
        }
    }

    // 4. 语义分析
    private void applySemanticAnalysis(String input, Map<String, Integer> categoryScores) {
        // 情感倾向分析
        if (hasEmotionalTone(input)) {
            categoryScores.put("emotion", categoryScores.getOrDefault("emotion", 0) + 5);
            categoryScores.put("psychology", categoryScores.getOrDefault("psychology", 0) + 3);
        }

        // 求知倾向分析
        if (hasLearningIntent(input)) {
            categoryScores.put("education", categoryScores.getOrDefault("education", 0) + 4);
        }

        // 求助倾向分析
        if (hasHelpIntent(input)) {
            categoryScores.put("help", categoryScores.getOrDefault("help", 0) + 6);
        }

        // 娱乐倾向分析
        if (hasEntertainmentIntent(input)) {
            categoryScores.put("entertainment", categoryScores.getOrDefault("entertainment", 0) + 5);
        }
    }

    // 5. 上下文感知（简单版本）
    private void applyContextAwareness(String input, Map<String, Integer> categoryScores) {
        // 这里可以集成上下文管理
        // 例如：如果上一条消息是情感类，这条消息也倾向于情感类

    }

    // 6. 特殊模式识别
    private void applyPatternRecognition(String input, Map<String, Integer> categoryScores) {
        // 笑话请求模式
        if (input.matches(".*(讲个?笑话|说个?笑话|来点笑话|幽默一下).*")) {
            categoryScores.put("entertainment", categoryScores.getOrDefault("entertainment", 0) + 15);
        }

        // 时间询问模式
        if (input.matches(".*(现在几点|几点钟?了?|什么时候|何时).*")) {
            categoryScores.put("time", categoryScores.getOrDefault("time", 0) + 12);
        }

        // 天气询问模式
        if (input.matches(".*(天气怎么样?|天气预报|下雨吗|下雪吗).*")) {
            categoryScores.put("weather", categoryScores.getOrDefault("weather", 0) + 12);
        }

        // 自我介绍模式
        if (input.matches(".*(介绍.*自己|关于.*你|你.*是谁).*")) {
            categoryScores.put("personal", categoryScores.getOrDefault("personal", 0) + 15);
        }
    }

    // 情感语调分析
    private boolean hasEmotionalTone(String input) {
        String[] emotionalWords = {"心情", "感觉", "情绪", "心态", "开心", "难过", "生气", "害怕", "焦虑", "压力"};
        for (String word : emotionalWords) {
            if (input.contains(word)) return true;
        }
        return false;
    }

    // 学习意图分析
    private boolean hasLearningIntent(String input) {
        String[] learningWords = {"学习", "学会", "掌握", "了解", "知道", "明白", "理解", "研究", "知识"};
        for (String word : learningWords) {
            if (input.contains(word)) return true;
        }
        return false;
    }

    // 求助意图分析
    private boolean hasHelpIntent(String input) {
        String[] helpWords = {"帮帮我", "求助", "请教", "请问", "该怎么办", "怎么办", "如何", "怎样"};
        for (String word : helpWords) {
            if (input.contains(word)) return true;
        }
        return false;
    }

    // 娱乐意图分析
    private boolean hasEntertainmentIntent(String input) {
        String[] entertainmentWords = {"笑话", "搞笑", "幽默", "好玩", "有趣", "娱乐", "放松", "休闲"};
        for (String word : entertainmentWords) {
            if (input.contains(word)) return true;
        }
        return false;
    }

    // 获取最佳分类
    private String getBestCategory(Map<String, Integer> categoryScores, String input) {
        if (categoryScores.isEmpty()) {
            return "general";
        }

        // 找到最高分
        int maxScore = Collections.max(categoryScores.values());

        // 获取所有达到最高分的分类
        List<String> topCategories = categoryScores.entrySet().stream()
                .filter(entry -> entry.getValue() == maxScore)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 如果只有一个最高分分类，直接返回
        if (topCategories.size() == 1) {
            return topCategories.get(0);
        }

        // 多个分类同分时，使用优先级规则
        return resolveCategoryTie(topCategories, input, maxScore);
    }

    // 解决分类平局
    private String resolveCategoryTie(List<String> topCategories, String input, int score) {
        // 定义分类优先级
        Map<String, Integer> categoryPriority = new HashMap<>();
        categoryPriority.put("personal", 100);  // 个人信息最高优先级
        categoryPriority.put("farewell", 90);   // 告别次之
        categoryPriority.put("greeting", 80);   // 问候
        categoryPriority.put("courtesy", 70);   // 感谢道歉
        categoryPriority.put("help", 60);       // 求助
        categoryPriority.put("emotion", 50);    // 情感

        // 按优先级排序
        return topCategories.stream()
                .max(Comparator.comparing(cat -> categoryPriority.getOrDefault(cat, 0)))
                .orElse("general");
    }

    // 获取默认回答
    private String getDefaultResponse(String input) {
        String[] defaultResponses = {
                "这个问题很有趣，让我想想...",
                "我不太确定，能换个方式问吗？",
                "我正在学习这个，以后告诉你！",
                "你能说得更具体些吗？",
                "这个问题超出了我目前的知识范围。",
                //友好型回答
                "哇，这个问题很有深度！",
                "让我思考一下怎么回答你最好...",
                "这个问题我需要更多信息才能准确回答",
                "你的问题让我学到了新东西！",
                "这个领域我还需要多学习",
                "你的好奇心让我很欣赏！",
                "这个问题激发了我的学习兴趣",
                "感谢你提出这个有趣的话题",
                "我会努力提升自己来回答这类问题",
                //幽默型回答
                "哎呀，我的知识库在这里有个小缺口",
                "这个问题难倒我了，我得去充充电",
                "看来我需要升级一下我的大脑版本",
                "我的处理器正在飞速运转，但还是没找到答案",
                "这个问题让我有点懵圈了",
                "我的大脑芯片对这个话题有点卡顿",
                "这个问题需要更高级的思考模式",
                "让我联系一下云端知识库...信号不太好",
                "这个问题的答案正在加载中...",
                "我需要一点时间来组织语言",
                //引导型回答
                "你能换个角度描述一下这个问题吗？",
                "关于这个话题，你有什么具体的疑问？",
                "我们可以从另一个相关的问题开始讨论",
                "也许你可以告诉我更多背景信息？",
                "让我确认一下，你想了解的是...？",
                "这个问题涉及多个方面，你想先从哪个角度了解？",
                "我可以帮你梳理一下问题的关键点",
                "我们可以一起寻找这个问题的答案",
                "也许我们可以从基础概念开始讨论",
                "你的想法是什么？我们可以交流一下",
                //积极型回答
                "很好的问题！我会记下来学习",
                "你的探索精神很棒！",
                "这个问题值得我们深入探讨",
                "保持好奇心，这是很宝贵的品质",
                "不要灰心，我们可以尝试其他问题",
                "每个问题都是学习的机会",
                "继续提问，我们一起学习成长",
                "你的问题很有价值，我会认真研究",
                "这个问题引导我们思考更深层的东西",
                "在求知的道路上，我们都在前行",
                //专业型回答
                "这个主题需要更专业的知识储备",
                "在我的训练数据中，这方面的信息有限",
                "建议咨询相关领域的专家获得准确答案",
                "这个问题涉及的专业深度超出了我的能力",
                "我会将这个问题加入学习清单",
                "这个知识点我还没点亮呢",
                "技能冷却中，暂时无法回答",
                "我的数据库需要更新这个模块",
                "这个问题不在我的技能树里",
                "看来得去刷一下经验值了",
                //亲切型回答
                "不好意思呀，这个我还不太懂",
                "让我再想想，或者你可以问我其他问题",
                "这个我得承认我不太会",
                "咱们聊点别的我会的更拿手的话题？",
                "哎呀，被你问住了呢",
                "这个我还真不知道，你能教教我吗？",
                "我还在学习中，这个问题有点超纲了",
                "看来我得去补补课了",
                "这个问题对我来说有点挑战性",
                "我们一起学习这个新知识好不好？",
                //实用型回答
                "我建议你可以通过搜索引擎了解更多",
                "相关的书籍或课程可能会有详细解答",
                "实践可能是理解这个问题的最好方式",
                "你可以尝试从这几个方面入手研究...",
                "这个问题在专业论坛上可能有详细讨论",
                "也许实际操作会比理论解释更清楚",
                "我推荐你查阅相关的专业资料",
                "这个问题需要结合实际案例来理解",
                "你可以尝试用不同的关键词搜索",
                "实践出真知，动手试试看吧",
                //诗意型回答
                "知识的海洋浩瀚无垠，我还在探索中",
                "这个问题像一扇未开启的门，等待被发现",
                "每个未知都是新学习的起点",
                "在求知的道路上，我们都在前行",
                "智慧的种子需要时间才能发芽",
                "这个问题像星空中的一颗未知星辰",
                "探索的过程本身就是一种收获",
                "有时候问题比答案更有意义",
                "知识的边界总是在不断扩展",
                "理解是一个渐进的过程",
                //现代网络型回答
                "这个知识点我还没点亮呢",
                "技能冷却中，暂时无法回答",
                "我的数据库需要更新这个模块",
                "这个问题不在我的技能树里",
                "看来得去刷一下经验值了",
                "这个功能还在开发中...",
                "系统更新中，请稍后再试",
                "这个查询超出了我的API限制",
                "正在连接到知识网络...连接失败",
                "内存不足，无法处理这个请求",
                //国际化型回答
                "I'm still learning about this topic",
                "This is beyond my current knowledge base",
                "Let's explore other questions together",
                "My apologies, I need more training on this",
                "This is an interesting area for me to study",
                "I'm expanding my knowledge in this field",
                "This question is outside my expertise",
                "I'd love to learn more about this with you",
                "My knowledge has its limits in this area",
                "Let me research this and get back to you",
                //哲学型回答
                "有些问题的价值在于探索过程本身",
                "每个问题都蕴含着新的认知可能",
                "知识的边界总是在不断扩展",
                "理解是一个渐进的过程",
                "这个问题引导我们思考更深层的东西",
                "未知是智慧的起点",
                "提问本身就是一种智慧",
                "在思考中我们不断成长",
                "每个问题都是认识世界的一扇窗",
                "探索真理的道路永无止境",
                //合作型回答
                "我们可以一起寻找这个问题的答案",
                "让我帮你梳理一下问题的关键点",
                "也许我们可以从基础概念开始讨论",
                "你的想法是什么？我们可以交流一下",
                "这个问题值得我们深入探讨",
                "让我们一起探索这个未知领域",
                "你的视角很有启发性，我们继续讨论",
                "我们可以互相学习，共同进步",
                "这个问题需要集思广益",
                "让我们携手解决这个难题",
                //情景适配型回答
                "能多说一点吗？太简短了我不好理解",
                "虽然你描述了很多，但核心问题是什么？",
                "我理解你的意思，但需要更具体的信息",
                "这个问题包含多个层面，你想重点了解哪个？",
                "让我确认一下，你的主要疑问是...",
                "这个问题很有意义，让我们一步步分析",
                "我感受到了你的求知欲，这很棒",
                "你的问题打开了新的思考方向",
                "这个问题让我看到了不同的可能性",
                "让我们继续探索这个有趣的话题",
                //动态回答（根据输入特征）
                input.length() < 3 ? "能多说一点吗？太简短了我不好理解" :
                        input.length() > 50 ? "虽然你描述了很多，但核心问题是什么？" :
                                "这个问题很有意思，让我想想...",
                //时间相关回答
                getTimeBasedDefaultResponse(),
                //鼓励型回答
                "不要灰心，我们可以尝试其他问题",
                "每个问题都是学习的机会",
                "你的探索精神很棒！",
                "继续提问，我们一起学习成长",
                "保持好奇心，这是很宝贵的品质",
                "你的坚持让我很佩服",
                "每个未知都是进步的机会",
                "你的问题很有价值",
                "这种探索精神值得鼓励",
                "相信你会找到答案的"
        };

        int index = getSmartResponseIndex(input, defaultResponses.length);
        return defaultResponses[index];
    }

    // 基于时间的动态默认回答
    private String getTimeBasedDefaultResponse() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 6) {
            return "深夜的问题总是特别深刻...";
        } else if (hour < 12) {
            return "早晨的思维还没完全启动呢";
        } else if (hour < 18) {
            return "午后时光，适合思考复杂问题";
        } else {
            return "夜晚让问题都变得深邃起来";
        }
    }

    // 智能选择回答索引
    private int getSmartResponseIndex(String input, int responseCount) {
        int baseIndex = input.length() % responseCount;
        Random random = new Random(input.hashCode());
        int variation = random.nextInt(5);
        return (baseIndex + variation) % responseCount;
    }

    // 词库管理方法
    public void addResponse(String category, String keyword, String response) {
        if (!categories.containsKey(category)) {
            categories.put(category, new CategoryKnowledge(category));
        }
        categories.get(category).addResponse(keyword, response);
    }

    public void addSynonym(String main,String syn){ synonyms.computeIfAbsent(main,k->new ArrayList<>()).add(syn); }

    public void loadFromFiles() {
        loadKnowledgeFromFile(DEFAULT_KNOWLEDGE_FILE);
        loadSynonymsFromFile(SYNONYMS_FILE);
        loadVariedResponsesFromFile(VARIED_RESPONSES_FILE);
    }

    public void loadKnowledgeFromFile(String filename) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filename));
            String currentCategory = "general";

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentCategory = line.substring(1, line.length() - 1).trim();
                    if (!categories.containsKey(currentCategory)) {
                        categories.put(currentCategory, new CategoryKnowledge(currentCategory));
                    }
                    continue;
                }

                if (line.startsWith("#")) continue;

                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String keyword = parts[0].trim();
                    String response = processDynamicContent(parts[1].trim());

                    if (!categories.containsKey(currentCategory)) {
                        categories.put(currentCategory, new CategoryKnowledge(currentCategory));
                    }
                    categories.get(currentCategory).addResponse(keyword, response);
                }
            }


        } catch (IOException e) {
            System.err.println("加载知识库文件失败: " + e.getMessage());
        }
    }

    public void loadAllResources(){
        loadKnowledgeFromFolder("/resourcse/database/knowledge_base.txt");
        loadSynonymsFromFile("/resourcse/database/synonyms.txt");
    }

    private void loadKnowledgeFromFolder(String path){ List<String> lines = readLines(path); String current="general"; for(String line: lines){ line=line.trim(); if(line.isEmpty()) continue; if(line.startsWith("[")&&line.endsWith("]")){ current=line.substring(1,line.length()-1).trim(); categories.computeIfAbsent(current, CategoryKnowledge::new); continue;} if(line.startsWith("#")) continue; String[] parts=line.split("=",2); if(parts.length==2){ categories.computeIfAbsent(current, CategoryKnowledge::new).addResponse(parts[0].trim(), processDynamicContent(parts[1].trim())); } } }

    public void addVariedResponse(String keyword, String response) {
        variedResponses.computeIfAbsent(keyword, k -> new ArrayList<>()).add(response);
    }

    public void loadVariedResponsesFromFile(String filename) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filename));
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String keyword = parts[0].trim();
                    String[] responseList = parts[1].split("\\|");
                    for (String response : responseList) {
                        addVariedResponse(keyword, response.trim());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("加载多样化回答文件失败: " + e.getMessage());
        }
    }

    public void loadSynonymsFromFile(String filename) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filename));
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String mainWord = parts[0].trim();
                    String[] synonymList = parts[1].split(",");
                    for (String synonym : synonymList) {
                        addSynonym(mainWord, synonym.trim());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("加载同义词文件失败: " + e.getMessage());
        }
    }
    // 动态内容处理
    private String processDynamicContent(String response) {
        if (response.contains("{current_time}")) {
            String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            response = response.replace("{current_time}", currentTime);
        }

        if (response.contains("{current_date}")) {
            String currentDate = java.time.LocalDate.now().toString();
            response = response.replace("{current_date}", currentDate);
        }

        if (response.contains("{random_joke}") || response.equals("{random_joke}")) {
            String[] jokes = {
                    "为什么鸡过马路？为了到另一边！",
                    "为什么程序员不喜欢大自然？因为有太多的bug！",
                    "我有个关于栈的笑话，但是它会溢出...",
                    "为什么程序员分不清万圣节和圣诞节？因为Oct 31 == Dec 25！",
                    "程序员最喜欢的音乐类型是什么？算法蓝调！",
                    "为什么程序员总是把万圣节和圣诞节搞混？因为Oct(31) = Dec(25)",
                    "程序员最讨厌的动物是什么？Bug！",
                    "为什么程序员不喜欢出门？因为外面没有Ctrl+C和Ctrl+V",
                    "程序员为什么喜欢黑暗模式？因为光明吸引bug！",
                    "什么编程语言最适合写情书？Ruby，因为它是面向对象的！",
                    "为什么恐龙不会用电脑？因为它们已经灭绝了！",
                    "为什么海星从来不生病？因为它有海星免疫力！",
                    "什么动物最喜欢问为什么？猪！因为总是'为什么为什么'的叫~",
                    "为什么鱼不喜欢玩扑克？因为怕遇到海豹（牌）！",
                    "什么动物最会做数学？章鱼，因为它有很多算盘！",
                    "为什么面包要去见心理医生？因为它感觉有点发霉！",
                    "面条和包子打架，谁会赢？面条，因为它会'拉面'！",
                    "为什么西瓜那么重？因为它有很多'水分'！",
                    "什么水果最冷？梨（离）子，因为离冰箱最近！",
                    "为什么数学书总是很悲伤？因为它有太多问题！",
                    "学生问老师：'老师，为什么历史总是在重复？' 老师说：'因为我们在复习！'",
                    "为什么英语书那么自信？因为它有很多'tense'（时态）！",
                    "什么课最危险？化学课，因为总是有'反应'！",
                    "为什么手机要去健身房？因为它想变得更'智能'！",
                    "为什么钱包总是空的？因为钱都去'旅行'了！",
                    "为什么时钟很聪明？因为它总是知道'time'！",
                    "为什么镜子很诚实？因为它从不'反'着说！",
                    "为什么电脑永远不会感冒？因为它有Windows（窗户）！",
                    "什么网站最冷？百度，因为它有很多'冰'(Bing)！",
                    "为什么手机和电视不能结婚？因为电视总是'播放'别人的故事！",
                    "为什么Wi-Fi很浪漫？因为它总是寻找连接！",
                    "为什么夏天很诚实？因为它总是很'热'情！",
                    "什么天气最适合学习？雷阵雨，因为可以'闪电'学习！",
                    "为什么冬天很懒？因为它总是'冻'手'冻'脚！",
                    "为什么云很会安慰人？因为它总是说'不要雨（虑）'！",
                    "为什么医生总是带着笔？因为要写'处方'！",
                    "什么职业最适合睡觉？程序员，因为他们经常'休眠'！",
                    "为什么老师很会赚钱？因为他们懂得'教育'投资！",
                    "为什么厨师很快乐？因为他们有很多'调味'生活！",
                    "为什么自行车不会说话？因为它很'自行'！",
                    "什么水果最忙？芒果，因为它很'忙'（芒）！",
                    "为什么西瓜那么大方？因为它有很多'心'！",
                    "什么动物最容易摔倒？狐狸，因为它很'滑'（狐）！",
                    "什么东西越洗越脏？水！",
                    "什么门永远关不上？球门！",
                    "什么人不用电？缅甸人（免电）！",
                    "什么书在书店买不到？秘书！",
                    "为什么程序员喜欢喝咖啡？因为Java是他们的母语！",
                    "什么代码永远不会出错？// TODO: 修复这个bug",
                    "为什么AI不会迷路？因为它总是找到最优路径！",
                    "什么算法最适合谈恋爱？贪心算法，因为总是选择最好的！",
                    "为什么爸爸不喜欢玩捉迷藏？因为孩子们总是找不到他！",
                    "妈妈对儿子说：'你为什么把成绩单藏在床底下？' 儿子：'因为床底下比较安全！'",
                    "女儿问：'爸爸，为什么天会黑？' 爸爸：'因为太阳下班了！'",
                    "为什么购物车总是满的？因为它有很多'购物'欲望！",
                    "什么商品最诚实？秤，因为它从不'偏'心！",
                    "为什么打折商品很受欢迎？因为它们懂得'降价'身段！",
                    "为什么篮球很聪明？因为它懂得'投'资！",
                    "什么运动最适合程序员？乒乓球，因为总是来回'调试'！",
                    "为什么足球很受欢迎？因为它懂得'踢'动人心！",
                    "为什么钢琴很悲伤？因为它有很多'键'康问题！",
                    "什么乐器最会说话？小提琴，因为它总是'拉'家常！",
                    "为什么吉他很快乐？因为它有很多'弦'外之音！",
                    "为什么圣诞老人很健康？因为他经常'锻炼'（送礼）！",
                    "什么节日最适合程序员？愚人节，因为可以写bug不负责！",
                    "为什么春节很热闹？因为大家都在'团'聚！",
                    "为什么地图很自信？因为它知道所有'路线'！",
                    "什么旅行最省钱？梦游，因为不用买票！",
                    "为什么行李箱很累？因为它总是被'拖'着走！",
                    "为什么时间很快？因为它从不'停'留！",
                    "什么表最不准？体温表，因为它总是'发烧'！",
                    "为什么闹钟很准时？因为它懂得'叫'醒服务！",
                    "为什么爱情像Wi-Fi？信号强的时候很甜蜜，断线的时候很痛苦！",
                    "什么约会最浪漫？和电脑约会，因为它从不'死机'！",
                    "为什么恋人喜欢看电影？因为可以'剧'情发展！",
                    "为什么铅笔很谦虚？因为它知道会被'削'弱！",
                    "什么书最重？百科全书，因为它有很多'知识'！",
                    "为什么学生喜欢放假？因为可以'释'放压力！",
                    "为什么办公室很冷？因为空调总是在'工作'！",
                    "什么工作最适合懒人？保安，因为可以'站'着赚钱！",
                    "为什么会议很长？因为大家都在'议'论纷纷！",
                    "为什么医院很安静？因为大家都在'休'息！",
                    "什么药最甜？糖果，因为它是'甜'的药！",
                    "为什么医生很健康？因为他们懂得'治'疗自己！",
                    "为什么钱包很瘦？因为钱都去'健身'了！",
                    "什么钱最干净？洗衣费，因为它是'洗'过的钱！",
                    "为什么ATM机很聪明？因为它懂得'取'舍！",
                    "我问朋友：'你怎么总是这么开心？'朋友说：'我把所有烦恼都交给了明天，但明天从来没来找过我！'",
                    "听说把手机放在菠萝旁边充电更快，因为菠萝是天然导体！",
                    "有一天，数学书和语文书吵架了，数学书说：'你凭什么在我上面？'语文书说：'因为我压你一头啊！'",
                    "为什么电脑经常生病？因为它有太多病毒！",
                    "什么光不会亮？时光！",
                    "为什么海水是咸的？因为鱼流的眼泪太多了！"
            };;
            Random rand = new Random();
            String randomJoke =  jokes[rand.nextInt(jokes.length)];
            response = response.replace("{random_joke}", randomJoke);
        }

        return response;
    }

    private List<String> readLines(String resource){
        try(InputStream is = getClass().getResourceAsStream(resource)){
            if(is!=null){ try(BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))){ List<String> lines = new ArrayList<>(); String line; while((line=br.readLine())!=null) lines.add(line); return lines; } }
        } catch(IOException ignored){}
        String fsPath = "src"+resource; fsPath = fsPath.replace('/', File.separatorChar); try { return Files.readAllLines(Paths.get(fsPath)); } catch(IOException e){ }
        String rootPath = resource.startsWith("/") ? resource.substring(1) : resource; rootPath = rootPath.replace('/', File.separatorChar);
        try { return Files.readAllLines(Paths.get(rootPath)); } catch(IOException e){ return Collections.emptyList(); }
    }
}
