/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 *  See the NOTICE file(s) distributed with this work for additional
 *  information regarding copyright ownership.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.eclipse.microprofile.jwt.tck.config.jwe;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.microprofile.jwt.tck.TCKConstants.TEST_GROUP_CONFIG;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Properties;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.config.Names;
import org.eclipse.microprofile.jwt.tck.TCKConstants;
import org.eclipse.microprofile.jwt.tck.config.PEMApplication;
import org.eclipse.microprofile.jwt.tck.config.SimpleTokenUtils;
import org.eclipse.microprofile.jwt.tck.util.MpJwtTestVersion;
import org.eclipse.microprofile.jwt.tck.util.TokenUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

/**
 * Validate that mp.jwt.decrypt.key.location property values of type resource path to a PEM
 * work to decrypt the JWT which is encrypted with publicKey4k.pem
 */
public class PrivateKeyAsPEMClasspathTest extends Arquillian {

    /**
     * The base URL for the container under test
     */
    @ArquillianResource
    private URL baseURL;

    /**
     * Create a CDI aware base web application archive that includes an embedded PEM public key
     * that is included as the mp.jwt.verify.publickey property.
     * The root url is /
     * @return the base base web application archive
     * @throws IOException - on resource failure
     */
    @Deployment()
    public static WebArchive createLocationDeployment() throws IOException {
        URL publicKey = PrivateKeyAsPEMClasspathTest.class.getResource("/publicKey4k.pem");
        URL privateKey = PrivateKeyAsPEMClasspathTest.class.getResource("/privateKey4k.pem");
        // Setup the microprofile-config.properties content
        Properties configProps = new Properties();
        // Location points to the PEM bundled in the deployment
        configProps.setProperty(Names.VERIFIER_PUBLIC_KEY_LOCATION, "/publicKey4k.pem");
        configProps.setProperty(Names.DECRYPTOR_KEY_LOCATION, "/privateKey4k.pem");
        configProps.setProperty(Names.ISSUER, TCKConstants.TEST_ISSUER);
        StringWriter configSW = new StringWriter();
        configProps.store(configSW, "PrivateKeyAsPEMClasspathTest microprofile-config.properties");
        StringAsset configAsset = new StringAsset(configSW.toString());

        WebArchive webArchive = ShrinkWrap
            .create(WebArchive.class, "PrivateKeyAsPEMClasspathTest.war")
            .addAsManifestResource(new StringAsset(MpJwtTestVersion.MPJWT_V_1_2.name()), MpJwtTestVersion.MANIFEST_NAME)
            .addAsResource(privateKey, "/privateKey4k.pem")
            .addAsResource(publicKey, "/publicKey4k.pem")
            .addClass(PrivateKeyEndpoint.class)
            .addClass(PEMApplication.class)
            .addClass(SimpleTokenUtils.class)
            .addAsWebInfResource("beans.xml", "beans.xml")
            .addAsManifestResource(configAsset, "microprofile-config.properties");
        return webArchive;
    }

    @RunAsClient
    @Test(groups = TEST_GROUP_CONFIG,
        description = "Validate specifying the mp.jwt.decrypt.key.location is a resource location of a private PEM key")
    public void testKeyAsLocationResource() throws Exception {
        Reporter.log("testKeyAsLocationResource, expect HTTP_OK");

        PrivateKey signingKey = TokenUtils.readPrivateKey("/privateKey4k.pem");
        PublicKey publicKey = TokenUtils.readPublicKey("/publicKey4k.pem");
        String token = TokenUtils.signEncryptClaims(signingKey, null, publicKey, null, "/Token1.json", true);

        String uri = baseURL.toExternalForm() + "pem/endp/verifyKeyLocationAsPEMResource";
        WebTarget echoEndpointTarget = ClientBuilder.newClient()
            .target(uri);
        Response response = echoEndpointTarget.request(APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, "Bearer "+token).get();
        Assert.assertEquals(response.getStatus(), HttpURLConnection.HTTP_OK);
        String replyString = response.readEntity(String.class);
        JsonReader jsonReader = Json.createReader(new StringReader(replyString));
        JsonObject reply = jsonReader.readObject();
        Reporter.log(reply.toString());
        Assert.assertTrue(reply.getBoolean("pass"), reply.getString("msg"));
    }
}
