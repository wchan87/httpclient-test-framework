package edu.nyu;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;

public class ProxyHttpClientTest {

    private static final String HTTP_SCHEME = "http";
    private static final String LOCALHOST = "localhost";
    private static final int PORT = 12345;
    private static final int PROXY_PORT = 54321;
    private static final String ROOT_CONTEXT = "/";
    private static final String USER = "user";
    private static final String PASS = "pass";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyHttpClientTest.class);

    private HttpProxyServer proxyHttpServer;
    private HttpServer httpServer;

    @After
    public void shutdown() {
        proxyHttpServer.stop();
        httpServer.stop();
    }

    /**
     * Test confirming proxy server handles request and response on behalf of the client
     *
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    public void testProxySimpleHttpGet() throws IOException, URISyntaxException {
        // setup proxy server
        proxyHttpServer = DefaultHttpProxyServer.bootstrap()
                .withPort(PROXY_PORT).plusActivityTracker(new LoggingActivityTracker()).start();
        // setup embedded server
        httpServer = ServerBootstrap.bootstrap()
                .setListenerPort(PORT)
                .registerHandler(ROOT_CONTEXT, (req, resp, context) -> resp.setStatusCode(HttpStatus.SC_OK))
                .create();
        httpServer.start();

        // setup HttpClient
        HttpClient httpClient = HttpClientBuilder.create().setProxy(new HttpHost(LOCALHOST, PROXY_PORT)).build();
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTP_SCHEME)
                .setHost(LOCALHOST).setPort(PORT).setPath(ROOT_CONTEXT).build()).build();
        HttpResponse httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(HttpStatus.SC_OK, httpResponse.getStatusLine().getStatusCode());
    }

    /**
     * Test confirming proxy server rejects request if Proxy-Authorization isn't provided
     *
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    public void testProxyAuthenticationRequired() throws IOException, URISyntaxException {
        // setup proxy server
        proxyHttpServer = DefaultHttpProxyServer.bootstrap()
                .withPort(PROXY_PORT).plusActivityTracker(new LoggingActivityTracker())
                .withProxyAuthenticator(getProxyAuthenticator()).start();
        // setup embedded server
        httpServer = ServerBootstrap.bootstrap()
                .setListenerPort(PORT)
                .registerHandler(ROOT_CONTEXT, (req, resp, context) -> resp.setStatusCode(HttpStatus.SC_OK))
                .create();
        httpServer.start();

        // setup HttpClient
        HttpClient httpClient = HttpClientBuilder.create().setProxy(new HttpHost(LOCALHOST, PROXY_PORT)).build();
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTP_SCHEME)
                .setHost(LOCALHOST).setPort(PORT).setPath(ROOT_CONTEXT).build()).build();
        HttpResponse httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, httpResponse.getStatusLine().getStatusCode());
    }

    /**
     * Test confirming proxy server accepts request with Proxy-Authorization and strips it out on request to target server
     *
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    public void testProxyAuthenticationSuccess() throws IOException, URISyntaxException {
        // setup proxy server
        proxyHttpServer = DefaultHttpProxyServer.bootstrap()
                .withPort(PROXY_PORT).plusActivityTracker(new LoggingActivityTracker())
                .withProxyAuthenticator(getProxyAuthenticator()).start();
        // setup embedded server
        httpServer = ServerBootstrap.bootstrap()
                .setListenerPort(PORT)
                .registerHandler(ROOT_CONTEXT, (req, resp, context) -> {
                    for (Header h : req.getAllHeaders()) {
                        // log the request headers to prove that Proxy-Authorization header isn't leaked
                        LOGGER.info("Request Header >> {} : {}", h.getName(), h.getValue());
                    }
                    Assert.assertEquals(0, req.getHeaders("Proxy-Authentication").length);
                    resp.setStatusCode(HttpStatus.SC_OK);
                })
                .create();
        httpServer.start();

        // setup HttpClient
        // https://stackoverflow.com/questions/6962047/apache-httpclient-4-1-proxy-authentication
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(LOCALHOST, PROXY_PORT),
                new UsernamePasswordCredentials(USER, PASS));
        HttpClient httpClient = HttpClientBuilder.create().setProxy(new HttpHost(LOCALHOST, PROXY_PORT))
                .setDefaultCredentialsProvider(credsProvider)
                .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy()).build();
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTP_SCHEME)
                .setHost(LOCALHOST).setPort(PORT).setPath(ROOT_CONTEXT).build()).build();
        HttpResponse httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(HttpStatus.SC_OK, httpResponse.getStatusLine().getStatusCode());
    }

    private ProxyAuthenticator getProxyAuthenticator() {
        return new ProxyAuthenticator() {
            @Override
            public boolean authenticate(String user, String pass) {
                 return user.equals(USER) && pass.equals(PASS);
            }
            @Override
            public String getRealm() {
                return null;
            }
        };
    }

}
