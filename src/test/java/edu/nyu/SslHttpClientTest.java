package edu.nyu;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.ssl.SSLContextBuilder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.security.validator.ValidatorException;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CertSelector;
import java.util.EnumSet;

public class SslHttpClientTest {

    private static final String HTTPS_SCHEME = "https";
    private static final String LOCALHOST = "localhost";
    private static final int PORT = 12345;
    private static final int CRL_PORT = 54321;
    private static final String KEYSTORE_PASS = "changeit";
    private static final String ROOT_CONTEXT = "/";

    private HttpServer httpServer;
    private static HttpServer crlHttpServer;

    private static final Logger LOGGER = LoggerFactory.getLogger(SslHttpClientTest.class);

    @BeforeClass
    public static void setup() throws IOException {
        crlHttpServer = ServerBootstrap.bootstrap()
                .setListenerPort(CRL_PORT)
                .registerHandler("/ca.crl", getCrlRequestHandler()).create();
        crlHttpServer.start();
    }

    /**
     * Returns the content of the /ca/ca.crl file where we revoked our localhost cert in src/test/resources
     *
     * @return HttpRequestHandler
     */
    private static HttpRequestHandler getCrlRequestHandler() {
        return (req, resp, ctx) -> {
            try {
                LOGGER.info("Request made for /ca/ca.crl file");
                resp.setEntity(new FileEntity(
                        new File(SslHttpClientTest.class.getResource("/ca/ca.crl").toURI())));
            } catch (URISyntaxException e) {
                LOGGER.error("Issue loading /ca/ca.crl file from classpath", e);
                Assert.fail();
            }
        };
    }

    @AfterClass
    public static void teardown() {
        crlHttpServer.stop();
    }

    @After
    public void shutdown() {
        httpServer.stop();
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
     * Test confirming that CRL is checked correctly when enabled
     *
     * @throws UnrecoverableKeyException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws IOException
     * @throws KeyManagementException
     * @throws URISyntaxException
     * @throws InvalidAlgorithmParameterException
     */
    // TODO find a better way to run this with -Djavax.net.debug=all
    @Test
    public void testCrlCheck() throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException,
            KeyStoreException, IOException, KeyManagementException, URISyntaxException,
            InvalidAlgorithmParameterException {
        // setup embedded server
        httpServer = ServerBootstrap.bootstrap()
                .setSslContext(buildKeyStoreSslContext())
                .setListenerPort(PORT)
                .registerHandler(ROOT_CONTEXT, (req, resp, ctx) -> resp.setStatusCode(HttpStatus.SC_OK))
                .create();
        httpServer.start();

        // SSLContext setup from https://stackoverflow.com/questions/38301283/java-ssl-certificate-revocation-checking
        KeyStore ts = KeyStore.getInstance("JKS");
        FileInputStream tfis = new FileInputStream(
                new File(this.getClass().getResource("/keystore.jks").toURI()));
        ts.load(tfis, KEYSTORE_PASS.toCharArray());
        KeyManagerFactory kmf =  KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        // initialize certification path checking for the offered certificates and revocation checks against CLRs
        CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");
        PKIXRevocationChecker rc = (PKIXRevocationChecker)cpb.getRevocationChecker();
        rc.setOptions(EnumSet.of(
                PKIXRevocationChecker.Option.PREFER_CRLS, // prefer CLR over OCSP
                PKIXRevocationChecker.Option.ONLY_END_ENTITY,
                PKIXRevocationChecker.Option.SOFT_FAIL, // handshake should not fail when CRL is not available
                PKIXRevocationChecker.Option.NO_FALLBACK)); // don't fall back to OCSP checking
        PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(ts, new X509CertSelector());
        pkixParams.addCertPathChecker(rc);
        tmf.init(new CertPathTrustManagerParameters(pkixParams));
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);

        // setup HttpClient
        HttpClient httpClient = HttpClientBuilder.create()
                .setSSLContext(ctx)
                .build();
        HttpUriRequest httpUriRequest = RequestBuilder.get(new URIBuilder().setScheme(HTTPS_SCHEME)
                .setHost(LOCALHOST).setPort(PORT).setPath(ROOT_CONTEXT).build()).build();
        try {
            httpClient.execute(httpUriRequest);
            Assert.fail();
        } catch (SSLHandshakeException e) {
            Assert.assertTrue(e.getCause() instanceof ValidatorException);
            Assert.assertTrue(e.getCause().getLocalizedMessage()
                    .startsWith("PKIX path validation failed"));
            Assert.assertTrue(e.getCause().getCause() instanceof CertPathValidatorException);
            Assert.assertTrue(e.getCause().getCause().getLocalizedMessage()
                    .startsWith("Certificate has been revoked,"));
        }
    }

    /**
     * Builds the keystore which is used by the mock server
     *
     * @return SSLContext
     * @throws UnrecoverableKeyException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws IOException
     * @throws KeyManagementException
     */
    private SSLContext buildKeyStoreSslContext() throws UnrecoverableKeyException, CertificateException,
            NoSuchAlgorithmException, KeyStoreException, IOException, KeyManagementException {
        return SSLContextBuilder.create()
                .loadKeyMaterial(this.getClass().getResource("/keystore.jks"),
                        KEYSTORE_PASS.toCharArray(), KEYSTORE_PASS.toCharArray()).build();
    }

    /**
     * Builds the truststore which is used by the client
     *
     * @return SSLContext
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws CertificateException
     * @throws IOException
     * @throws KeyManagementException
     */
    private SSLContext buildTrustStoreSslContext() throws NoSuchAlgorithmException, KeyStoreException,
            CertificateException, IOException, KeyManagementException {
        return SSLContextBuilder.create()
                .loadTrustMaterial(this.getClass().getResource("/keystore.jks"),
                        KEYSTORE_PASS.toCharArray()).build();
    }

}
