package edu.nyu;

import com.google.common.collect.ImmutableList;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.util.PublicSuffixMatcher;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
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
    private static final String GOOD_HOST = "good.localhost.com";
    private static final String EVIL_HOST = "evil.localhost.com";
    private static final String ROOT_CONTEXT = "/";

    // some other mock server implementations
    // https://github.com/jamesdbloom/mockserver
    // https://github.com/danielyule/mockingjay

    private HttpServer httpServer;

    @After
    public void shutdown() {
        httpServer.stop();
    }

    /**
     * Test confirming simple HTTP GET goes through
     *
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    public void testSimpleHttpGet() throws IOException, URISyntaxException {
        // setup embedded server
        httpServer = ServerBootstrap.bootstrap()
                .setListenerPort(PORT)
                .registerHandler(ROOT_CONTEXT, (req, resp, context) -> resp.setStatusCode(HttpStatus.SC_OK))
                .create();
        httpServer.start();

        // setup HttpClient
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTP_SCHEME)
                .setHost(LOCALHOST).setPort(PORT).setPath(ROOT_CONTEXT).build()).build();
        HttpResponse httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
    }

    /**
     * Test confirming simple HTTPS GET goes through
     *
     * @throws UnrecoverableKeyException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws IOException
     * @throws KeyManagementException
     * @throws URISyntaxException
     */
    // https://stackoverflow.com/questions/32618108/example-of-using-ssl-with-org-apache-http-impl-bootstrap-httpserver-from-apache
    @Test
    public void testSimpleHttpsGet() throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException,
            KeyStoreException, IOException, KeyManagementException, URISyntaxException {
        // setup embedded server
        httpServer = ServerBootstrap.bootstrap()
                .setSslContext(buildKeyStoreSslContext())
                .setListenerPort(PORT)
                .registerHandler(ROOT_CONTEXT, (req, resp, ctx) -> resp.setStatusCode(HttpStatus.SC_OK))
                .create();
        httpServer.start();

        // setup HttpClient
        HttpClient httpClient = HttpClientBuilder.create()
                .setSSLContext(buildTrustStoreSslContext())
                .build();
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTPS_SCHEME)
                .setHost(LOCALHOST).setPort(PORT).setPath(ROOT_CONTEXT).build()).build();
        HttpResponse httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
    }

    /**
     * Test confirming supercookies are set if not specified on the public suffix list
     *
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    public void testPublicSuffixListFailure() throws IOException, URISyntaxException {
        // setup embedded server
        httpServer = ServerBootstrap.bootstrap()
                .setListenerPort(PORT)
                .registerHandler(ROOT_CONTEXT, buildRequestHandlerForEvilCookie())
                .create();
        httpServer.start();

        // setup HttpClient
        HttpClient httpClient = HttpClientBuilder.create().setDnsResolver(buildDnsResolver()).build();
        // request to evil.localhost.com
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTP_SCHEME)
                .setHost(EVIL_HOST).setPort(PORT).setPath(ROOT_CONTEXT).build()).build();
        HttpResponse httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
        // request to good.localhost.com
        httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTP_SCHEME)
                .setHost(GOOD_HOST).setPort(PORT).setPath(ROOT_CONTEXT).build()).build();
        // note that the associated HTTP WIRE entry indicates that the EvilCookie is sent to the good.localhost.com
        httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
        Assert.assertEquals("DID SOMETHING EVIL", EntityUtils.toString(httpResponse.getEntity()));
    }

    /**
     * Test confirming supercookies are not set if specified on the public suffix list
     *
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    public void testPublicSuffixListSuccess() throws IOException, URISyntaxException {
        // setup embedded server
        httpServer = ServerBootstrap.bootstrap()
                .setListenerPort(PORT)
                .registerHandler(ROOT_CONTEXT, buildRequestHandlerForEvilCookie())
                .create();
        httpServer.start();

        // setup HttpClient
        HttpClient httpClient = HttpClientBuilder.create().setDnsResolver(buildDnsResolver())
                .setPublicSuffixMatcher(new PublicSuffixMatcher(ImmutableList.of("localhost.com"), null))
                .build();
        // request to evil.localhost.com
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTP_SCHEME)
                .setHost(EVIL_HOST).setPort(PORT).setPath(ROOT_CONTEXT).build()).build();
        HttpResponse httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
        // request to good.localhost.com
        httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTP_SCHEME)
                .setHost(GOOD_HOST).setPort(PORT).setPath(ROOT_CONTEXT).build()).build();
        // note that the associated HTTP WIRE entry indicates that the EvilCookie is sent to the good.localhost.com
        httpResponse = httpClient.execute(httpUriRequest);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
        Assert.assertNotEquals("DID SOMETHING EVIL", EntityUtils.toString(httpResponse.getEntity()));
    }

    private HttpRequestHandler buildRequestHandlerForEvilCookie() {
        return (req, resp, ctx) -> {
            if (req.getFirstHeader("Host").getValue().startsWith(EVIL_HOST)) {
                resp.setHeader("Set-Cookie", "EvilCookie=AuthorizeAll; Domain=localhost.com; Path=/; Max-Age=1000");
            }
            // TODO find a more elegant way to parse and look for EvilCookie
            if (req.getHeaders("Cookie").length > 0 &&
                    req.getFirstHeader("Cookie").getValue().contains("EvilCookie=AuthorizeAll")) {
                resp.setEntity(new StringEntity("DID SOMETHING EVIL"));
            }
            resp.setStatusCode(HttpStatus.SC_OK);
        };
    }

    private DnsResolver buildDnsResolver() {
        return host -> {
                if (host.equals(EVIL_HOST) || host.equals(GOOD_HOST)) {
                    return new InetAddress[] { InetAddress.getByAddress(new byte[] {127, 0, 0, 1}) };
                } else {
                    return null;
                }
        };
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
