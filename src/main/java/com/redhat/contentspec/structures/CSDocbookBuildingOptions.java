package com.redhat.contentspec.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.pressgang.ccms.docbook.compiling.DocbookBuildingOptions;

public class CSDocbookBuildingOptions extends DocbookBuildingOptions {
    private List<String> injectionTypes = new ArrayList<String>();
    private Boolean injection = true;
    private Map<String, String> overrides = new HashMap<String, String>();
    private Boolean allowEmptySections = false;
    private Boolean showReportPage = false;
    private String locale = null;
    private String commonContentLocale = null;
    private String commonContentDirectory = null;
    private String outputLocale = null;
    private Boolean draft = false;
    private List<String> revisionMessages = null;
    private Boolean useLatestVersions = false;

    public CSDocbookBuildingOptions() {

    }

    public CSDocbookBuildingOptions(final DocbookBuildingOptions docbookBuildingOptions) {
        this.setBuildName(docbookBuildingOptions.getBuildName());
        this.setBuildNarrative(docbookBuildingOptions.getBuildNarrative());
        this.setCvsPkgOption(docbookBuildingOptions.getCvsPkgOption());
        this.setEmailTo(docbookBuildingOptions.getEmailTo());
        this.setEnableDynamicTreeToc(docbookBuildingOptions.getEnableDynamicTreeToc());
        this.setIgnoreMissingCustomInjections(docbookBuildingOptions.getIgnoreMissingCustomInjections());
        this.setIncludeUntranslatedTopics(docbookBuildingOptions.getIncludeUntranslatedTopics());
        this.setInsertBugzillaLinks(docbookBuildingOptions.getInsertBugzillaLinks());
        this.setInsertSurveyLink(docbookBuildingOptions.getInsertSurveyLink());
        this.setProcessRelatedTopics(docbookBuildingOptions.getProcessRelatedTopics());
        this.setPublicanShowRemarks(docbookBuildingOptions.getPublicanShowRemarks());
        this.setSuppressContentSpecPage(docbookBuildingOptions.getSuppressContentSpecPage());
        this.setSuppressErrorsPage(docbookBuildingOptions.getSuppressErrorsPage());
        this.setTaskAndOverviewOnly(docbookBuildingOptions.getTaskAndOverviewOnly());
        this.setInsertEditorLinks(docbookBuildingOptions.getInsertEditorLinks());

        this.setBookEdition(docbookBuildingOptions.getBookEdition());
        this.setBookProduct(docbookBuildingOptions.getBookProduct());
        this.setBookProductVersion(docbookBuildingOptions.getBookProductVersion());
        this.setBookTitle(docbookBuildingOptions.getBookTitle());
        this.setBookPubsnumber(docbookBuildingOptions.getBookPubsnumber());
        this.setBookSubtitle(docbookBuildingOptions.getBookSubtitle());
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

    public String getCommonContentLocale() {
        return commonContentLocale;
    }

    public void setCommonContentLocale(final String commonContentLocale) {
        this.commonContentLocale = commonContentLocale;
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
}
