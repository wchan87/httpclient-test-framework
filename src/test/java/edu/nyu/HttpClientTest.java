package edu.nyu;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

public class HttpClientTest {

    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String LOCALHOST = "localhost";
    private static final int PORT = 12345;
    private static final String KEYSTORE_PASS = "changeit";

    // some other mock server implementations
    // https://github.com/jamesdbloom/mockserver
    // https://github.com/danielyule/mockingjay

    private HttpServer httpServer;

    @After
    public void shutdown() {
        httpServer.stop();
    }

    @Test
    public void testSimpleHttpGet() throws IOException, URISyntaxException {
        // setup embedded server
        String rootContext = "/";
        httpServer = ServerBootstrap.bootstrap()
                .setListenerPort(PORT)
                .registerHandler(rootContext, (req, resp, context) -> resp.setStatusCode(HttpStatus.SC_OK))
                .create();
        httpServer.start();

        // setup HttpClient
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTP_SCHEME)
                .setHost(LOCALHOST).setPort(PORT).setPath(rootContext).build()).build();
        HttpResponse httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
    }

    // https://stackoverflow.com/questions/32618108/example-of-using-ssl-with-org-apache-http-impl-bootstrap-httpserver-from-apache
    @Test
    public void testSimpleHttpsGet() throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException,
            KeyStoreException, IOException, KeyManagementException, URISyntaxException {
        // setup embedded server
        String rootContext = "/";
        httpServer = ServerBootstrap.bootstrap()
                .setSslContext(buildKeyStoreSslContext())
                .setListenerPort(PORT)
                .registerHandler(rootContext, (req, resp, ctx) -> resp.setStatusCode(HttpStatus.SC_OK))
                .create();
        httpServer.start();

        // setup HttpClient
        HttpClient httpClient = HttpClientBuilder.create()
                .setSSLContext(buildTrustStoreSslContext())
                .build();
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTPS_SCHEME)
                .setHost(LOCALHOST).setPort(PORT).setPath(rootContext).build()).build();
        HttpResponse httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
    }

    private SSLContext buildKeyStoreSslContext() throws UnrecoverableKeyException, CertificateException,
            NoSuchAlgorithmException, KeyStoreException, IOException, KeyManagementException {
        return SSLContextBuilder.create()
                .loadKeyMaterial(this.getClass().getResource("/keystore.jks"),
                        KEYSTORE_PASS.toCharArray(), KEYSTORE_PASS.toCharArray()).build();
    }

    private SSLContext buildTrustStoreSslContext() throws NoSuchAlgorithmException, KeyStoreException,
            CertificateException, IOException, KeyManagementException {
        return SSLContextBuilder.create()
                .loadTrustMaterial(this.getClass().getResource("/keystore.jks"),
                        KEYSTORE_PASS.toCharArray()).build();
    }

}
