package com.distributedsearch.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Document model — represents a searchable document stored in the index.
 * Fields must match the Document model in query-router so JSON serialization
 * works seamlessly when the router proxies writes to this node.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Document {

    private int id;
    private String url;
    private String title;
    private String content;

    public Document() {}

    public Document(int id, String url, String title, String content) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.content = content;
    }

    public int getId()              { return id; }
    public void setId(int id)       { this.id = id; }

    public String getUrl()          { return url; }
    public void setUrl(String url)  { this.url = url; }

    public String getTitle()           { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent()             { return content; }
    public void setContent(String content) { this.content = content; }

    @Override
    public String toString() {
        return "Document{id=" + id + ", title='" + title + "', url='" + url + "'}";
    }
}
