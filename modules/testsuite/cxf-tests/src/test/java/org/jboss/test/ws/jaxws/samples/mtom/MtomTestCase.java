/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.test.ws.jaxws.samples.mtom;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import junit.framework.Test;

import org.apache.cxf.helpers.IOUtils;
import org.jboss.ws.common.DOMUtils;
import org.jboss.wsf.test.JBossWSCXFTestSetup;
import org.jboss.wsf.test.JBossWSTest;
import org.jboss.wsf.test.JBossWSTestHelper;
import org.jboss.wsf.test.JBossWSTestHelper.BaseDeployment;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
/**
 * Client invoking web service using MTOM
 *
 */
public final class MtomTestCase extends JBossWSTest
{
   public static BaseDeployment<?>[] createDeployments() {
      List<BaseDeployment<?>> list = new LinkedList<BaseDeployment<?>>();
      list.add(new JBossWSTestHelper.WarDeployment("jaxws-samples-mtom.war") { {
         archive
               .addManifest()
               .addClass(org.jboss.test.ws.jaxws.samples.mtom.ServiceIface.class)
               .addClass(org.jboss.test.ws.jaxws.samples.mtom.ServiceImpl.class)
               .addClass(org.jboss.test.ws.jaxws.samples.mtom.jaxws.SayHello.class)
               .addClass(org.jboss.test.ws.jaxws.samples.mtom.jaxws.SayHelloResponse.class)
               .addAsWebInfResource(new File(JBossWSTestHelper.getTestResourcesDir() + "/jaxws/samples/mtom/WEB-INF/wsdl/MtomService.wsdl"), "wsdl/MtomService.wsdl")
               .setWebXML(new File(JBossWSTestHelper.getTestResourcesDir() + "/jaxws/samples/mtom/WEB-INF/web.xml"));
         }
      });
      return list.toArray(new BaseDeployment<?>[list.size()]);
   }

   private final String serviceURL = "http://" + getServerHost() + ":8080/jaxws-samples-mtom/MtomService";

   public static Test suite()
   {
      return new JBossWSCXFTestSetup(MtomTestCase.class, JBossWSTestHelper.writeToFile(createDeployments()));
   }

   public void testMtomWithProxy() throws Exception
   {
      // construct proxy
      QName serviceName = new QName("http://www.jboss.org/jbossws/ws-extensions/mtom", "MtomService");
      URL wsdlURL = new URL(serviceURL + "?wsdl");
      Service service = Service.create(wsdlURL, serviceName);
      ServiceIface proxy = (ServiceIface)service.getPort(ServiceIface.class);
      // invoke method
      assertEquals("Hello World!", proxy.sayHello());
   }

   public void testMtomWithoutProxy() throws Exception
   {
      final String mtomPayload = "--uuid:b7a481a7-274a-42ed-8b84-9bb2280fb2e7\r\n"
                                 + "Content-Type: application/xop+xml; charset=UTF-8; type=\"text/xml\"\r\n"
                                 + "Content-Transfer-Encoding: binary\r\n"
                                 + "Content-ID: <root.message@cxf.apache.org>\r\n\r\n"
                                 + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                                 + "<ns2:sayHello xmlns:ns2=\"http://www.jboss.org/jbossws/ws-extensions/mtom\" "
                                 + "xmlns:ns3=\"http://www.jboss.org/jbossws/ws-extensions/wsaddressing\"/></soap:Body></soap:Envelope>\r\n"
                                 + "--uuid:b7a481a7-274a-42ed-8b84-9bb2280fb2e7--";

      HttpURLConnection conn = (HttpURLConnection)new URL(serviceURL).openConnection();
      conn.setDoOutput(true);
      conn.setRequestMethod("POST"); 
      conn.setRequestProperty("Content-Type", 
                              "multipart/related; type=\"application/xop+xml\"; boundary=\"uuid:b7a481a7-274a-42ed-8b84-9bb2280fb2e7\";" 
                              + " start=\"<root.message@cxf.apache.org>\"; start-info=\"text/xml\"");
      conn.setRequestProperty("Content-Length", Integer.toString(mtomPayload.length()));
      OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream());
      out.write(mtomPayload);
      out.flush();
      out.close();

      assertEquals(200, conn.getResponseCode());
      final InputStream is = conn.getInputStream();
      try {
         String response = IOUtils.readStringFromStream(is);
   
         assertTrue(response.contains("--uuid"));
         assertTrue(response.contains("<return>Hello World!</return>"));
      } finally {
         is.close();
      }
   }

   public void testMtomNotUsed() throws Exception
   {
      final String envelope = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                              + "<ns2:sayHello xmlns:ns2=\"http://www.jboss.org/jbossws/ws-extensions/mtom\" "
                              + "xmlns:ns3=\"http://www.jboss.org/jbossws/ws-extensions/wsaddressing\"/></soap:Body></soap:Envelope>";

      HttpURLConnection conn = (HttpURLConnection)new URL(serviceURL).openConnection();
      conn.setDoOutput(true);
      conn.setRequestMethod("POST"); 
      conn.setRequestProperty("Content-Type", "text/xml");
      conn.setRequestProperty("Content-Length", Integer.toString(envelope.length()));
      OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream());
      out.write(envelope);
      out.flush();
      out.close();

      assertEquals(500, conn.getResponseCode());
      Element responseEl = DOMUtils.parse(conn.getErrorStream());

      NodeList list = responseEl.getElementsByTagName("faultstring");
      String text = list.item(0).getTextContent();
      assertTrue(text.contains("These policy alternatives can not be satisfied: "));
      assertTrue(text.contains("{http://schemas.xmlsoap.org/ws/2004/09/policy/optimizedmimeserialization}"
                    + "OptimizedMimeSerialization"));
   }   
}
