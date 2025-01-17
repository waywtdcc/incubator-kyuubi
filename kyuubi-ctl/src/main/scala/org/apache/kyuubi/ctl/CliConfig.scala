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
package org.apache.kyuubi.ctl

import org.apache.kyuubi.ctl.ControlAction.ControlAction
import org.apache.kyuubi.ctl.ControlObject.ControlObject

private[ctl] object ControlAction extends Enumeration {
  type ControlAction = Value
  val CREATE, GET, DELETE, LIST, LOG, SUBMIT = Value
}

private[ctl] object ControlObject extends Enumeration {
  type ControlObject = Value
  val SERVER, ENGINE, BATCH = Value
}

case class CliConfig(
    action: ControlAction = null,
    resource: ControlObject = ControlObject.SERVER,
    commonOpts: CommonOpts = CommonOpts(),
    createOpts: CreateOpts = CreateOpts(),
    logOpts: LogOpts = LogOpts(),
    batchOpts: BatchOpts = BatchOpts(),
    engineOpts: EngineOpts = EngineOpts(),
    conf: Map[String, String] = Map.empty)

case class CommonOpts(
    zkQuorum: String = null,
    namespace: String = null,
    host: String = null,
    port: String = null,
    version: String = null,
    verbose: Boolean = false,
    hostUrl: String = null,
    authSchema: String = null,
    username: String = null,
    password: String = null,
    spnegoHost: String = null)

case class CreateOpts(filename: String = null)

case class LogOpts(forward: Boolean = false)

case class BatchOpts(
    batchId: String = null,
    batchType: String = null,
    batchUser: String = null,
    batchState: String = null,
    createTime: Long = 0,
    endTime: Long = 0,
    from: Int = -1,
    size: Int = 100,
    hs2ProxyUser: String = null)

case class EngineOpts(
    user: String = null,
    engineType: String = null,
    engineSubdomain: String = null,
    engineShareLevel: String = null)
