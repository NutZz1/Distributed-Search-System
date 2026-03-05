package com.distributedsearch.node;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
public class SearchController {

    private SearchService service = new SearchService();

    @PostMapping("/index")
    public String indexDocument(@RequestBody Document doc) {

        service.addDocument(doc);
        return "Document indexed";
    }

    @GetMapping("/search")
    public List<Document> search(@RequestParam String q) {

        return service.search(q);
    }
}