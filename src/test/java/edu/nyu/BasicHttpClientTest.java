package edu.nyu;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.ssl.SSLContextBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

public class BasicHttpClientTest {

    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String LOCALHOST = "localhost";
    private static final int PORT = 12345;
    private static final int PROXY_PORT = 54321;
    private static final String KEYSTORE_PASS = "changeit";

    // some other mock server implementations
    // https://github.com/jamesdbloom/mockserver
    // https://github.com/danielyule/mockingjay

    @Test
    public void testSimpleHttpGet() throws IOException, URISyntaxException {
        // setup embedded server
        String rootContext = "/";
        HttpServer httpServer = ServerBootstrap.bootstrap()
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

        // clean up
        httpServer.stop();
    }

    // https://stackoverflow.com/questions/32618108/example-of-using-ssl-with-org-apache-http-impl-bootstrap-httpserver-from-apache
    @Test
    public void testSimpleHttpsGet() throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, KeyManagementException, URISyntaxException {
        // setup embedded server
        String rootContext = "/";
        HttpServer httpServer = ServerBootstrap.bootstrap()
                .setSslContext(SSLContextBuilder.create()
                        .loadKeyMaterial(this.getClass().getResource("/keystore.jks"),
                                KEYSTORE_PASS.toCharArray(), KEYSTORE_PASS.toCharArray()).build())
                .setListenerPort(PORT)
                .registerHandler(rootContext, (req, resp, ctx) -> resp.setStatusCode(HttpStatus.SC_OK))
                .create();
        httpServer.start();

        // setup HttpClient
        HttpClient httpClient = HttpClientBuilder.create().setSSLContext(SSLContextBuilder.create()
                .loadTrustMaterial(this.getClass().getResource("/keystore.jks"),
                        KEYSTORE_PASS.toCharArray()).build()).build();
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTPS_SCHEME)
                .setHost(LOCALHOST).setPort(PORT).setPath(rootContext).build()).build();
        HttpResponse httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);

        // clean up
        httpServer.stop();
    }

    @Test
    public void testProxyHttpGet() throws IOException, URISyntaxException {
        // setup proxy server
        HttpProxyServer proxyHttpServer = DefaultHttpProxyServer.bootstrap()
                .withPort(PROXY_PORT).withProxyAuthenticator(new ProxyAuthenticator() {
                    @Override
                    public boolean authenticate(String s, String s1) {
                        return false;
                    }
                    @Override
                    public String getRealm() {
                        return null;
                    }
                }).start();
        // setup embedded server
        String rootContext = "/";
        HttpServer httpServer = ServerBootstrap.bootstrap()
                .setListenerPort(PORT)
                .registerHandler(rootContext, (req, resp, context) -> resp.setStatusCode(HttpStatus.SC_OK))
                .create();
        httpServer.start();

        // setup HttpClient
        HttpClient httpClient = HttpClientBuilder.create().setRoutePlanner(
                new DefaultProxyRoutePlanner(new HttpHost(LOCALHOST, PROXY_PORT))).build();
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTP_SCHEME)
                .setHost(LOCALHOST).setPort(PORT).setPath(rootContext).build()).build();
        HttpResponse httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED);

        // clean up
        proxyHttpServer.stop();
        httpServer.stop();
    }

}
