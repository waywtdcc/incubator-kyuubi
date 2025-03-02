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

package org.apache.kyuubi.server.metadata.api

import org.apache.kyuubi.session.SessionType.SessionType

/**
 * The metadata to store. It including three parts:
 * 1. session related metadata.
 * 2. request related metadata.
 * 3. engine related metadata.
 *
 * @param identifier the identifier id.
 * @param sessionType the session type, SQL or BATCH.
 * @param realUser the real user.
 * @param username the final user name. If proxy user is used, it is the proxy user.
 *                 Otherwise, it is the real user.
 * @param ipAddress the ip address.
 * @param kyuubiInstance the kyuubi instance.
 * @param state the state.
 * @param resource the main resource.
 * @param className the main class name.
 * @param requestName the request name.
 * @param requestConf the request config map.
 * @param requestArgs the request arguments.
 * @param createTime the create time.
 * @param engineType the engine type.
 * @param clusterManager the engine cluster manager.
 * @param engineId the engine id.
 * @param engineName the engine name.
 * @param engineUrl the engine tracking url.
 * @param engineState the engine state.
 * @param engineError the engine error diagnose.
 * @param endTime the end time.
 */
case class Metadata(
    identifier: String,
    sessionType: SessionType = null,
    realUser: String = null,
    username: String = null,
    ipAddress: String = null,
    kyuubiInstance: String = null,
    state: String = null,
    resource: String = null,
    className: String = null,
    requestName: String = null,
    requestConf: Map[String, String] = Map.empty,
    requestArgs: Seq[String] = Seq.empty,
    createTime: Long = 0L,
    engineType: String = null,
    clusterManager: Option[String] = None,
    engineId: String = null,
    engineName: String = null,
    engineUrl: String = null,
    engineState: String = null,
    engineError: Option[String] = None,
    endTime: Long = 0L)
