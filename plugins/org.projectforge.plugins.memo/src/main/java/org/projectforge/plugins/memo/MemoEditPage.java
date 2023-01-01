/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2023 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.plugins.memo;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.projectforge.web.wicket.AbstractEditPage;
import org.projectforge.web.wicket.EditPage;
import org.slf4j.Logger;

/**
 * The controller of the edit formular page. Most functionality such as insert, update, delete etc. is done by the super
 * class.
 * 
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
@EditPage(defaultReturnPage = MemoListPage.class)
public class MemoEditPage extends AbstractEditPage<MemoDO, MemoEditForm, MemoDao>
{
  private static final long serialVersionUID = -5058143025817192156L;

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MemoEditPage.class);

  @SpringBean
  private MemoDao memoDao;

  public MemoEditPage(final PageParameters parameters)
  {
    super(parameters, "plugins.memo");
    init();
  }

  @Override
  protected MemoDao getBaseDao()
  {
    return memoDao;
  }

  @Override
  protected MemoEditForm newEditForm(final AbstractEditPage<?, ?, ?> parentPage, final MemoDO data)
  {
    return new MemoEditForm(this, data);
  }

  @Override
  protected Logger getLogger()
  {
    return log;
  }
}
