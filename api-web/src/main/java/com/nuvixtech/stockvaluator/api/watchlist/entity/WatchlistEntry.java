package com.nuvixtech.stockvaluator.api.watchlist.entity;

import com.nuvixtech.stockvaluator.ingestion.entity.Company;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist")
public class WatchlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @Column(name = "notes")
    private String notes;

    @PrePersist
    void prePersist() {
        if (addedAt == null) addedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
