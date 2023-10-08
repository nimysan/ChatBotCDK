package red.plaza.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.StackProps;

public class ChatBotCdkApp {
    public static void main(final String[] args) {
        App app = new App();
        ChatBotCdkStack stack = new ChatBotCdkStack(app, "ChatBotCdkStack", StackProps.builder()
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region(System.getenv("CDK_DEFAULT_REGION"))
                        .build())
                .build());
        app.synth();
    }
}

