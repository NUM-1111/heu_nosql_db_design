package com.university.shipmanager.entity.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("ping_doc")
public class PingDoc {
    @Id
    private String id;
    private String msg;

    public PingDoc() {}
    public PingDoc(String msg) { this.msg = msg; }

    public String getId() { return id; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
}
