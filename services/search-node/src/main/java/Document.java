public class Document {

    private int id;
    private String url;
    private String title;
    private String content;

    public Document(int id, String url, String title, String content) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.content = content;
    }

    public int getId() { return id; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
}
