package ru.qa.tinkoff.tracking.services.grpc;

import com.google.protobuf.GeneratedMessageV3;
import io.grpc.Metadata;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CapturedResponse<T extends GeneratedMessageV3> {
    private T response;
    private Metadata trailers;
    private Metadata headers;
}
