package red.plaza.cdk;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.sagemaker.*;

import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class SageMakerEndpointStack extends Stack {
    public SageMakerEndpointStack(@NotNull Construct scope, @NotNull String id, @Nullable StackProps props) {
        super(scope, id, props);

        CfnModel llmModel = new CfnModel(this, "llmModel", CfnModelProps.builder()
                .executionRoleArn("arn:aws:iam::390468416359:role/accelerate_sagemaker_execution_role") //need to make
                .modelName("ChatGLM-6B")
                .primaryContainer(CfnModel.ContainerDefinitionProperty.builder()
                        .mode("SingleModel")
                        .environment(Map.of("HF_MODEL_ID", "THUDM/chatglm-6b-int8", "SAGEMAKER_CONTAINER_LOG_LEVEL", "20", "SAGEMAKER_REGION", "us-east-1"))
                        .image("763104351884.dkr.ecr.us-east-1.amazonaws.com/huggingface-pytorch-inference:1.13.1-transformers4.26.0-gpu-py39-cu111-ubuntu20.04") //region
                        .build())
                .build());


        CfnEndpointConfig
                endpointConfig = new CfnEndpointConfig(this, "ChatGLM-EndpointConfig", CfnEndpointConfigProps.builder()
                .endpointConfigName("ChatGLM-EndpointConfig")
                .productionVariants(List.of(
                        CfnEndpointConfig.ProductionVariantProperty.builder()
                                .initialInstanceCount(1)
                                .instanceType("ml.g4dn.xlarge")
                                .modelName(llmModel.getModelName())
                                .variantName("AllTraffic")
                                .initialVariantWeight(1)
                                .build()
                ))
                .build());
//
        // 创建Endpoint
        CfnEndpointProps endpointProps = CfnEndpointProps.builder()
                .endpointName("llm-AsyncEndpoint")
                .endpointConfigName(endpointConfig.getEndpointConfigName())
                // 添加其他配置参数
                .build();
        CfnEndpoint endpoint = new CfnEndpoint(this, "Endpoint", endpointProps);
//        endpoint.addDependency(endpointConfig);


        CfnOutput.Builder.create(this, "sagemakerEndpointName").value(endpoint.getAttrEndpointName()).build();
    }

    /**
     * 获取Image的路径
     *
     * @param region
     * @param gpu
     * @param pytorchVersion
     * @param transformerVersion
     * @return
     */
    String getImageUri(String region, boolean gpu, String pytorchVersion, String transformerVersion) {
        String repository = "{region_dict[region]}.dkr.ecr.{region}.amazonaws.com/huggingface-pytorch-inference";
        String tag = "{pytorch_version}-transformers{transformmers_version}-{'gpu-py36-cu111' if use_gpu == True else 'cpu-py36'}-ubuntu18.04";
        return "{repository}:{tag}";
    }

    /**
     * 根据instanceType的命名规则来判断是否是GPU实例
     *
     * @param instanceType
     * @return
     */
    boolean is_gpu_instance(String instanceType) {
        return true;
    }


}
