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

package org.apache.kyuubi.engine

import java.util.UUID
import java.util.concurrent.Executors

import org.apache.hadoop.security.UserGroupInformation
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import org.apache.kyuubi.{KYUUBI_VERSION, KyuubiFunSuite, Utils}
import org.apache.kyuubi.config.KyuubiConf
import org.apache.kyuubi.config.KyuubiConf._
import org.apache.kyuubi.ha.HighAvailabilityConf
import org.apache.kyuubi.ha.client.DiscoveryClientProvider
import org.apache.kyuubi.ha.client.DiscoveryPaths
import org.apache.kyuubi.metrics.MetricsConstants.ENGINE_TOTAL
import org.apache.kyuubi.metrics.MetricsSystem
import org.apache.kyuubi.util.NamedThreadFactory
import org.apache.kyuubi.zookeeper.{EmbeddedZookeeper, ZookeeperConf}

class EngineRefSuite extends KyuubiFunSuite {
  import EngineType._
  import ShareLevel._
  private val zkServer = new EmbeddedZookeeper
  private val conf = KyuubiConf()
  private val user = Utils.currentUser
  private val metricsSystem = new MetricsSystem

  override def beforeAll(): Unit = {
    val zkData = Utils.createTempDir()
    conf.set(ZookeeperConf.ZK_DATA_DIR, zkData.toString)
      .set(ZookeeperConf.ZK_CLIENT_PORT, 0)
      .set("spark.sql.catalogImplementation", "in-memory")
    zkServer.initialize(conf)
    zkServer.start()
    metricsSystem.initialize(conf)
    metricsSystem.start()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    metricsSystem.stop()
    zkServer.stop()
    super.afterAll()
  }

  override def beforeEach(): Unit = {
    conf.unset(KyuubiConf.ENGINE_SHARE_LEVEL_SUBDOMAIN)
    conf.unset(KyuubiConf.ENGINE_SHARE_LEVEL_SUB_DOMAIN)
    conf.unset(KyuubiConf.ENGINE_POOL_SIZE)
    conf.unset(KyuubiConf.ENGINE_POOL_NAME)
    super.beforeEach()
  }

  test("CONNECTION shared level engine name") {
    val id = UUID.randomUUID().toString
    val engineType = conf.get(KyuubiConf.ENGINE_TYPE)
    Seq(None, Some("suffix")).foreach { domain =>
      conf.set(KyuubiConf.ENGINE_SHARE_LEVEL, CONNECTION.toString)
      domain.foreach(conf.set(KyuubiConf.ENGINE_SHARE_LEVEL_SUBDOMAIN.key, _))
      val engine = new EngineRef(conf, user, id, null)
      assert(engine.engineSpace ===
        DiscoveryPaths.makePath(
          s"kyuubi_${KYUUBI_VERSION}_${CONNECTION}_${engineType}",
          user,
          Array(id)))
      assert(engine.defaultEngineName === s"kyuubi_${CONNECTION}_${engineType}_${user}_$id")
    }
  }

  test("USER shared level engine name") {
    val id = UUID.randomUUID().toString
    conf.set(KyuubiConf.ENGINE_SHARE_LEVEL, USER.toString)
    conf.set(KyuubiConf.ENGINE_TYPE, FLINK_SQL.toString)
    val appName = new EngineRef(conf, user, id, null)
    assert(appName.engineSpace ===
      DiscoveryPaths.makePath(
        s"kyuubi_${KYUUBI_VERSION}_${USER}_$FLINK_SQL",
        user,
        Array("default")))
    assert(appName.defaultEngineName === s"kyuubi_${USER}_${FLINK_SQL}_${user}_default_$id")

    Seq(KyuubiConf.ENGINE_SHARE_LEVEL_SUBDOMAIN, KyuubiConf.ENGINE_SHARE_LEVEL_SUB_DOMAIN).foreach {
      k =>
        conf.unset(KyuubiConf.ENGINE_SHARE_LEVEL_SUBDOMAIN)
        conf.set(k.key, "abc")
        val appName2 = new EngineRef(conf, user, id, null)
        assert(appName2.engineSpace ===
          DiscoveryPaths.makePath(
            s"kyuubi_${KYUUBI_VERSION}_${USER}_${FLINK_SQL}",
            user,
            Array("abc")))
        assert(appName2.defaultEngineName === s"kyuubi_${USER}_${FLINK_SQL}_${user}_abc_$id")
    }
  }

  test("GROUP shared level engine name") {
    val id = UUID.randomUUID().toString
    conf.set(KyuubiConf.ENGINE_SHARE_LEVEL, GROUP.toString)
    conf.set(KyuubiConf.ENGINE_TYPE, SPARK_SQL.toString)
    val engineRef = new EngineRef(conf, user, id, null)
    val primaryGroupName = UserGroupInformation.createRemoteUser(user).getPrimaryGroupName
    assert(engineRef.engineSpace ===
      DiscoveryPaths.makePath(
        s"kyuubi_${KYUUBI_VERSION}_GROUP_SPARK_SQL",
        primaryGroupName,
        Array("default")))
    assert(engineRef.defaultEngineName ===
      s"kyuubi_GROUP_SPARK_SQL_${primaryGroupName}_default_$id")

    Seq(KyuubiConf.ENGINE_SHARE_LEVEL_SUBDOMAIN, KyuubiConf.ENGINE_SHARE_LEVEL_SUB_DOMAIN).foreach {
      k =>
        conf.unset(k)
        conf.set(k.key, "abc")
        val engineRef2 = new EngineRef(conf, user, id, null)
        assert(engineRef2.engineSpace ===
          DiscoveryPaths.makePath(
            s"kyuubi_${KYUUBI_VERSION}_${GROUP}_${SPARK_SQL}",
            primaryGroupName,
            Array("abc")))
        assert(engineRef2.defaultEngineName ===
          s"kyuubi_${GROUP}_${SPARK_SQL}_${primaryGroupName}_abc_$id")
    }

    val userName = "Iamauserwithoutgroup"
    val newUGI = UserGroupInformation.createRemoteUser(userName)
    assert(newUGI.getGroupNames.isEmpty)
    val engineRef3 = new EngineRef(conf, userName, id, null)
    assert(engineRef3.engineSpace ===
      DiscoveryPaths.makePath(
        s"kyuubi_${KYUUBI_VERSION}_GROUP_SPARK_SQL",
        userName,
        Array("abc")))
    assert(engineRef3.defaultEngineName === s"kyuubi_GROUP_SPARK_SQL_${userName}_abc_$id")
  }

  test("SERVER shared level engine name") {
    val id = UUID.randomUUID().toString
    conf.set(KyuubiConf.ENGINE_SHARE_LEVEL, SERVER.toString)
    conf.set(KyuubiConf.ENGINE_TYPE, FLINK_SQL.toString)
    val appName = new EngineRef(conf, user, id, null)
    assert(appName.engineSpace ===
      DiscoveryPaths.makePath(
        s"kyuubi_${KYUUBI_VERSION}_${SERVER}_${FLINK_SQL}",
        user,
        Array("default")))
    assert(appName.defaultEngineName === s"kyuubi_${SERVER}_${FLINK_SQL}_${user}_default_$id")

    conf.set(KyuubiConf.ENGINE_SHARE_LEVEL_SUBDOMAIN.key, "abc")
    val appName2 = new EngineRef(conf, user, id, null)
    assert(appName2.engineSpace ===
      DiscoveryPaths.makePath(
        s"kyuubi_${KYUUBI_VERSION}_${SERVER}_${FLINK_SQL}",
        user,
        Array("abc")))
    assert(appName2.defaultEngineName === s"kyuubi_${SERVER}_${FLINK_SQL}_${user}_abc_$id")
  }

  test("check the engine space of engine pool") {
    val id = UUID.randomUUID().toString

    // set subdomain and disable engine pool
    conf.set(ENGINE_SHARE_LEVEL_SUBDOMAIN.key, "abc")
    conf.set(ENGINE_POOL_SIZE, -1)
    val engine1 = new EngineRef(conf, user, id, null)
    assert(engine1.subdomain === "abc")

    // unset subdomain and disable engine pool
    conf.unset(ENGINE_SHARE_LEVEL_SUBDOMAIN)
    conf.set(ENGINE_POOL_SIZE, -1)
    val engine2 = new EngineRef(conf, user, id, null)
    assert(engine2.subdomain === "default")

    // set subdomain and 1 <= engine pool size < threshold
    conf.set(ENGINE_SHARE_LEVEL_SUBDOMAIN.key, "abc")
    conf.set(ENGINE_POOL_SIZE, 1)
    val engine3 = new EngineRef(conf, user, id, null)
    assert(engine3.subdomain === "abc")

    // unset subdomain and 1 <= engine pool size < threshold
    conf.unset(ENGINE_SHARE_LEVEL_SUBDOMAIN)
    conf.set(ENGINE_POOL_SIZE, 3)
    val engine4 = new EngineRef(conf, user, id, null)
    assert(engine4.subdomain.startsWith("engine-pool-"))

    // unset subdomain and engine pool size > threshold
    conf.unset(ENGINE_SHARE_LEVEL_SUBDOMAIN)
    conf.set(ENGINE_POOL_SIZE, 100)
    val engine5 = new EngineRef(conf, user, id, null)
    val engineNumber = Integer.parseInt(engine5.subdomain.substring(12))
    val threshold = ENGINE_POOL_SIZE_THRESHOLD.defaultVal.get
    assert(engineNumber <= threshold)

    // unset subdomain and set engine pool name and 1 <= engine pool size < threshold
    conf.unset(ENGINE_SHARE_LEVEL_SUBDOMAIN)
    val enginePoolName = "test-pool"
    conf.set(ENGINE_POOL_NAME, enginePoolName)
    conf.set(ENGINE_POOL_SIZE, 3)
    val engine6 = new EngineRef(conf, user, id, null)
    assert(engine6.subdomain.startsWith(s"$enginePoolName-"))
  }

  test("start and get engine address with lock") {
    val id = UUID.randomUUID().toString
    conf.set(KyuubiConf.ENGINE_SHARE_LEVEL, USER.toString)
    conf.set(KyuubiConf.ENGINE_TYPE, SPARK_SQL.toString)
    conf.set(KyuubiConf.FRONTEND_THRIFT_BINARY_BIND_PORT, 0)
    conf.set(HighAvailabilityConf.HA_NAMESPACE, "engine_test")
    conf.set(HighAvailabilityConf.HA_ADDRESSES, zkServer.getConnectString)
    val engine = new EngineRef(conf, user, id, null)

    var port1 = 0
    var port2 = 0

    val r1 = new Runnable {
      override def run(): Unit = {
        DiscoveryClientProvider.withDiscoveryClient(conf) { client =>
          val hp = engine.getOrCreate(client)
          port1 = hp._2
        }
      }
    }

    val r2 = new Runnable {
      override def run(): Unit = {
        DiscoveryClientProvider.withDiscoveryClient(conf) { client =>
          val hp = engine.getOrCreate(client)
          port2 = hp._2
        }
      }
    }
    val factory = new NamedThreadFactory("engine-test", false)
    val thread1 = factory.newThread(r1)
    val thread2 = factory.newThread(r2)
    thread1.start()
    thread2.start()

    eventually(timeout(90.seconds), interval(1.second)) {
      assert(port1 != 0, "engine started")
      assert(port2 == port1, "engine shared")
    }
  }

  test("different engine type should use its own lock") {
    conf.set(KyuubiConf.ENGINE_SHARE_LEVEL, USER.toString)
    conf.set(KyuubiConf.FRONTEND_THRIFT_BINARY_BIND_PORT, 0)
    conf.set(KyuubiConf.ENGINE_INIT_TIMEOUT, 3000L)
    conf.set(HighAvailabilityConf.HA_ZK_NAMESPACE, "engine_test1")
    conf.set(HighAvailabilityConf.HA_ZK_QUORUM, zkServer.getConnectString)
    val conf1 = conf.clone
    conf1.set(KyuubiConf.ENGINE_TYPE, SPARK_SQL.toString)
    val conf2 = conf.clone
    conf2.set(KyuubiConf.ENGINE_TYPE, HIVE_SQL.toString)

    val start = System.currentTimeMillis()
    val times = new Array[Long](2)
    val executor = Executors.newFixedThreadPool(2)
    try {
      executor.execute(() => {
        DiscoveryClientProvider.withDiscoveryClient(conf1) { client =>
          try {
            new EngineRef(conf1, user, UUID.randomUUID().toString, null)
              .getOrCreate(client)
          } finally {
            times(0) = System.currentTimeMillis()
          }
        }
      })
      executor.execute(() => {
        DiscoveryClientProvider.withDiscoveryClient(conf2) { client =>
          try {
            new EngineRef(conf2, user, UUID.randomUUID().toString, null)
              .getOrCreate(client)
          } finally {
            times(1) = System.currentTimeMillis()
          }
        }
      })

      eventually(timeout(10.seconds), interval(200.milliseconds)) {
        assert(times.forall(_ > start))
        // ENGINE_INIT_TIMEOUT is 3000ms
        assert(times.max - times.min < 2500)
      }
    } finally {
      executor.shutdown()
    }
  }

  test("three same lock request with initialization timeout") {
    val id = UUID.randomUUID().toString
    conf.set(KyuubiConf.ENGINE_SHARE_LEVEL, USER.toString)
    conf.set(KyuubiConf.ENGINE_TYPE, SPARK_SQL.toString)
    conf.set(KyuubiConf.FRONTEND_THRIFT_BINARY_BIND_PORT, 0)
    conf.set(KyuubiConf.ENGINE_INIT_TIMEOUT, 3000L)
    conf.set(HighAvailabilityConf.HA_ZK_NAMESPACE, "engine_test2")
    conf.set(HighAvailabilityConf.HA_ZK_QUORUM, zkServer.getConnectString)

    val beforeEngines = MetricsSystem.counterValue(ENGINE_TOTAL).getOrElse(0L)
    val start = System.currentTimeMillis()
    val times = new Array[Long](3)
    val executor = Executors.newFixedThreadPool(3)
    try {
      (0 until (3)).foreach { i =>
        val cloned = conf.clone
        executor.execute(() => {
          DiscoveryClientProvider.withDiscoveryClient(cloned) { client =>
            try {
              new EngineRef(cloned, user, id, null).getOrCreate(client)
            } finally {
              times(i) = System.currentTimeMillis()
            }
          }
        })
      }

      eventually(timeout(20.seconds), interval(200.milliseconds)) {
        assert(times.forall(_ > start))
        // ENGINE_INIT_TIMEOUT is 3000ms
        assert(times.max - times.min > 2800)
      }

      // we should only submit two engines, the last request should timeout and fail
      assert(MetricsSystem.counterValue(ENGINE_TOTAL).get - beforeEngines == 2)
    } finally {
      executor.shutdown()
    }
  }
}
