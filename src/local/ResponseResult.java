package local;

import java.util.Objects;

public class ResponseResult {
    private final String response;
    private final String category;
    private final int score;

    public ResponseResult(String response, String category, int score) {
        this.response = response;
        this.category = category;
        this.score = score;
    }
    public String getResponse() { return response; }
    public String getCategory() { return category; }
    public int getScore() { return score; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            ResponseResult that = (ResponseResult)obj;
            // 只比较 response 和 category，不比较 score
            return Objects.equals(this.response, that.response) &&
                    Objects.equals(this.category, that.category);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(response, category); // 只基于 response 和 category
    }

    @Override
    public String toString() {
        return String.format("ResponseResult{response='%s', category='%s', score=%d}",
                response, category, score);
    }
}
