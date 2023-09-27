package red.plaza.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.StackProps;

public class ChatBotCdkApp {
    public static void main(final String[] args) {
        App app = new App();

//        SageMakerEndpointStack ss = new SageMakerEndpointStack(app, "LLMStackInSageMaker", StackProps.builder()
//                .env(Environment.builder()
//                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
//                        .region(System.getenv("CDK_DEFAULT_REGION"))
//                        .build())
//                .build());

//        new VectorStoreStack((app, "VectorStoreStack", NestedStackProps.builder().build()));
        ChatBotCdkStack stack = new ChatBotCdkStack(app, "ChatBotCdkStack", StackProps.builder()
                // If you don't specify 'env', this stack will be environment-agnostic.
                // Account/Region-dependent features and context lookups will not work,
                // but a single synthesized template can be deployed anywhere.

                // Uncomment the next block to specialize this stack for the AWS Account
                // and Region that are implied by the current CLI configuration.
                /*
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region(System.getenv("CDK_DEFAULT_REGION"))
                        .build())
                */
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region(System.getenv("CDK_DEFAULT_REGION"))
                        .build())
                // Uncomment the next block if you know exactly what Account and Region you
                // want to deploy the stack to.
                /*
                .env(Environment.builder()
                        .account("123456789012")
                        .region("us-east-1")
                        .build())
                */
//                .env(Environment.builder()
//                        .account("390468416359")
//                        .region("us-east-1")
//                        .build())
                // For more information, see https://docs.aws.amazon.com/cdk/latest/guide/environments.html
                .build());
        app.synth();
    }
}

