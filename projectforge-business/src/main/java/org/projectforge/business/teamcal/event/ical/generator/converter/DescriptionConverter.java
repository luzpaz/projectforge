package org.projectforge.business.teamcal.event.ical.generator.converter;

import org.apache.commons.lang.StringUtils;
import org.projectforge.business.teamcal.event.model.TeamEventDO;

import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.property.Description;

public class DescriptionConverter extends PropertyConverter
{
  @Override
  public Property convert(final TeamEventDO event)
  {
    if (StringUtils.isNotBlank(event.getNote())) {
      return new Description(event.getNote());
    }

    return null;
  }
}
