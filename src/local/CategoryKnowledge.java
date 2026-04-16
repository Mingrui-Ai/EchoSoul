package local;

import java.io.PrintWriter;
import java.util.*;

class CategoryKnowledge {
    private String name;
    private Map<String, List<String>> responses = new HashMap<>();
    private Random random;

    public CategoryKnowledge(String name) {
        this.name = name;
        this.responses = new HashMap<>();
        this.random = new Random();
    }

    //添加回答在新建的哈希表中，用于初始化
    public void addResponse(String keyword, String response) {
        responses.computeIfAbsent(keyword.toLowerCase(), k -> new ArrayList<>()).add(response);
    }


    /*
    在当前分类内搜索与用户输入匹配的关键词，为每个匹配的关键词随机返回一个回答，并计算匹配度得分。
    * */
    public List<ResponseResult> search(String input) {
        // 找到最佳匹配的关键词
        String bestKeyword = null;
        int bestScore = 0;

        for (String keyword : responses.keySet()) {
            int score = calculateMatchScore(input, keyword);
            if (score > 0 && score > bestScore) {
                bestScore = score;
                bestKeyword = keyword;
            }
        }

        // 如果没有匹配的关键词，返回空列表
        if (bestKeyword == null) {
            return new ArrayList<>();
        }

        // 获取该关键词对应的回答列表
        List<String> responseList = responses.get(bestKeyword);
        if (responseList == null || responseList.isEmpty()) {
            return new ArrayList<>();
        }

        // 随机选择一个回答
        String selectedResponse = getRandomResponse(responseList);

        // 返回单个结果
        return Arrays.asList(new ResponseResult(selectedResponse, name, bestScore));
    }


    // 从同个关键词下的回答集随机抽取一条回答
    private String getRandomResponse(List<String> responseList) {
        if (responseList.size() == 1) {
            return responseList.get(0);
        }
        int randomIndex = random.nextInt(responseList.size());
        return responseList.get(randomIndex);
    }

    // 计算匹配度得分
    private int calculateMatchScore(String input, String keyword) {
        // 初始匹配度得分为零
        int score = 0;

        if (input.equals(keyword)) score += 10;//完全匹配
        if (input.contains(keyword)) score += 5;//包含匹配
        //分词匹配——将一个句子拆分成多个关键崔
        String[] inputWords = input.split("\\s+");
        String[] keywordWords = keyword.split("\\s+");

        for (String kw : keywordWords)
            for (String iw : inputWords) {
                if (iw.equals(kw)) score += 3;
                else if (iw.contains(kw)) score += 1;
            }

        return score;
    }

    String getName() {
        return name;
    }

    // Export all keyword-response pairs for this category
    void exportToFile(PrintWriter writer) {
        for (Map.Entry<String, List<String>> e : responses.entrySet()) {
            String keyword = e.getKey();
            for (String response : e.getValue()) {
                writer.println(keyword + "=" + response);
            }
        }
    }
}
