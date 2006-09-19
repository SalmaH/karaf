/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */
package org.apache.felix.mosgi.managedelements.obrprobe;

/**
 * TODO : Should listen to Agent Service lifecycle
 *        Need to change ObjectName
 *        Should listen to serviceLifecycle
**/

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.NotificationBroadcasterSupport;
import javax.management.AttributeChangeNotification;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;

//import org.apache.felix.bundlerepository.BundleRepository;
import org.osgi.service.obr.RepositoryAdmin;
import org.osgi.service.obr.Resolver;
import org.osgi.service.obr.Resource;
import org.osgi.framework.Version;

import org.osgi.service.log.LogService;

public class ObrProbe implements BundleActivator, ObrProbeMBean {

  private String version = null;
  private static String tabNameString = "TabUI:name=ObrProbe";

  private ObjectName tabName = null;
  private MBeanServer server = null;
  private BundleContext bc = null;
  private ServiceReference sr = null;


  ////////////////////////////////////////////////////////
  //     TabIfc (from ObrProbeMBean)                  //
  ////////////////////////////////////////////////////////
  public String getBundleName() {
    return this.bc.getProperty("mosgi.jmxconsole.tab.url.obrprobetab");
  }


  ////////////////////////////////////////////////////////
  //       BundleActivator                              //
  ////////////////////////////////////////////////////////
  public void start(BundleContext context) throws Exception  {
    this.bc=context;
    this.version=(String)bc.getBundle().getHeaders().get(Constants.BUNDLE_VERSION);
    this.log(LogService.LOG_INFO, "Starting obrProbe MBean " + this.version,null);
    this.tabName=new ObjectName(tabNameString);
    this.sr = context.getServiceReference(MBeanServer.class.getName());
    if (sr!=null){
      this.connectToAgent(sr);
    }
    this.log(LogService.LOG_INFO, "ObrProbe MBean "+this.version+" started", null);
  }


  public void stop(BundleContext context) {
    this.log(LogService.LOG_INFO, "Stopping obrprobe MBean "+this.version, null);
    if (this.server!=null){
      this.disconnectFromAgent();
    }
    this.sr=null;
    this.log(LogService.LOG_INFO, "obrProbe MBean "+this.version+" stopped", null);
    this.bc=null;
  }

  ////////////////////////////////////////////////////////
  //       ObrProbeMBean                              //
  ////////////////////////////////////////////////////////
  public void deploy(String location,String version){
    ServiceReference sr=this.bc.getServiceReference(RepositoryAdmin.class.getName());

    System.out.println("Starting "+location+" "+version);
    if (sr!=null){
      RepositoryAdmin brs=(RepositoryAdmin)this.bc.getService(sr);
      Resolver resolver=brs.resolver();
      Resource ressource = selectNewestVersion(searchRepository(brs, location, version));
      if (ressource!=null){
	resolver.add(ressource);
      }
      if ((resolver.getAddedResources() != null) &&
            (resolver.getAddedResources().length > 0)) {
        if (resolver.resolve()) {
          try{
            resolver.deploy(true); //Bundles are started
          }catch (IllegalStateException ex) {
            System.out.println(ex);
          }
        }
      }
    }else{
      this.log(LogService.LOG_ERROR, "No BundleRepository Service", null);
    }
  }

  ////////////////////////////////////////////////////////
  //       ServiceListener                              //
  ////////////////////////////////////////////////////////
  public void serviceChanged(ServiceEvent event) {
    ServiceReference sr=event.getServiceReference();
    Object service=bc.getService(sr);
    if (this.server==null && event.getType()==ServiceEvent.REGISTERED && service instanceof MBeanServer){
      this.connectToAgent(sr);
    }
    if (this.server!=null){
      if(event.getType()==ServiceEvent.UNREGISTERING && service instanceof MBeanServer){
        this.disconnectFromAgent();
      }
    }
  }

  private void connectToAgent(ServiceReference sr){
    this.log(LogService.LOG_INFO, "Registering to agent", null);
    try{
      this.server=(MBeanServer)this.bc.getService(sr);
      this.server.registerMBean(this, tabName);
    }catch (Exception e){
      e.printStackTrace();
    }
    this.log(LogService.LOG_INFO, "Registered to agent", null);
  }
  
  private void disconnectFromAgent(){
    this.log(LogService.LOG_INFO, "Unregistering from agent", null);
    try {
      server.unregisterMBean(tabName);
    } catch (Exception e) {
      e.printStackTrace();
    }
    this.server=null;
    this.bc.ungetService(this.sr);
    this.log(LogService.LOG_INFO, "Unregistered from agent", null);
  }

  private void log(int prio, String message, Throwable t){
    if (this.bc!=null){
      ServiceReference logSR=this.bc.getServiceReference(LogService.class.getName());
      if (logSR!=null){
        ((LogService)this.bc.getService(logSR)).log(prio, message, t);
      }else{
        System.out.println("No Log Service");
      }
    }else{
      System.out.println(this.getClass().getName()+".log: No bundleContext");
    }
  }

  private Resource[] searchRepository(RepositoryAdmin brs, String targetId, String targetVersion)
  {
        // Try to see if the targetId is a bundle ID.
        try
        {
            Bundle bundle = bc.getBundle(Long.parseLong(targetId));
            targetId = bundle.getSymbolicName();
        }
        catch (NumberFormatException ex)
        {
            // It was not a number, so ignore.
        }

        // The targetId may be a bundle name or a bundle symbolic name,
        // so create the appropriate LDAP query.
        StringBuffer sb = new StringBuffer("(|(presentationname=");
        sb.append(targetId);
        sb.append(")(symbolicname=");
        sb.append(targetId);
        sb.append("))");
        if (targetVersion != null)
        {
            sb.insert(0, "(&");
            sb.append("(version=");
            sb.append(targetVersion);
            sb.append("))");
        }
        return brs.discoverResources(sb.toString());
    }

    private Resource selectNewestVersion(Resource[] resources)
    {
        int idx = -1;
        Version v = null;
        for (int i = 0; (resources != null) && (i < resources.length); i++)
        {
            if (i == 0)
            {
                idx = 0;
                v = resources[i].getVersion();
            }
            else
            {
                Version vtmp = resources[i].getVersion();
                if (vtmp.compareTo(v) > 0)
                {
                    idx = i;
                    v = vtmp;
                }
            }
        }

        return (idx < 0) ? null : resources[idx];
    }


}
