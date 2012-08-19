package com.redhat.contentspec.client.validator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.jboss.pressgangccms.contentspec.constants.CSConstants;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;
import com.redhat.contentspec.client.utils.ClientUtilities;

public class OverrideValidator implements IParameterValidator
{
	private static final List<String> validNames = new ArrayList<String>()
	{
		private static final long serialVersionUID = 8972067339176103456L;
		
		{
			add(CSConstants.PUBSNUMBER_OVERRIDE);
			add(CSConstants.AUTHOR_GROUP_OVERRIDE);
			add(CSConstants.REVISION_HISTORY_OVERRIDE);
		}
	};
	
	@Override
	public void validate(final String name, final String value) throws ParameterException
	{
		if (value.matches("^.*=.*$"))
		{
			final String[] vars = value.split("=", 2);
			final String varName = vars[0];
			final String varValue = vars[1];
			
			if (validNames.contains(varName))
			{
				if (varName.equals(CSConstants.AUTHOR_GROUP_OVERRIDE) || varName.equals(CSConstants.REVISION_HISTORY_OVERRIDE))
				{
					final File file = new File(ClientUtilities.validateFilePath(varValue));
					if (!(file.exists() && file.isFile()))
					{
						throw new ParameterException("\"" + varName + "\" override is not a valid file.");
					}
				}
				else if (varName.equals(CSConstants.PUBSNUMBER_OVERRIDE))
				{
					try
					{
						final Integer pubsnumber = Integer.parseInt(varValue);
						if (pubsnumber < 0)
						{
							throw new ParameterException("\"" + varName + "\" override is not a valid number.");
						}
					}
					catch (NumberFormatException e)
					{
						throw new ParameterException("\"" + varName + "\" override is not a valid number.");
					}
				}
			}
			else
			{
				throw new ParameterException("\"" + varName + "\" is an invalid override parameter");
			}
		}
		else
		{
			throw new ParameterException("Invalid override parameter");
		}
	}

}