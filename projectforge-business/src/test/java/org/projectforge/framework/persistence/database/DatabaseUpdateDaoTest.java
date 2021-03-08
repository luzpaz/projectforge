/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2021 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.framework.persistence.database;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.projectforge.business.address.AddressDO;
import org.projectforge.common.DatabaseDialect;
import org.projectforge.continuousdb.DatabaseSupport;
import org.projectforge.continuousdb.Table;
import org.projectforge.continuousdb.TableAttribute;
import org.projectforge.continuousdb.TableAttributeType;
import org.projectforge.framework.persistence.api.HibernateUtils;
import org.projectforge.test.AbstractTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseUpdateDaoTest extends AbstractTestBase
{
  @Autowired
  private DatabaseService databaseService;

  @Autowired
  private DataSource dataSource;

  @Test
  public void createUniqueConstraintName()
  {
    final DatabaseService databaseService = new DatabaseService();
    final String[] existingConstraintNames = { "t_mytable_uq_tenant_i1", "t_mytable_uq_tenant_i2",
        "t_mytable_uq_username",
        "t_my_very_long__uq_tenant_i1", "t_my_very_long__uq_tenant_i2", "t_my_very_long__uq_username" };
    assertEquals("t_mytable_uq_tenant_i3",
        databaseService.createUniqueConstraintName("t_MYTABLE", new String[] { "Tenant_id", "username" },
            existingConstraintNames));
    assertEquals("t_mytable_uq_name1",
        databaseService.createUniqueConstraintName("t_mytable", new String[] { "name", "username" },
            existingConstraintNames));
    assertEquals("t_my_very_long__uq_tenant_i3",
        databaseService.createUniqueConstraintName("t_my_very_LONG_table", new String[] { "tenant_id", "username" },
            existingConstraintNames));
    assertEquals("t_my_very_long__uq_name1",
        databaseService.createUniqueConstraintName("t_my_very_long_table", new String[] { "Name", "username" },
            existingConstraintNames));

    final String[] paranoia = new String[1000];
    for (int i = 0; i < 1000; i++) {
      paranoia[i] = "t_mytable_uq_tenant_i" + i;
    }
    try {
      databaseService.createUniqueConstraintName("t_mytable", new String[] { "tenant_id", "username" }, paranoia);
      Assertions.fail("UnsupportedOperation excepted!");
    } catch (final UnsupportedOperationException ex) {
      // Expected.
    }
  }

  @Test
  public void createTableScript()
  {
    final Table table = new Table("t_test") //
        .addAttribute(new TableAttribute("pk", TableAttributeType.INT).setPrimaryKey(true).setGenerated(true))//
        .addAttribute(new TableAttribute("counter", TableAttributeType.INT)) //
        .addAttribute(new TableAttribute("money", TableAttributeType.DECIMAL, 8, 2).setNullable(false)) //
        .addAttribute(new TableAttribute("address_fk", TableAttributeType.INT).setForeignTable(AddressDO.class));
    HibernateUtils.setDialect(DatabaseDialect.HSQL);
    StringBuffer buf = new StringBuffer();
    DatabaseService databaseService = new DatabaseService();
    databaseService.buildCreateTableStatement(buf, table);
    assertEquals("CREATE TABLE t_test (\n" //
        + "  pk INT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) NOT NULL PRIMARY KEY,\n" //
        + "  counter INT,\n" //
        + "  money DECIMAL(8, 2) NOT NULL,\n" //
        + "  address_fk INT,\n" //
        + "  FOREIGN KEY (address_fk) REFERENCES T_ADDRESS(pk)\n" //
        + ");\n", buf.toString());
    HibernateUtils.setDialect(DatabaseDialect.PostgreSQL);
    buf = new StringBuffer();
    databaseService = new DatabaseService();
    databaseService.buildCreateTableStatement(buf, table);
    assertEquals("CREATE TABLE t_test (\n" //
        + "  pk INT4,\n" //
        + "  counter INT4,\n" //
        + "  money DECIMAL(8, 2) NOT NULL,\n" //
        + "  address_fk INT4,\n" //
        + "  PRIMARY KEY (pk),\n" //
        + "  FOREIGN KEY (address_fk) REFERENCES T_ADDRESS(pk)\n" //
        + ");\n", buf.toString());
  }

  @Test
  public void createAndDropTable()
  {
    logon(AbstractTestBase.ADMIN);
    final Table table = new Table("t_test") //
        .addAttribute(new TableAttribute("name", TableAttributeType.VARCHAR, 5).setPrimaryKey(true))//
        .addAttribute(new TableAttribute("counter", TableAttributeType.INT)) //
        .addAttribute(new TableAttribute("money", TableAttributeType.DECIMAL, 8, 2).setNullable(false)) //
        .addAttribute(new TableAttribute("address_fk", TableAttributeType.INT).setForeignTable("t_address")
            .setForeignAttribute("pk"));
    final StringBuffer buf = new StringBuffer();
    databaseService.buildCreateTableStatement(buf, table);
    assertEquals("CREATE TABLE t_test (\n" //
        + "  name VARCHAR(5),\n" //
        + "  counter INT,\n" //
        + "  money DECIMAL(8, 2) NOT NULL,\n" //
        + "  address_fk INT,\n" //
        + "  PRIMARY KEY (name),\n" //
        + "  FOREIGN KEY (address_fk) REFERENCES t_address(pk)\n" //
        + ");\n", buf.toString());
    assertTrue(databaseService.createTable(table));
    assertTrue(databaseService.doesTableExist("t_test"));
    assertTrue(databaseService.dropTable("t_test"));
    assertTrue(databaseService.dropTable("t_test"));
    assertTrue(databaseService.createTable(table));
    final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("INSERT INTO t_test (name, counter, money) VALUES('test', 5, 5.12);");
    assertFalse( databaseService.dropTable("t_test"),"Data base is not empty!");
    jdbc.execute("DELETE FROM t_test;");
  }

  @Test
  public void buildAddUniqueConstraint()
  {
    final StringBuffer buf = new StringBuffer();
    databaseService.buildAddUniqueConstraintStatement(buf, "t_user_right", "t_user_right_user_fk_key", "user_fk",
        "right_id");
    assertEquals("ALTER TABLE t_user_right ADD CONSTRAINT t_user_right_user_fk_key UNIQUE (user_fk, right_id);\n",
        buf.toString());
  }

  @Test
  public void buildAddTableColumn()
  {
    logon(AbstractTestBase.ADMIN);
    final StringBuffer buf = new StringBuffer();
    databaseService.buildAddTableAttributesStatement(buf, "t_task",
        new TableAttribute("workpackage_code", TableAttributeType.VARCHAR,
            100, false),
        new TableAttribute("user_fk", TableAttributeType.INT).setForeignTable("t_user").setForeignAttribute("pk"));
    assertEquals("-- Does already exist: ALTER TABLE t_task ADD COLUMN workpackage_code VARCHAR(100) NOT NULL;\n" //
        + "ALTER TABLE t_task ADD COLUMN user_fk INT;\n"
        + "ALTER TABLE t_task ADD CONSTRAINT t_task_user_fk FOREIGN KEY (user_fk) REFERENCES t_user(pk);\n",
        buf.toString());
  }

  @Test
  public void createAndDropTableColumn()
  {
    logon(AbstractTestBase.ADMIN);
    databaseService.addTableAttributes("t_task", new TableAttribute("test1", TableAttributeType.DATE),
        new TableAttribute("test2",
            TableAttributeType.INT));
    assertTrue(databaseService.doesTableAttributeExist("t_task", "test1"));
    assertTrue(databaseService.doesTableAttributeExist("t_task", "test2"));
    databaseService.dropTableAttribute("t_task", "test1");
    assertFalse(databaseService.doesTableAttributeExist("t_task", "test1"));
    assertTrue(databaseService.doesTableAttributeExist("t_task", "test2"));
    databaseService.dropTableAttribute("t_task", "test2");
    assertFalse(databaseService.doesTableAttributeExist("t_task", "test1"));
    assertFalse(databaseService.doesTableAttributeExist("t_task", "test2"));
  }

  @Test
  public void renameTableAttribute()
  {
    assertEquals("ALTER TABLE t_test ALTER COLUMN old_col RENAME TO new_col",
        DatabaseSupport.getInstance().renameAttribute("t_test", "old_col", "new_col"));
  }
}
