package com.casemap.hierarchy.asset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CaseAssetFile {

    private String source;
    private List<CaseAsset> items = new ArrayList<>();

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<CaseAsset> getItems() {
        return items;
    }

    public void setItems(List<CaseAsset> items) {
        this.items = items;
    }
}
