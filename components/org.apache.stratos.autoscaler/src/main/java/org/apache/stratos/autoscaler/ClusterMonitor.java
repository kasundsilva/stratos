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
package org.apache.stratos.autoscaler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.deployment.policy.DeploymentPolicy;
import org.apache.stratos.autoscaler.policy.model.AutoscalePolicy;
import org.apache.stratos.autoscaler.rule.AutoscalerRuleEvaluator;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.rule.FactHandle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Is responsible for monitoring a service cluster. This runs periodically
 * and perform minimum instance check and scaling check using the underlying
 * rules engine.
 *
 */
public class ClusterMonitor implements Runnable{

    private static final Log log = LogFactory.getLog(ClusterMonitor.class);
    private String clusterId;

    private String serviceId;

    //key: network partition id, value: Network partition context
    private Map<String, NetworkPartitionContext> networkPartitionCtxts;


    private StatefulKnowledgeSession minCheckKnowledgeSession;
    private StatefulKnowledgeSession scaleCheckKnowledgeSession;
    private boolean isDestroyed;

    private DeploymentPolicy deploymentPolicy;
    private AutoscalePolicy autoscalePolicy;

        // Key- MemberId Value- partitionId
    private Map<String, String> memberPartitionMap;

    private FactHandle minCheckFactHandle;
    private FactHandle scaleCheckFactHandle;

    private AutoscalerRuleEvaluator autoscalerRuleEvaluator;

    public ClusterMonitor(String clusterId, String serviceId, DeploymentPolicy deploymentPolicy,
                          AutoscalePolicy autoscalePolicy) {
        this.clusterId = clusterId;
        this.serviceId = serviceId;

        this.autoscalerRuleEvaluator = new AutoscalerRuleEvaluator();
        this.scaleCheckKnowledgeSession = autoscalerRuleEvaluator.getScaleCheckStatefulSession();
        this.minCheckKnowledgeSession = autoscalerRuleEvaluator.getMinCheckStatefulSession();

        this.deploymentPolicy = deploymentPolicy;
        this.deploymentPolicy = deploymentPolicy;
        networkPartitionCtxts = new ConcurrentHashMap<String, NetworkPartitionContext>();
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public Map<String, NetworkPartitionContext> getNetworkPartitionCtxts() {
        return networkPartitionCtxts;
    }

    public NetworkPartitionContext getNetworkPartitionCtxt(String networkPartitionId) {
        return networkPartitionCtxts.get(networkPartitionId);
    }

    public void setPartitionCtxt(Map<String, NetworkPartitionContext> partitionCtxt) {
        this.networkPartitionCtxts = partitionCtxt;
    }

    public boolean partitionCtxtAvailable(String partitionId) {
        return networkPartitionCtxts.containsKey(partitionId);
    }

    public void addNetworkPartitionCtxt(NetworkPartitionContext ctxt) {
        this.networkPartitionCtxts.put(ctxt.getId(), ctxt);
    }
    
    public NetworkPartitionContext getPartitionCtxt(String id) {
        return this.networkPartitionCtxts.get(id);
    }

    public StatefulKnowledgeSession getMinCheckKnowledgeSession() {
        return minCheckKnowledgeSession;
    }

    public void setMinCheckKnowledgeSession(StatefulKnowledgeSession minCheckKnowledgeSession) {
        this.minCheckKnowledgeSession = minCheckKnowledgeSession;
    }

    public FactHandle getMinCheckFactHandle() {
        return minCheckFactHandle;
    }

    public void setMinCheckFactHandle(FactHandle minCheckFactHandle) {
        this.minCheckFactHandle = minCheckFactHandle;
    }

    @Override
    public void run() {

        while (!isDestroyed()) {
            if (log.isDebugEnabled()) {
                log.debug("Cluster monitor is running..");
            }
            try {
                monitor();
            } catch (Exception e) {
                log.error("Cluster monitor: Monitor failed.", e);
            }
            try {
                // TODO make this configurable
                Thread.sleep(30000);
            } catch (InterruptedException ignore) {
            }
        }
    }
    
    private void monitor() {
//        if(clusterCtxt != null ) {
            //TODO make this concurrent
        for (NetworkPartitionContext networkPartitionContext : networkPartitionCtxts.values()) {

            //minimum check per partition
            for(PartitionContext partitionContext: networkPartitionContext.getPartitionCtxts().values()){

                minCheckKnowledgeSession.setGlobal("clusterId", clusterId);

                if (log.isDebugEnabled()) {
                    log.debug(String.format("Running minimum check for partition %s ", partitionContext.getPartitionId()));
                }

                minCheckFactHandle = AutoscalerRuleEvaluator.evaluateMinCheck(minCheckKnowledgeSession
                        , minCheckFactHandle, partitionContext);

            }

            scaleCheckKnowledgeSession.setGlobal("clusterId", clusterId);
            scaleCheckKnowledgeSession.setGlobal("deploymentPolicy", deploymentPolicy);
            scaleCheckKnowledgeSession.setGlobal("autoscalePolicy", autoscalePolicy);

            if (log.isDebugEnabled()) {
                log.debug(String.format("Running scale check for network partition %s ", networkPartitionContext.getId()));
            }

            scaleCheckFactHandle = AutoscalerRuleEvaluator.evaluateScaleCheck(scaleCheckKnowledgeSession
                    , scaleCheckFactHandle, networkPartitionContext);

        }
    }

    
    public void destroy() {
        minCheckKnowledgeSession.dispose();
        scaleCheckKnowledgeSession.dispose();
        setDestroyed(true);
        if(log.isDebugEnabled()) {
            log.debug("Cluster Monitor Drools session has been disposed.");
        }
    }

    public boolean isDestroyed() {
        return isDestroyed;
    }

    public void setDestroyed(boolean isDestroyed) {
        this.isDestroyed = isDestroyed;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public DeploymentPolicy getDeploymentPolicy() {
        return deploymentPolicy;
    }

    public void setDeploymentPolicy(DeploymentPolicy deploymentPolicy) {
        this.deploymentPolicy = deploymentPolicy;
    }

    public AutoscalePolicy getAutoscalePolicy() {
        return autoscalePolicy;
    }

    public void setAutoscalePolicy(AutoscalePolicy autoscalePolicy) {
        this.autoscalePolicy = autoscalePolicy;
    }

    public String getPartitonOfMember(String memberId){
   		return this.memberPartitionMap.get(memberId);
   	}

   	public boolean memberExist(String memberId){
   		return this.memberPartitionMap.containsKey(memberId);
   	}
}
