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
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import java.io.IOException;
import java.net.URISyntaxException;

public class BasicProxyHttpClientTest {

    private static final String HTTP_SCHEME = "http";
    private static final String LOCALHOST = "localhost";
    private static final int PORT = 12345;
    private static final int PROXY_PORT = 54321;

    private HttpProxyServer proxyHttpServer;
    private HttpServer httpServer;

    @After
    public void shutdown() {
        proxyHttpServer.stop();
        httpServer.stop();
    }

    @Test
    public void testProxyHttpGet() throws IOException, URISyntaxException {
        // setup proxy server
        proxyHttpServer = DefaultHttpProxyServer.bootstrap()
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
        httpServer = ServerBootstrap.bootstrap()
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
    }

}
