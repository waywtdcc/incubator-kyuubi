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
package org.apache.kyuubi.ctl.util

import java.net.InetAddress
import java.nio.file.{Files, Paths}

import org.apache.commons.lang3.StringUtils

import org.apache.kyuubi.KyuubiException
import org.apache.kyuubi.ctl.CliConfig

private[ctl] object Validator {

  def validateZkArguments(cliConfig: CliConfig): Unit = {
    if (cliConfig.commonOpts.zkQuorum == null) {
      fail("Zookeeper quorum is not specified and no default value to load")
    }
    if (cliConfig.commonOpts.namespace == null) {
      fail("Zookeeper namespace is not specified and no default value to load")
    }
  }

  def validateHostAndPort(cliConfig: CliConfig): Unit = {
    if (cliConfig.commonOpts.host == null) {
      fail("Must specify host for service")
    }
    if (cliConfig.commonOpts.port == null) {
      fail("Must specify port for service")
    }

    try {
      InetAddress.getByName(cliConfig.commonOpts.host)
    } catch {
      case _: Exception =>
        fail(s"Unknown host: ${cliConfig.commonOpts.host}")
    }

    try {
      if (cliConfig.commonOpts.port.toInt <= 0) {
        fail(s"Specified port should be a positive number")
      }
    } catch {
      case _: NumberFormatException =>
        fail(s"Specified port is not a valid integer number: " +
          s"${cliConfig.commonOpts.port}")
    }
  }

  def validateFilename(cliConfig: CliConfig): Unit = {
    var filename = cliConfig.createOpts.filename
    if (StringUtils.isBlank(filename)) {
      fail(s"Config file is not specified.")
    }

    val currentPath = Paths.get("").toAbsolutePath
    if (!filename.startsWith("/")) {
      filename = currentPath + "/" + filename
    }
    if (!Files.exists(Paths.get(filename))) {
      fail(s"Config file does not exist: ${filename}.")
    }
  }

  private def fail(msg: String): Unit = throw new KyuubiException(msg)
}
