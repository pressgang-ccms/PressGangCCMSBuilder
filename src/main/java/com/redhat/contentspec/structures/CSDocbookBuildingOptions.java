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
