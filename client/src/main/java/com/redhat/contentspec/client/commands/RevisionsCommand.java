package com.redhat.contentspec.client.commands;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.redhat.contentspec.client.config.ClientConfiguration;
import com.redhat.contentspec.client.config.ContentSpecConfiguration;
import com.redhat.contentspec.client.constants.Constants;
import com.redhat.contentspec.client.entities.Revision;
import com.redhat.contentspec.client.entities.RevisionList;
import com.redhat.contentspec.rest.RESTManager;
import com.redhat.contentspec.rest.RESTReader;
import com.redhat.contentspec.utils.logging.ErrorLoggerManager;
import com.redhat.ecs.commonutils.CollectionUtilities;
import com.redhat.topicindex.rest.entities.interfaces.RESTUserV1;

@Parameters(commandDescription = "Get a list of revisions for a specified ID")
public class RevisionsCommand extends BaseCommandImpl {

	@Parameter(metaVar = "[ID]")
	private List<Integer> ids = new ArrayList<Integer>();
	
	@Parameter(names = {Constants.CONTENT_SPEC_LONG_PARAM, Constants.CONTENT_SPEC_SHORT_PARAM})
	private Boolean contentSpec = false;
	
	@Parameter(names = {Constants.TOPIC_LONG_PARAM, Constants.TOPIC_SHORT_PARAM})
	private Boolean topic = false;
	
	public RevisionsCommand(final JCommander parser, final ContentSpecConfiguration cspConfig, final ClientConfiguration clientConfig) {
		super(parser, cspConfig, clientConfig);
	}

	public Boolean isUseContentSpec() {
		return contentSpec;
	}

	public void setUseContentSpec(Boolean useContentSpec) {
		this.contentSpec = useContentSpec;
	}

	public List<Integer> getIds() {
		return ids;
	}

	public void setIds(List<Integer> ids) {
		this.ids = ids;
	}

	public Boolean isUseTopic() {
		return topic;
	}

	public void setUseTopic(Boolean useTopic) {
		this.topic = useTopic;
	}

	@Override
	public void printError(final String errorMsg, final boolean displayHelp) {
		printError(errorMsg, displayHelp, Constants.REVISIONS_COMMAND_NAME);
	}

	@Override
	public void printHelp() {
		printHelp(Constants.REVISIONS_COMMAND_NAME);
	}
	
	@Override
	public RESTUserV1 authenticate(final RESTReader reader) {
		return null;
	}
	
	public boolean isValid() {		
		if (contentSpec && topic) return false;
		
		return true;
	}

	@Override
	public void process(final RESTManager restManager, final ErrorLoggerManager elm, final RESTUserV1 user)
	{
		// If there are no ids then use the csprocessor.cfg file
		if (loadFromCSProcessorCfg()) {
			setIds(CollectionUtilities.toArrayList(cspConfig.getContentSpecId()));
		}
		
		// Check that the command is valid
		if (!isValid()) {
			printError(Constants.INVALID_ARG_MSG, true);
			shutdown(Constants.EXIT_ARGUMENT_ERROR);
		}
		
		// Check that we only have one id
		if (ids.size() == 0) {
			printError(Constants.ERROR_NO_ID_MSG, false);
			shutdown(Constants.EXIT_ARGUMENT_ERROR);
		} else if (ids.size() > 1) {
			printError(Constants.ERROR_MULTIPLE_ID_MSG, false);
			shutdown(Constants.EXIT_ARGUMENT_ERROR);
		}
		
		// Good point to check for a shutdown
		if (isAppShuttingDown()) {
			shutdown.set(true);
			return;
		}
		
		// Get the list of revisions
		List<Object[]> revisions = null;
		if (topic) {
			revisions = restManager.getReader().getTopicRevisionsById(ids.get(0));
		} else {
			revisions = restManager.getReader().getContentSpecRevisionsById(ids.get(0));
		}
		
		// Good point to check for a shutdown
		if (isAppShuttingDown()) {
			shutdown.set(true);
			return;
		}
		
		// Create the revision list
		RevisionList list = new RevisionList(ids.get(0), topic ? "Topic" : "Content Specification");
		for (Object[] o: revisions) {
			Number rev = (Number)o[0];
			Date revDate = (Date)o[1];
			list.addRevision(new Revision((Integer) rev, revDate));
		}
		
		// Display the list
		JCommander.getConsole().println(list.toString());
	}

	@Override
	public boolean loadFromCSProcessorCfg() {
		return ids.size() == 0 && cspConfig != null && cspConfig.getContentSpecId() != null;
	}
}
