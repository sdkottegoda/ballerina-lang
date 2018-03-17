/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.net.grpc.nativeimpl.servicestub;

import io.grpc.MethodDescriptor;
import org.ballerinalang.bre.Context;
import org.ballerinalang.connector.api.BLangConnectorSPIUtil;
import org.ballerinalang.connector.api.Service;
import org.ballerinalang.connector.api.Value;
import org.ballerinalang.connector.impl.ValueImpl;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.model.values.BStruct;
import org.ballerinalang.model.values.BTypeValue;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.Receiver;
import org.ballerinalang.natives.annotations.ReturnType;
import org.ballerinalang.net.grpc.Message;
import org.ballerinalang.net.grpc.MessageRegistry;
import org.ballerinalang.net.grpc.MessageUtils;
import org.ballerinalang.net.grpc.exception.GrpcClientException;
import org.ballerinalang.net.grpc.stubs.DefaultStreamObserver;
import org.ballerinalang.net.grpc.stubs.GrpcNonBlockingStub;

import static org.ballerinalang.net.grpc.EndpointConstants.SERVICE_STUB;

/**
 * {@code NonBlockingExecute} is the NonBlockingExecute action implementation of the gRPC Connector.
 */
@BallerinaFunction(
        packageName = "ballerina.net.grpc",
        functionName = "nonBlockingExecute",
        receiver = @Receiver(type = TypeKind.STRUCT, structType = "ServiceStub",
                structPackage = "ballerina.net.grpc"),
        args = {
                @Argument(name = "methodID", type = TypeKind.STRING),
                @Argument(name = "payload", type = TypeKind.ANY),
                @Argument(name = "listenerService", type = TypeKind.TYPE)
        },
        returnType = {
                @ReturnType(type = TypeKind.ANY),
                @ReturnType(type = TypeKind.STRUCT, structType = "ConnectorError",
                        structPackage = "ballerina.net.grpc"),
        },
        isPublic = true
)
public class NonBlockingExecute extends AbstractExecute {
    @Override
    public void execute(Context context) {
        BStruct serviceStub = (BStruct) context.getRefArgument(0);
        if (serviceStub == null) {
            notifyErrorReply(context, "Error while getting connector. gRPC Client connector is " +
                    "not initialized properly");
            return;
        }

        Object connectionStub = serviceStub.getNativeData(SERVICE_STUB);
        if (connectionStub == null) {
            notifyErrorReply(context, "Error while getting connection stub. gRPC Client connector " +
                    "is not initialized properly");
            return;
        }
        String methodName = context.getStringArgument(0);
        if (methodName == null) {
            notifyErrorReply(context, "Error while processing the request. RPC endpoint doesn't " +
                    "set properly");
            return;
        }
        com.google.protobuf.Descriptors.MethodDescriptor methodDescriptor = MessageRegistry.getInstance()
                .getMethodDescriptor(methodName);
        if (methodDescriptor == null) {
            notifyErrorReply(context, "No registered method descriptor for '" + methodName + "'");
            return;
        }
        if (connectionStub instanceof GrpcNonBlockingStub) {
            BValue payloadBValue = context.getRefArgument(1);
            Message requestMsg = MessageUtils.generateProtoMessage(payloadBValue, methodDescriptor.getInputType());
            GrpcNonBlockingStub grpcNonBlockingStub = (GrpcNonBlockingStub) connectionStub;
            BTypeValue serviceType = (BTypeValue) context.getRefArgument(2);
            Service callbackService = BLangConnectorSPIUtil.getServiceFromType(context.getProgramFile(), getTypeField
                    (serviceType));
            try {
                MethodDescriptor.MethodType methodType = getMethodType(methodDescriptor);
                if (methodType.equals(MethodDescriptor.MethodType.UNARY)) {
                    grpcNonBlockingStub.executeUnary(requestMsg, new DefaultStreamObserver(context, callbackService),
                            methodName);
                } else if (methodType.equals(MethodDescriptor.MethodType.SERVER_STREAMING)) {
                    grpcNonBlockingStub.executeServerStreaming(requestMsg, new DefaultStreamObserver(context,
                            callbackService), methodName);
                } else {
                    notifyErrorReply(context, "Error while executing the client call. Method type " +
                            methodType.name() + " not supported");
                    return;
                }
                context.setReturnValues();
                return;
            } catch (RuntimeException | GrpcClientException e) {
                notifyErrorReply(context, "gRPC Client Connector Error :" + e.getMessage());
                return;
            }
        }
        notifyErrorReply(context, "Error while processing the request message. Connection Sub " +
                "type not supported");
    }

    private Value getTypeField(BTypeValue refField) {
        if (refField == null) {
            return null;
        }
        return ValueImpl.createValue(refField);
    }
}
