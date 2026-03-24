package com.dokocli.core.task;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务实体，持久化为 .tasks/task_{id}.json。
 * 字段与 Python 版保持一致：id, subject, description, status, blockedBy, blocks, owner。
 */
public class Task {

    private int id;
    private String subject;
    private String description;
    private String status;
    private List<Integer> blockedBy;
    private List<Integer> blocks;
    private String owner;

    public Task() {
        this.description = "";
        this.status = "pending";
        this.blockedBy = new ArrayList<>();
        this.blocks = new ArrayList<>();
        this.owner = "";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<Integer> getBlockedBy() { return blockedBy; }
    public void setBlockedBy(List<Integer> blockedBy) { this.blockedBy = blockedBy; }

    public List<Integer> getBlocks() { return blocks; }
    public void setBlocks(List<Integer> blocks) { this.blocks = blocks; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
}
