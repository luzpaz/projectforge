/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2024 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.web.fibu;

import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.projectforge.business.fibu.EmployeeDO;
import org.projectforge.business.fibu.EmployeeDao;
import org.projectforge.business.fibu.EmployeeSalaryDO;
import org.projectforge.business.user.UserDao;
import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.test.AbstractTestBase;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

public class EmployeeSelectFilterTest extends AbstractTestBase {

  @Autowired
  EmployeeDao employeeDao;

  @Autowired
  UserDao userDao;

  @Override
  protected void beforeAll() {
    logon(AbstractTestBase.TEST_FULL_ACCESS_USER);
  }

  @Test
  public void test()
          throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
    WicketTester wicketTester = new WicketTester();

    EmployeeSalaryDO data = new EmployeeSalaryDO();
    EmployeeSelectPanel selectPanel = new EmployeeSelectPanel("1", new PropertyModel<EmployeeDO>(data,
            "employee"), null, "employee");
    PFUserDO pfUserDO = new PFUserDO();
    pfUserDO.setUsername("EmployeeSelectFilterTestuser1");
    userDao.saveNewTrans(pfUserDO);
    pfUserDO.setFirstname("1");
    pfUserDO.setLastname("user1");
    userDao.updateNewTrans(pfUserDO);
    EmployeeDO employeeDO = new EmployeeDO();
    employeeDO.setUser(pfUserDO);
    this.employeeDao.saveNewTrans(employeeDO);

    PFUserDO pfUserDO1 = new PFUserDO();
    pfUserDO1.setUsername("EmployeeSelectFilterTestuser2");
    userDao.saveNewTrans(pfUserDO1);
    pfUserDO1.setFirstname("2");
    pfUserDO1.setLastname("user2");
    userDao.updateNewTrans(pfUserDO1);
    EmployeeDO employeeDO1 = new EmployeeDO();
    employeeDO1.setUser(pfUserDO1);
    employeeDO1.setAustrittsDatum(LocalDate.now().minusDays(2));
    this.employeeDao.saveNewTrans(employeeDO1);

    PFUserDO pfUserDO2 = new PFUserDO();

    pfUserDO2.setUsername("EmployeeSelectFilterTestuser3");
    pfUserDO2.setFirstname("3");
    pfUserDO2.setLastname("user3");
    userDao.saveNewTrans(pfUserDO2);
    EmployeeDO employeeDO2 = new EmployeeDO();
    employeeDO2.setUser(pfUserDO2);
    employeeDO2.setAustrittsDatum(LocalDate.now().plusDays(2));
    this.employeeDao.saveNewTrans(employeeDO2);

    Method getChoices = EmployeeSelectPanel.class.getDeclaredMethod("getFilteredEmployeeDOs", String.class);

    getChoices.setAccessible(true);
    List<EmployeeDO> employeeDOList = (List<EmployeeDO>) getChoices
            .invoke(selectPanel, "user1");
    Assertions.assertTrue(employeeDOList.get(0).getUser().getUsername().equals(pfUserDO.getUsername()));

    employeeDOList = (List<EmployeeDO>) getChoices.invoke(selectPanel, "user2");
    Assertions.assertTrue(employeeDOList.isEmpty());

    employeeDOList = (List<EmployeeDO>) getChoices.invoke(selectPanel, "user3");
    Assertions.assertTrue(employeeDOList.get(0).getUser().getUsername().equals(pfUserDO2.getUsername()));
  }
}
