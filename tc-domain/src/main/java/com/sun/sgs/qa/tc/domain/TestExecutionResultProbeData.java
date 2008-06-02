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

package com.sun.sgs.qa.tc.domain;

import java.sql.Date;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "TestExecutionResultProbeData")
public class TestExecutionResultProbeData implements Serializable
{
    private Long id;
    private Date timestamp;
    private Long value;
    
    private TestExecutionResultProbeLog parentProbe;
    
    public TestExecutionResultProbeData(Date timestamp,
                                        Long value)
    {
        this.setTimestamp(timestamp);
        this.setValue(value);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "timestamp", nullable = false)
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    
    @Column(name = "value", nullable = false)
    public Long getValue() { return value; }
    public void setValue(Long value) { this.value = value; }
    
    @ManyToOne
    @JoinColumn(name = "parentProbe", nullable = false)
    public TestExecutionResultProbeLog getParentProbe() { return parentProbe; }
    public void setParentProbe(TestExecutionResultProbeLog parentProbe) { this.parentProbe = parentProbe; }
}
