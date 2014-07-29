/*
  Copyright 2011-2014 Red Hat

  This file is part of PressGang CCMS.

  PressGang CCMS is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  PressGang CCMS is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with PressGang CCMS.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.jboss.pressgang.ccms.contentspec.builder.structures;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocBookBuildingOptions implements Serializable {
    private static final long serialVersionUID = -3481034970109486054L;
    private Boolean suppressContentSpecPage = false;
    private Boolean insertBugLinks = true;
    private Boolean useOldBugLinks = false;
    private Boolean publicanShowRemarks = false;
    private Boolean ignoreMissingCustomInjections = true;
    private Boolean suppressErrorsPage = false;
    private Boolean insertEditorLinks = false;
    private String buildName = null;
    private List<String> injectionTypes = new ArrayList<String>();
    private Boolean injection = true;
    private Map<String, String> overrides = new HashMap<String, String>();
    private Map<String, String> publicanCfgOverrides = new HashMap<String, String>();
    private Boolean allowEmptySections = false;
    private Boolean showReportPage = false;
    private String locale = null;
    private String commonContentDirectory = null;
    private String outputLocale = null;
    private Boolean draft = false;
    private List<String> revisionMessages = null;
    private Boolean useLatestVersions = false;
    private Boolean flattenTopics = false;
    private Boolean flatten = false;
    private Boolean forceInjectBugLinks = false;
    private Boolean serverBuild = false;
    private Integer maxRevision = null;
    private Boolean calculateChunkDepth = false;
    private Boolean resolveEntities = false;
    private Boolean skipNestedSectionValidation = false;

    public DocBookBuildingOptions() {

    }

    public String getBuildName() {
        return buildName;
    }

    public void setBuildName(final String buildName) {
        this.buildName = buildName;
    }

    public Boolean getInsertBugLinks() {
        return insertBugLinks;
    }

    public void setInsertBugLinks(Boolean insertBugLinks) {
        this.insertBugLinks = insertBugLinks;
    }

    public Boolean getSuppressContentSpecPage() {
        return suppressContentSpecPage;
    }

    public void setSuppressContentSpecPage(Boolean suppressContentSpecPage) {
        this.suppressContentSpecPage = suppressContentSpecPage;
    }

    public Boolean getInsertEditorLinks() {
        return insertEditorLinks;
    }

    public void setInsertEditorLinks(final Boolean insertEditorLinks) {
        this.insertEditorLinks = insertEditorLinks;
    }

    public void setPublicanShowRemarks(final Boolean publicanShowRemarks) {
        this.publicanShowRemarks = publicanShowRemarks;
    }

    public Boolean getPublicanShowRemarks() {
        return publicanShowRemarks;
    }

    public Boolean getIgnoreMissingCustomInjections() {
        return ignoreMissingCustomInjections;
    }

    public void setIgnoreMissingCustomInjections(final Boolean ignoreMissingCustomInjections) {
        this.ignoreMissingCustomInjections = ignoreMissingCustomInjections;
    }

    public Boolean getSuppressErrorsPage() {
        return suppressErrorsPage;
    }

    public void setSuppressErrorsPage(final Boolean suppressErrorsPage) {
        this.suppressErrorsPage = suppressErrorsPage;
    }

    public List<String> getInjectionTypes() {
        return injectionTypes;
    }

    public void setInjectionTypes(final List<String> injectionTypes) {
        this.injectionTypes = injectionTypes;
    }

    public boolean getInjection() {
        return injection;
    }

    public void setInjection(final Boolean injection) {
        this.injection = injection;
    }

    public Map<String, String> getOverrides() {
        return overrides;
    }

    public void setOverrides(final Map<String, String> overrides) {
        this.overrides = overrides;
    }

    public Map<String, String> getPublicanCfgOverrides() {
        return publicanCfgOverrides;
    }

    public void setPublicanCfgOverrides(final Map<String, String> publicanCfgOverrides) {
        this.publicanCfgOverrides = publicanCfgOverrides;
    }

    public boolean isAllowEmptySections() {
        return allowEmptySections;
    }

    public void setAllowEmptySections(final Boolean allowEmptySections) {
        this.allowEmptySections = allowEmptySections;
    }

    public Boolean getShowReportPage() {
        return showReportPage;
    }

    public void setShowReportPage(final Boolean showReportPage) {
        this.showReportPage = showReportPage;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getCommonContentDirectory() {
        return commonContentDirectory;
    }

    public void setCommonContentDirectory(final String commonContentDirectory) {
        this.commonContentDirectory = commonContentDirectory;
    }

    public String getOutputLocale() {
        return outputLocale;
    }

    public void setOutputLocale(String outputLocale) {
        this.outputLocale = outputLocale;
    }

    public Boolean getDraft() {
        return draft;
    }

    public void setDraft(final Boolean draft) {
        this.draft = draft;
    }

    public List<String> getRevisionMessages() {
        return revisionMessages;
    }

    public void setRevisionMessages(final List<String> revisionMessage) {
        this.revisionMessages = revisionMessage;
    }

    public Boolean getUseLatestVersions() {
        return useLatestVersions;
    }

    public void setUseLatestVersions(Boolean useLatestVersions) {
        this.useLatestVersions = useLatestVersions;
    }

    public Boolean getFlattenTopics() {
        return flattenTopics;
    }

    public void setFlattenTopics(Boolean flattenTopics) {
        this.flattenTopics = flattenTopics;
    }

    public Boolean getForceInjectBugLinks() {
        return forceInjectBugLinks;
    }

    public void setForceInjectBugLinks(Boolean forceInjectBugLinks) {
        this.forceInjectBugLinks = forceInjectBugLinks;
    }

    public Boolean isServerBuild() {
        return serverBuild;
    }

    public void setServerBuild(Boolean serverBuild) {
        this.serverBuild = serverBuild;
    }

    public Boolean getFlatten() {
        return flatten;
    }

    public void setFlatten(Boolean flatten) {
        this.flatten = flatten;
    }

    public Integer getMaxRevision() {
        return maxRevision;
    }

    public void setMaxRevision(Integer maxRevision) {
        this.maxRevision = maxRevision;
    }

    public Boolean getCalculateChunkDepth() {
        return calculateChunkDepth;
    }

    public void setCalculateChunkDepth(Boolean calculateChunkDepth) {
        this.calculateChunkDepth = calculateChunkDepth;
    }

    public Boolean getUseOldBugLinks() {
        return useOldBugLinks;
    }

    public void setUseOldBugLinks(Boolean useOldBugLinks) {
        this.useOldBugLinks = useOldBugLinks;
    }

    public Boolean isResolveEntities() {
        return resolveEntities;
    }

    public void setResolveEntities(Boolean resolveEntities) {
        this.resolveEntities = resolveEntities;
    }

    public Boolean isSkipNestedSectionValidation() {
        return skipNestedSectionValidation;
    }

    public void setSkipNestedSectionValidation(Boolean skipNestedSectionValidation) {
        this.skipNestedSectionValidation = skipNestedSectionValidation;
    }
}
