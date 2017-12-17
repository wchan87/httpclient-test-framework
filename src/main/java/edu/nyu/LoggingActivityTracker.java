package edu.nyu;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.littleshoot.proxy.ActivityTracker;
import org.littleshoot.proxy.FlowContext;
import org.littleshoot.proxy.FullFlowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;

public class LoggingActivityTracker implements ActivityTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingActivityTracker.class);

    @Override
    public void clientConnected(InetSocketAddress inetSocketAddress) {
        LOGGER.info("clientConnected: {}", inetSocketAddress);
    }

    @Override
    public void clientSSLHandshakeSucceeded(InetSocketAddress inetSocketAddress, SSLSession sslSession) {
        LOGGER.info("clientSSLHandshakeSucceeded: {}", inetSocketAddress);
    }

    @Override
    public void clientDisconnected(InetSocketAddress inetSocketAddress, SSLSession sslSession) {
        LOGGER.info("clientDisconnected: {}", inetSocketAddress);
    }

    @Override
    public void bytesReceivedFromClient(FlowContext flowContext, int i) {
        LOGGER.info("bytesReceivedFromClient: {}", flowContext.getClientAddress());
    }

    @Override
    public void requestReceivedFromClient(FlowContext flowContext, HttpRequest httpRequest) {
        LOGGER.info("requestReceivedFromClient: {} {}", flowContext.getClientAddress(), httpRequest);
    }

    @Override
    public void bytesSentToServer(FullFlowContext fullFlowContext, int i) {
        LOGGER.info("bytesSentToServer: {}", fullFlowContext.getServerHostAndPort());
    }

    @Override
    public void requestSentToServer(FullFlowContext fullFlowContext, HttpRequest httpRequest) {
        LOGGER.info("requestSentToServer: {} {}", fullFlowContext.getServerHostAndPort(), httpRequest);
    }

    @Override
    public void bytesReceivedFromServer(FullFlowContext fullFlowContext, int i) {
        LOGGER.info("bytesReceivedFromServer: {}", fullFlowContext.getServerHostAndPort());
    }

    @Override
    public void responseReceivedFromServer(FullFlowContext fullFlowContext, HttpResponse httpResponse) {
        LOGGER.info("responseReceivedFromServer: {} {}", fullFlowContext.getServerHostAndPort(), httpResponse);
    }

    @Override
    public void bytesSentToClient(FlowContext flowContext, int i) {
        LOGGER.info("bytesSentToClient: {}", flowContext.getClientAddress());
    }

    @Override
    public void responseSentToClient(FlowContext flowContext, HttpResponse httpResponse) {
        LOGGER.info("responseSentToClient: {} {}", flowContext.getClientAddress(), httpResponse);
    }

}
