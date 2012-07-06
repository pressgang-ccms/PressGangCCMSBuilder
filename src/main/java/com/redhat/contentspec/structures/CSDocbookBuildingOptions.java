package com.redhat.contentspec.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.redhat.ecs.services.docbookcompiling.DocbookBuildingOptions;

public class CSDocbookBuildingOptions extends DocbookBuildingOptions {

	private List<String> injectionTypes = new ArrayList<String>();
	private Boolean injection = true;
	private Boolean permissive = false;
	private Map<String, String> overrides = new HashMap<String, String>();
	private Boolean allowEmptySections = false;
	
	public CSDocbookBuildingOptions()
	{
		
	}
	
	public CSDocbookBuildingOptions(final DocbookBuildingOptions docbookBuildingOptions)
	{
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
	
	public void setInjectionTypes(List<String> injectionTypes) {
		this.injectionTypes = injectionTypes;
	}
	
	public boolean getInjection() {
		return injection;
	}
	
	public void setInjection(Boolean injection) {
		this.injection = injection;
	}
	
	public boolean getPermissive() {
		return permissive;
	}
	
	public void setPermissive(Boolean permissive) {
		this.permissive = permissive;
	}

	public Map<String, String> getOverrides() {
		return overrides;
	}

	public void setOverrides(Map<String, String> overrides) {
		this.overrides = overrides;
	}

	public boolean isAllowEmptySections() {
		return allowEmptySections;
	}

	public void setAllowEmptySections(boolean allowEmptySections) {
		this.allowEmptySections = allowEmptySections;
	}
}
