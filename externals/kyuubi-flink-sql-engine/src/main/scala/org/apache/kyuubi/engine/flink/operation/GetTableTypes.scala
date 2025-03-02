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

package org.apache.kyuubi.engine.flink.operation

import org.apache.kyuubi.engine.flink.result.{Constants, ResultSetUtil}
import org.apache.kyuubi.operation.OperationType
import org.apache.kyuubi.operation.meta.ResultSetSchemaConstant.TABLE_TYPE
import org.apache.kyuubi.session.Session

class GetTableTypes(session: Session)
  extends FlinkOperation(OperationType.GET_TABLE_TYPES, session) {

  override protected def runInternal(): Unit = {
    val tableTypes = Constants.SUPPORTED_TABLE_TYPES.toList
    resultSet = ResultSetUtil.stringListToResultSet(tableTypes, TABLE_TYPE)
  }
}
