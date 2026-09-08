package com.casemap.hierarchy.produce;

import java.util.ArrayList;
import java.util.List;

public class BatchReviewRequest {

    private List<String> reviewIds = new ArrayList<>();
    private String operator;

    public List<String> getReviewIds() {
        return reviewIds;
    }

    public void setReviewIds(List<String> reviewIds) {
        this.reviewIds = reviewIds == null ? new ArrayList<>() : new ArrayList<>(reviewIds);
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
