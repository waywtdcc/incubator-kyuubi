/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kyuubi.engine.jdbc.dialect
import java.sql.{Connection, ResultSet, Statement}

import org.apache.kyuubi.KyuubiSQLException
import org.apache.kyuubi.engine.jdbc.doris.{DorisRowSetHelper, DorisSchemaHelper}
import org.apache.kyuubi.engine.jdbc.schema.{RowSetHelper, SchemaHelper}
import org.apache.kyuubi.operation.Operation
import org.apache.kyuubi.session.Session

class DorisDialect extends JdbcDialect {

  override def createStatement(connection: Connection, fetchSize: Int): Statement = {
    val statement =
      connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    statement.setFetchSize(Integer.MIN_VALUE)
    statement
  }

  override def getTypeInfoOperation(session: Session): Operation = {
    throw KyuubiSQLException.featureNotSupported()
  }

  override def getCatalogsOperation(session: Session): Operation = {
    throw KyuubiSQLException.featureNotSupported()
  }

  override def getSchemasOperation(session: Session): Operation = {
    throw KyuubiSQLException.featureNotSupported()
  }

  override def getTablesOperation(session: Session): Operation = {
    throw KyuubiSQLException.featureNotSupported()
  }

  override def getTableTypesOperation(session: Session): Operation = {
    throw KyuubiSQLException.featureNotSupported()
  }

  override def getColumnsOperation(session: Session): Operation = {
    throw KyuubiSQLException.featureNotSupported()
  }

  override def getFunctionsOperation(session: Session): Operation = {
    throw KyuubiSQLException.featureNotSupported()
  }

  override def getPrimaryKeysOperation(session: Session): Operation = {
    throw KyuubiSQLException.featureNotSupported()
  }

  override def getCrossReferenceOperation(session: Session): Operation = {
    throw KyuubiSQLException.featureNotSupported()
  }

  override def getRowSetHelper(): RowSetHelper = {
    new DorisRowSetHelper
  }

  override def getSchemaHelper(): SchemaHelper = {
    new DorisSchemaHelper
  }

  override def name(): String = {
    "doris"
  }
}
