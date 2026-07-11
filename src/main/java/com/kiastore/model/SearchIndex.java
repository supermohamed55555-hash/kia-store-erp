package com.kiastore.model;

/**
 * Represents keywords indexed for smart fuzzy parts searching.
 */
public class SearchIndex {

    private int id;
    private int partId;
    private String keyword;
    private int weight = 1;

    public SearchIndex() {}

    public SearchIndex(int partId, String keyword, int weight) {
        this.partId = partId;
        this.keyword = keyword;
        this.weight = weight;
    }

    public int getId() { return id; }
    public int getPartId() { return partId; }
    public String getKeyword() { return keyword; }
    public int getWeight() { return weight; }

    public void setId(int id) { this.id = id; }
    public void setPartId(int partId) { this.partId = partId; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public void setWeight(int weight) { this.weight = weight; }
}
