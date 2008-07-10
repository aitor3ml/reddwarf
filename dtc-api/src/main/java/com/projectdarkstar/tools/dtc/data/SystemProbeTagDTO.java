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

package com.projectdarkstar.tools.dtc.data;

import com.projectdarkstar.tools.dtc.service.DTCInvalidDataException;
import java.util.List;
import java.util.ArrayList;
import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a tag entity used to categorize {@link SystemProbeDTO}
 * objects.
 */
public class SystemProbeTagDTO extends AbstractDTO
{
    private Long id;
    private String tag;
    
    private List<SystemProbeDTO> probes;
    
    public SystemProbeTagDTO(Long id,
                             String tag)
    {
        this.setId(id);
        
        this.setTag(tag);
        
        this.setProbes(new ArrayList<SystemProbeDTO>());
    }
    
    /**
     * Returns the id of the entity in persistent storage
     * 
     * @return id of the entity
     */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTag() { return tag; }
    protected void setTag(String tag) { this.tag = tag; }
    public void updateTag(String tag)
            throws DTCInvalidDataException {
        this.updateAttribute("tag", tag);
    }
    
    /**
     * Returns the list of {@link SystemProbeDTO} objects that are tagged
     * with this tag.
     * 
     * @return probes tagged with this tag.
     */
    public List<SystemProbeDTO> getProbes() { return probes; }
    protected void setProbes(List<SystemProbeDTO> probes) { this.probes = probes; }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException
    {
        this.checkBlank("tag");
        this.checkNull("probes");
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof SystemProbeTagDTO) || o == null) return false;

        SystemProbeTagDTO other = (SystemProbeTagDTO)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getTag(), other.getTag()) &&
                ObjectUtils.equals(this.getProbes(), other.getProbes());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashTag = 31*hash + ObjectUtils.hashCode(this.getTag());
        return hashId + hashTag;
    }
}