package edu.nyu;

import ca.danielyule.mockingjay.MockServer;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpClientTest {

    private static final int TEST_PORT = 12345;

    @Rule
    public MockServer mockServer = new MockServer(TEST_PORT);

    // TODO get this to work or find another mock binary server
    @Test
    public void test() throws IOException {
        mockServer.expected().write("GET / HTTP/1.1\r\n".getBytes(StandardCharsets.UTF_8));
        mockServer.response().write("HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.UTF_8));
        HttpClient httpClient = HttpClientBuilder.create().build();
        System.out.println(httpClient.execute(new HttpGet("http://localhost:" + TEST_PORT + "/")));
    }

}
