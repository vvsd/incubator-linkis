/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.linkis.cs.contextcache.cache.csid.impl;

import org.apache.linkis.common.listener.Event;
import org.apache.linkis.cs.contextcache.cache.csid.ContextIDValue;
import org.apache.linkis.cs.contextcache.cache.cskey.ContextKeyValueContext;
import org.apache.linkis.cs.contextcache.metric.ContextIDMetric;
import org.apache.linkis.cs.contextcache.metric.DefaultContextIDMetric;
import org.apache.linkis.cs.contextcache.metric.SizeEstimator;
import org.apache.linkis.cs.listener.CSKeyListener;
import org.apache.linkis.cs.listener.event.ContextKeyEvent;
import org.apache.linkis.cs.listener.event.impl.DefaultContextKeyEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.linkis.cs.listener.event.enumeration.OperateType.*;

public class ContextIDValueImpl implements ContextIDValue, CSKeyListener {

  private static final Logger logger = LoggerFactory.getLogger(ContextIDValueImpl.class);

  private String contextID;

  private ContextKeyValueContext contextKeyValueContext;

  private ContextIDMetric contextIDMetric = new DefaultContextIDMetric();

  public ContextIDValueImpl() {}

  public ContextIDValueImpl(String contextID, ContextKeyValueContext contextKeyValueContext) {
    this.contextID = contextID;
    this.contextKeyValueContext = contextKeyValueContext;
  }

  @Override
  public String getContextID() {
    return this.contextID;
  }

  @Override
  public ContextKeyValueContext getContextKeyValueContext() {
    return this.contextKeyValueContext;
  }

  @Override
  public void refresh() {
    // TODO
  }

  @Override
  public ContextIDMetric getContextIDMetric() {
    return this.contextIDMetric;
  }

  @Override
  public void onEvent(Event event) {
    DefaultContextKeyEvent defaultContextKeyEvent = null;
    if (event != null && event instanceof DefaultContextKeyEvent) {
      defaultContextKeyEvent = (DefaultContextKeyEvent) event;
    }
    if (null == defaultContextKeyEvent) {
      return;
    }
    if (ACCESS.equals(defaultContextKeyEvent.getOperateType())) {
      onCSKeyAccess(defaultContextKeyEvent);
    } else {
      onCSKeyUpdate(defaultContextKeyEvent);
    }
  }

  @Override
  public void onCSKeyUpdate(ContextKeyEvent contextKeyEvent) {

    DefaultContextKeyEvent defaultContextKeyEvent = (DefaultContextKeyEvent) contextKeyEvent;
    logger.debug("Start to deal csKeyEvent of csID({})", this.contextID);
    if (ADD == defaultContextKeyEvent.getOperateType()) {
      Long size = SizeEstimator.estimate(defaultContextKeyEvent.getContextKeyValue());
      this.contextIDMetric.setMemory(getContextIDMetric().getMemory() + size);
    } else if (DELETE == defaultContextKeyEvent.getOperateType()) {
      Long size = SizeEstimator.estimate(defaultContextKeyEvent.getContextKeyValue());
      this.contextIDMetric.setMemory(getContextIDMetric().getMemory() - size);
    } else if (REMOVEALL == defaultContextKeyEvent.getOperateType()) {
      Long size = SizeEstimator.estimate(getContextKeyValueContext());
      this.contextIDMetric.setMemory(size);
    } else {
      long size =
          SizeEstimator.estimate(defaultContextKeyEvent.getContextKeyValue())
              - SizeEstimator.estimate(defaultContextKeyEvent.getOldValue());
      this.contextIDMetric.setMemory(getContextIDMetric().getMemory() - size);
    }
    logger.info(
        "Now, The Memory of ContextID({}) are {}", contextID, getContextIDMetric().getMemory());
    if (logger.isDebugEnabled()) {
      logger.debug("Finished to deal csKeyEvent of csID({})", this.contextID);
    }
  }

  @Override
  public void onCSKeyAccess(ContextKeyEvent contextKeyEvent) {
    // TODO null
  }

  @Override
  public void onEventError(Event event, Throwable t) {
    logger.error("Failed to deal event", t);
  }
}
