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

package org.apache.spark.shuffle.celeborn

import org.apache.celeborn.client.LifecycleManager
import org.apache.celeborn.common.CelebornConf
import org.apache.celeborn.common.internal.Logging

class RssShuffleFallbackPolicyRunner(conf: CelebornConf) extends Logging {

  def applyAllFallbackPolicy(lifecycleManager: LifecycleManager, numPartitions: Int): Boolean = {
    applyForceFallbackPolicy() || applyShufflePartitionsFallbackPolicy(numPartitions) ||
    !checkQuota(lifecycleManager)
  }

  /**
   * if rss.force.fallback is true, fallback to external shuffle
   * @return return rss.force.fallback
   */
  def applyForceFallbackPolicy(): Boolean = CelebornConf.forceFallback(conf)

  /**
   * if shuffle partitions > rss.max.partition.number, fallback to external shuffle
   * @param numPartitions shuffle partitions
   * @return return if shuffle partitions bigger than limit
   */
  def applyShufflePartitionsFallbackPolicy(numPartitions: Int): Boolean = {
    val confNumPartitions = CelebornConf.maxPartitionNumSupported(conf)
    val needFallback = numPartitions >= confNumPartitions
    if (needFallback) {
      logInfo(s"Shuffle num of partitions: $numPartitions" +
        s" is bigger than the limit: $confNumPartitions," +
        s" need fallback to spark shuffle")
    }
    needFallback
  }

  /**
   * If rss cluster is exceed current user's quota, fallback to external shuffle
   *
   * @return if rss cluster usage of current user's percent is overhead the limit
   */
  def checkQuota(lifecycleManager: LifecycleManager): Boolean = {
    if (!CelebornConf.clusterCheckQuotaEnabled(conf)) {
      return true
    }

    val available = lifecycleManager.checkQuota()
    if (!available) {
      logWarning(s"Cluster is not alive!")
    }
    available
  }
}