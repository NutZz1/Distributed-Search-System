package com.distributedsearch.router.model;

/**
 * Simple POJO representing a search result.
 * Contains title and URL of a matched document.
 */
public class SearchResult {
    
    private String title;
    private String url;

    // Default constructor for JSON deserialization
    public SearchResult() {
    }

    public SearchResult(String title, String url) {
        this.title = title;
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "title='" + title + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
