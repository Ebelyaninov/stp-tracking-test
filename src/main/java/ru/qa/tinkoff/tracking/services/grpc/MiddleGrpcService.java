package ru.qa.tinkoff.tracking.services.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import ru.tinkoff.invest.miof.Client;
import ru.tinkoff.invest.miof.ClientServiceGrpc;

import ru.tinkoff.invest.miof.Client.GetClientPositionsReq;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class MiddleGrpcService implements DisposableBean {

    private final ManagedChannel managedChannel;
    private final ClientServiceGrpc.ClientServiceBlockingStub stub;
    private final AtomicReference<Metadata> trailersCapture = new AtomicReference<>();
    private final AtomicReference<Metadata> headersCapture = new AtomicReference<>();

    public MiddleGrpcService(ManagedChannel managedChannel) {
        this.managedChannel = managedChannel;
        this.stub = ClientServiceGrpc.newBlockingStub(managedChannel)
            .withInterceptors(MetadataUtils.newCaptureMetadataInterceptor(headersCapture, trailersCapture));
    }

    @Step("Получение всех позиций клиента по средствам gRPC")
    public CapturedResponse<Client.GetClientPositionsResp> getClientPositions(GetClientPositionsReq request) {
        log.info("Получение всех позиций клиента gRPC call getClientPositions");
        Client.GetClientPositionsResp body = stub.getClientPositions(request);
        return new CapturedResponse<Client.GetClientPositionsResp>()
            .setResponse(body)
            .setHeaders(headersCapture.get())
            .setTrailers(trailersCapture.get());
    }
    @Override
    public void destroy() {
        log.info("Closing gRPC connection");
        managedChannel.shutdown();
    }

}
