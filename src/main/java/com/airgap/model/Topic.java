package com.airgap.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "topics")
public class Topic implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        CAPTURED,
        WAITING_FOR_NETWORK,
        GENERATING,
        READY_OFFLINE,
        FAILED,
        AI_UNAVAILABLE
    }

    public enum CaptureSource {
        MANUAL,
        RELATED_CONCEPT,
        SEARCH,
        IMPORT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = true)
    private String direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.CAPTURED;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_source", nullable = false, length = 30)
    private CaptureSource captureSource = CaptureSource.MANUAL;

    // Fully Nullable Content Columns (Guarantees non-null initialization for legacy MySQL constraints)
    @Column(name = "summary_content", columnDefinition = "LONGTEXT", nullable = true)
    private String summaryContent = "";

    @Column(name = "knowledge_pack_json", columnDefinition = "LONGTEXT", nullable = true)
    private String knowledgePackJson = "{}";

    @Column(name = "teaching_plan_json", columnDefinition = "LONGTEXT", nullable = true)
    private String teachingPlanJson = "{}";

    @Column(name = "curiosity_paths_json", columnDefinition = "LONGTEXT", nullable = true)
    private String curiosityPathsJson = "[]";

    @Column(name = "related_concepts_json", columnDefinition = "LONGTEXT", nullable = true)
    private String relatedConceptsJson = "[]";

    @Column(name = "estimated_reading_time", nullable = false, columnDefinition = "INT DEFAULT 1")
    private int estimatedReadingTime = 1;

    @Column(name = "summary_version", nullable = false, columnDefinition = "INT DEFAULT 1")
    private int summaryVersion = 1;

    @Column(name = "knowledge_version", nullable = false, columnDefinition = "INT DEFAULT 1")
    private int knowledgeVersion = 1;

    @Column(name = "times_read", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int timesRead = 0;

    @Column(name = "questions_asked", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int questionsAsked = 0;

    @Column(columnDefinition = "LONGTEXT", nullable = true)
    private String content = ""; // Backward compatibility non-null fallback

    @Column(name = "is_pinned", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isPinned = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_opened_at")
    private Date lastOpenedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Date createdAt;

    public Topic() {
        this.summaryContent = "";
        this.knowledgePackJson = "{}";
        this.teachingPlanJson = "{}";
        this.curiosityPathsJson = "[]";
        this.relatedConceptsJson = "[]";
        this.content = "";
    }

    public Topic(User user, String title, String direction, CaptureSource captureSource) {
        this();
        this.user = user;
        this.title = title;
        this.direction = direction;
        this.captureSource = captureSource != null ? captureSource : CaptureSource.MANUAL;
        this.status = Status.CAPTURED;
        this.createdAt = new Date();
        this.lastOpenedAt = new Date();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Status getStatus() {
        return status != null ? status : Status.READY_OFFLINE;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public CaptureSource getCaptureSource() {
        return captureSource != null ? captureSource : CaptureSource.MANUAL;
    }

    public void setCaptureSource(CaptureSource captureSource) {
        this.captureSource = captureSource;
    }

    public String getSummaryContent() {
        return summaryContent != null ? summaryContent : "";
    }

    public void setSummaryContent(String summaryContent) {
        this.summaryContent = summaryContent != null ? summaryContent : "";
        this.content = this.summaryContent;
    }

    public String getKnowledgePackJson() {
        return knowledgePackJson != null ? knowledgePackJson : "{}";
    }

    public void setKnowledgePackJson(String knowledgePackJson) {
        this.knowledgePackJson = knowledgePackJson != null ? knowledgePackJson : "{}";
    }

    public String getTeachingPlanJson() {
        return teachingPlanJson != null ? teachingPlanJson : "{}";
    }

    public void setTeachingPlanJson(String teachingPlanJson) {
        this.teachingPlanJson = teachingPlanJson != null ? teachingPlanJson : "{}";
    }

    public String getCuriosityPathsJson() {
        return curiosityPathsJson != null ? curiosityPathsJson : "[]";
    }

    public void setCuriosityPathsJson(String curiosityPathsJson) {
        this.curiosityPathsJson = curiosityPathsJson != null ? curiosityPathsJson : "[]";
    }

    public String getRelatedConceptsJson() {
        return relatedConceptsJson != null ? relatedConceptsJson : "[]";
    }

    public void setRelatedConceptsJson(String relatedConceptsJson) {
        this.relatedConceptsJson = relatedConceptsJson != null ? relatedConceptsJson : "[]";
    }

    public int getEstimatedReadingTime() {
        return estimatedReadingTime > 0 ? estimatedReadingTime : 1;
    }

    public void setEstimatedReadingTime(int estimatedReadingTime) {
        this.estimatedReadingTime = estimatedReadingTime;
    }

    public int getSummaryVersion() {
        return summaryVersion;
    }

    public void setSummaryVersion(int summaryVersion) {
        this.summaryVersion = summaryVersion;
    }

    public int getKnowledgeVersion() {
        return knowledgeVersion;
    }

    public void setKnowledgeVersion(int knowledgeVersion) {
        this.knowledgeVersion = knowledgeVersion;
    }

    public int getTimesRead() {
        return timesRead;
    }

    public void setTimesRead(int timesRead) {
        this.timesRead = timesRead;
    }

    public int getQuestionsAsked() {
        return questionsAsked;
    }

    public void setQuestionsAsked(int questionsAsked) {
        this.questionsAsked = questionsAsked;
    }

    public String getContent() {
        return content != null ? content : "";
    }

    public void setContent(String content) {
        this.content = content != null ? content : "";
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public Date getLastOpenedAt() {
        return lastOpenedAt;
    }

    public void setLastOpenedAt(Date lastOpenedAt) {
        this.lastOpenedAt = lastOpenedAt;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
