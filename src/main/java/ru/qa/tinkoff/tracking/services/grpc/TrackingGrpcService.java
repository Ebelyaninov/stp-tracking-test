package ru.qa.tinkoff.tracking.services.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import ru.tinkoff.trading.tracking.internal.Empty;
import ru.tinkoff.trading.tracking.internal.OrderServiceGrpc;
import ru.tinkoff.trading.tracking.internal.ValidateOrderRequest;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class TrackingGrpcService implements DisposableBean {

    private final ManagedChannel managedChannel;
    private final OrderServiceGrpc.OrderServiceBlockingStub stub;
    private final AtomicReference<Metadata> trailersCapture = new AtomicReference<>();
    private final AtomicReference<Metadata> headersCapture = new AtomicReference<>();

    public TrackingGrpcService(ManagedChannel managedChannel) {
        this.managedChannel = managedChannel;
        this.stub = OrderServiceGrpc.newBlockingStub(managedChannel)
            .withInterceptors(MetadataUtils.newCaptureMetadataInterceptor(headersCapture, trailersCapture));
    }

    public CapturedResponse<Empty> validateOrder(ValidateOrderRequest request) {
        log.info("gRPC call ValidateOrder");
        Empty body = stub.validateOrder(request);
        return new CapturedResponse<Empty>()
            .setResponse(body)
            .setHeaders(headersCapture.get())
            .setTrailers(trailersCapture.get());
    }

    @Override
    public void destroy() throws Exception {
        log.info("Closing gRPC connection");
        managedChannel.shutdown();
    }
}
