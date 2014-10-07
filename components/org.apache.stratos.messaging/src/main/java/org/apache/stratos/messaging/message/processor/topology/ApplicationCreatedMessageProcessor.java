/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.stratos.messaging.message.processor.topology;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.messaging.domain.topology.Cluster;
import org.apache.stratos.messaging.domain.topology.Topology;
import org.apache.stratos.messaging.event.topology.ApplicationCreatedEvent;
import org.apache.stratos.messaging.message.processor.MessageProcessor;
import org.apache.stratos.messaging.util.Util;

public class ApplicationCreatedMessageProcessor extends MessageProcessor {

    private static final Log log = LogFactory.getLog(ApplicationCreatedMessageProcessor.class);
    private MessageProcessor nextProcessor;

    @Override
    public void setNext(MessageProcessor nextProcessor) {
        this.nextProcessor = nextProcessor;
    }

    @Override
    public boolean process(String type, String message, Object object) {

        Topology topology = (Topology) object;

        if (ApplicationCreatedEvent.class.getName().equals(type)) {
            if (!topology.isInitialized()) {
                return false;
            }

            ApplicationCreatedEvent appCreatedEvent = (ApplicationCreatedEvent) Util.jsonToObject(message, ApplicationCreatedEvent.class);
            if (appCreatedEvent == null) {
                log.error("Unable to convert the JSON message to ApplicationCreatedEvent");
                return false;
            }

            // check if required properties are available
            if (appCreatedEvent.getApplication() == null) {
                String errorMsg = "Application object of application created event is invalid";
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            if (appCreatedEvent.getApplication().getId() == null || appCreatedEvent.getApplication().getId().isEmpty()) {
                String errorMsg = "App id of application created event is invalid: [ " + appCreatedEvent.getApplication().getId() + " ]";
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            // check if an Application with same name exists in topology
            if (topology.applicationExists(appCreatedEvent.getApplication().getId())) {
                log.warn("Application with id [ " + appCreatedEvent.getApplication().getId() + " ] already exists in Topology");

            } else {
                // add application and the clusters to Topology
                for(Cluster cluster: appCreatedEvent.getClusterList()) {
                    topology.getService(cluster.getServiceName()).addCluster(cluster);
                }
                topology.addApplication(appCreatedEvent.getApplication());
            }

            notifyEventListeners(appCreatedEvent);
            return true;

        } else {
            if (nextProcessor != null) {
                // ask the next processor to take care of the message.
                return nextProcessor.process(type, message, topology);
            } else {
                throw new RuntimeException(String.format("Failed to process message using available message processors: [type] %s [body] %s", type, message));
            }
        }
    }
}