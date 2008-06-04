/*
 * Copyright 2008 Sun Microsystems, Inc.
 *
 * This file is part of the Darkstar Test Cluster
 *
 * Darkstar Test Cluster is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Darkstar Test Cluster is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.projectdarkstar.tools.dtc.domain;

import java.util.List;
import java.io.Serializable;
import java.sql.Date;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.CascadeType;
import javax.persistence.OrderBy;
import javax.persistence.Version;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "TestExecution")
public class TestExecution implements Serializable
{
    private Long id;
    private Long versionNumber;
    private String name;
    private Date dateStarted;
    private Date dateFinished;
    
    private List<TestExecutionTag> tags;
    private List<TestExecutionResult> results;
    
    private String originalTestSuiteName;
    private String originalTestSuiteDescription;
    private TestSuite originalTestSuite;

    
    public TestExecution(String name,
                         Date dateStarted,
                         TestSuite originalTestSuite)
    {
        this.setName(name);
        this.setDateStarted(dateStarted);
        
        this.setOriginalTestSuiteName(originalTestSuite.getName());
        this.setOriginalTestSuiteDescription(originalTestSuite.getDescription());
        this.setOriginalTestSuite(originalTestSuite);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Version
    @Column(name = "versionNumber")
    public Long getVersionNumber() { return versionNumber; }
    protected void setVersionNumber(Long versionNumber) { this.versionNumber = versionNumber; }
    
    @Column(name = "name", nullable = false)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateStarted", nullable = false)
    public Date getDateStarted() { return dateStarted; }
    public void setDateStarted(Date dateStarted) { this.dateStarted = dateStarted; }
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateFinished", nullable = true)
    public Date getDateFinished() { return dateFinished; } 
    public void setDateFinished(Date dateFinished) { this.dateFinished = dateFinished; }
    
    @ManyToMany
    @OrderBy("tag")
    @JoinTable(name = "testExecutionTags",
               joinColumns = @JoinColumn(name = "testExecutionId"),
               inverseJoinColumns = @JoinColumn(name = "testExecutionTagId"))
    public List<TestExecutionTag> getTags() { return tags; }
    public void setTags(List<TestExecutionTag> tags) { this.tags = tags; }
    
    @OneToMany(mappedBy = "parentExecution", cascade=CascadeType.REMOVE)
    public List<TestExecutionResult> getResults() { return results; }
    public void setResults(List<TestExecutionResult> results) { this.results = results; }
    
    
    @Column(name = "originalTestSuiteName", nullable = false)
    public String getOriginalTestSuiteName() { return originalTestSuiteName; }
    private void setOriginalTestSuiteName(String originalTestSuiteName) { this.originalTestSuiteName = originalTestSuiteName; }
    
    @Column(name = "originalTestSuiteDescription", nullable = false)
    public String getOriginalTestSuiteDescription() { return originalTestSuiteDescription; }
    private void setOriginalTestSuiteDescription(String originalTestSuiteDescription) { this.originalTestSuiteDescription = originalTestSuiteDescription; }
    
    @ManyToOne
    @JoinColumn(name = "originalTestSuite", nullable = false)
    public TestSuite getOriginalTestSuite() { return originalTestSuite; }
    public void setOriginalTestSuite(TestSuite originalTestSuite) { this.originalTestSuite = originalTestSuite; }
    
}