package com.distributedsearch.router.model;

/**
 * Document model representing search results.
 * Matches the Document structure returned by search nodes.
 */
public class Document {
    private int id;
    private String url;
    private String title;
    private String content;

    public Document() {
    }

    public Document(int id, String url, String title, String content) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.content = content;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "Document{" +
                "id=" + id +
                ", url='" + url + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}
