package red.plaza.cdk;

import org.apache.commons.io.IOUtils;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.cognito.AuthFlow;
import software.amazon.awscdk.services.cognito.CfnUserPoolUser;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.targets.InstanceTarget;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.rds.CfnDBCluster;
import software.amazon.awscdk.services.rds.CfnDBClusterProps;
import software.amazon.awscdk.services.rds.CfnDBInstance;
import software.amazon.awscdk.services.rds.CfnDBInstanceProps;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;

import software.constructs.Construct;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * cdk synth --debug -vvv --parameters ec2KeyPair=us-east-1 --parameters vpcId="vpc-08cd91d07ef1cfbfa"
 */
public class ChatBotCdkStack extends Stack {

    private final static int DB_PORT = 5432;
    private final static int CHAT_BOT_PORT = 7860;
    private final static int CHAT_BOT_MANAGER_PORT = 7865;
    private final static int PGADMIN_PORT = 62315;

    public ChatBotCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public ChatBotCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // 定义实例类型参数
        CfnParameter instanceTypeParameter = CfnParameter.Builder.create(this, "InstanceType")
                .type("String")
                .description("EC2 instance type (Graviton will be cost effective)")
                .defaultValue("t4g.medium").build();
        // The code that defines your stack goes here
        // Parameters
        CfnParameter ec2KeyPairName = CfnParameter.Builder.create(this, "ec2keypair").type("String").description("EC2 Key Pair name").build();


        CfnParameter pgDatabaseName = CfnParameter.Builder.create(this, "databasename").type("String").defaultValue("knowledge").description("Database name").build();
        CfnParameter databaseMasterPassword = CfnParameter.Builder.create(this, "databasepassword").type("String").defaultValue(generateRandomPassword(8)).description("Database master password").build();


        CfnParameter pgAdmin4UserName = CfnParameter.Builder.create(this, "pgAdmin4UserName").type("String").defaultValue("pgadmin4").description("pgAdmin4 User name").build();
        CfnParameter pgAdmin4Password = CfnParameter.Builder.create(this, "pgAdmin4Password").type("String").defaultValue(generateRandomPassword(8)).description("pgAdmin4 Password").build();


        CfnParameter openaiKeyParam = CfnParameter.Builder.create(this, "openaikey").type("String").description("OpenAI key, can be changed after initialized").build();
        //Resources
        IVpc vpc = Vpc.fromLookup(this, "VPC", VpcLookupOptions.builder().isDefault(true).build());

        //EC2的安全组
        SecurityGroup ec2sg = SecurityGroup.Builder.create(this, "ChatBotServerSecurityGroup").vpc(vpc).description("Allow ssh access to ec2 instances").allowAllOutbound(true).build();
        ec2sg.addIngressRule(Peer.anyIpv4(), Port.tcp(22), "allow ssh access from the world");
        ec2sg.addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(DB_PORT), "Connect to pg");
        ec2sg.addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(PGADMIN_PORT), "ALB to pg admin webui");
        ec2sg.addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(CHAT_BOT_PORT), "ALB to gradio webui");
        ec2sg.addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(CHAT_BOT_MANAGER_PORT), "ALB to gradio manager webui");


        //数据库的安全组
        SecurityGroup rdsSg = SecurityGroup.Builder.create(this, "ChatBotRDSSecurityGroup").vpc(vpc).description("Allow EC2 to connect pg").allowAllOutbound(true).build();
        rdsSg.addIngressRule(Peer.securityGroupId(ec2sg.getSecurityGroupId()), Port.tcp(DB_PORT), "Allow EC2 to access database");

        //ALB Security Group
        SecurityGroup albSg = SecurityGroup.Builder.create(this, "ChatBotALBSecurityGroup")
                .securityGroupName("ChatBotALBSecurityGroup")
                .vpc(vpc).description("Application LoadBalancer for ChatBot Application").allowAllOutbound(true).build();
        //rule for target group
        albSg.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "80 for webui");
        albSg.addIngressRule(Peer.anyIpv4(), Port.tcp(443), "443 for pgadmin");

        /**
         * 1. chat ui
         * 2. chat admin ui
         * 3. pgadmin
         */
        albSg.addIngressRule(Peer.securityGroupId(ec2sg.getSecurityGroupId()), Port.tcp(7860), "access gradio webui");
        albSg.addIngressRule(Peer.securityGroupId(ec2sg.getSecurityGroupId()), Port.tcp(62315), "access pgadmin ui");

        CfnDBCluster dbCluster = serverlessV2Cluster(vpc, rdsSg, databaseMasterPassword.getValueAsString(), pgDatabaseName.getValueAsString());
        dbCluster.applyRemovalPolicy(RemovalPolicy.RETAIN);
//

        //create sagemaker role
        Map<String, PolicyDocument> inlinePolicies = new HashMap<>();
        final Object jsonPolicy = new ResourceAsJsonReader().readResourceAsJsonObjects("policy.json");
        inlinePolicies.put("SageMakerAccessPolicy", PolicyDocument.fromJson(jsonPolicy));

        Role sagemakerRole = Role.Builder.create(this, "SagemakerRole")
                .assumedBy(new ServicePrincipal("sagemaker.amazonaws.com"))
                .inlinePolicies(inlinePolicies)
                .roleName("ChatBotCdkStack-SagemakerRole")
                .build();
        sagemakerRole.getRoleArn();

        //创建ec2 role
        Map<String, PolicyDocument> ec2Policies = new HashMap<>();

        final Object bedrockJsonPolicy = new ResourceAsJsonReader().readResourceAsJsonObjects("bedrock-policy.json");
        ec2Policies.put("BedrockAccessPolicy", PolicyDocument.fromJson(bedrockJsonPolicy));

        PolicyStatement statement = PolicyStatement.Builder.create()
                .actions(List.of("iam:GetRole", "iam:PassRole"))
                .effect(Effect.ALLOW)
                .resources(List.of(sagemakerRole.getRoleArn()))
                .build();
        PolicyDocument doc = PolicyDocument.Builder.create()
                .statements(List.of(statement))
                .build();

        ec2Policies.put("passrole", doc);
        Role ec2Role = Role.Builder.create(this, "EC2Role")
                .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                .inlinePolicies(ec2Policies)
                .roleName("ChatBotCdkStack-EC2Role")
                .build();

        //创建EC2
        String userData = null;
        try {
            userData = IOUtils.readLines(new FileInputStream("./userdata.sh")).stream().collect(Collectors.joining("\n"));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        String finalUserData = Fn.sub(userData,
                Map.of("pgAdmin4UserName", pgAdmin4UserName.getValueAsString(),
                        "pgAdmin4Password", pgAdmin4Password.getValueAsString(),
                        //pg_host
                        "pg_host", dbCluster.getAttrEndpointAddress(),
                        "pg_port", String.valueOf(dbCluster.getAttrEndpointPort()),
                        "pg_database", pgDatabaseName.getValueAsString(),
                        "pg_username", "postgres",
                        "pg_password", databaseMasterPassword.getValueAsString(),
                        //openai_key
                        "openai_key", openaiKeyParam.getValueAsString()
                ));


        Instance ec2Instance = Instance.Builder.create(this, "ChatBotWebServerV2-t4g-medium-v1")
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build()) //放在公有子网 需要访问openapi或者其他外部网页数据
                .machineImage(MachineImage.latestAmazonLinux2023(AmazonLinux2023ImageSsmParameterProps.builder()
                        .cpuType(AmazonLinuxCpuType.ARM_64).build()))
                .securityGroup(ec2sg)
                .keyName(ec2KeyPairName.getValueAsString())
                .userData(UserData.custom(finalUserData))
                .role(ec2Role)
                .instanceType(new InstanceType("t4g.medium"))
                .build();

        ec2Instance.getNode().addDependency(dbCluster);


        ApplicationLoadBalancer applicationLoadBalancer = buildALB(vpc, albSg, ec2Instance);

        buildUserPool();

//        @NotNull LoadBalancer lb = LoadBalancer.application(albTg);
//        LoadBalancer.application(pgAlbTg);

        CfnOutput.Builder.create(this, "database-endpoint").value(dbCluster.getAttrEndpointAddress()).build();

        CfnOutput.Builder.create(this, "database").value(pgDatabaseName.getValueAsString()).build();
        CfnOutput.Builder.create(this, "username").value("postgres").build();
        CfnOutput.Builder.create(this, "database password").value(databaseMasterPassword.getValueAsString()).build();


//        CfnOutput.Builder.create(this, "pgAdmin4-url").value("http://" + ec2Instance.getInstancePublicIp() + ":" + PGADMIN_PORT).build();

        CfnOutput.Builder.create(this, "pg4Admin username and password").value(pgAdmin4UserName + " --- " + pgAdmin4Password).build();

        CfnOutput.Builder.create(this, "SageMaker Role ARN").value(sagemakerRole.getRoleArn()).build();
        CfnOutput.Builder.create(this, "ALB endpoint").value(applicationLoadBalancer.getLoadBalancerDnsName()).build();


//        CfnOutput.Builder.create(this, "chatbot-url").value("http://" + ec2Instance.getInstancePublicIp() + ":" + CHAT_BOT_PORT).description("ChatBot endpoint").build();
    }

    private String generateRandomPassword(int len) {
        // ASCII range – alphanumeric (0-9, a-z, A-Z)
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXY*@#$%Zabcdefghijklmnopqrstuvwxyz0123456789";

        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        // each iteration of the loop randomly chooses a character from the given
        // ASCII range and appends it to the `StringBuilder` instance

        for (int i = 0; i < len; i++) {
            int randomIndex = random.nextInt(chars.length());
            sb.append(chars.charAt(randomIndex));
        }

        return sb.toString();
    }

    private void buildUserPool() {
        UserPool userPool = UserPool.Builder.create(this, "UserPool")
                .userPoolName("chat-bot-user-pool")

                .build();

        UserPoolClient userPoolClient = UserPoolClient.Builder
                .create(this, "UserPoolClient")
                .userPoolClientName("chatbot-userpool-client")
                .authFlows(AuthFlow.builder().userPassword(true).userSrp(true).adminUserPassword(true).build())
                .userPool(userPool)
                .generateSecret(false)
                .build();


        CfnUserPoolUser user = CfnUserPoolUser.Builder.create(this, "defaultUser")
                .userPoolId(userPool.getUserPoolId())
                .username("chatbotAdmin")
//                .te
                .build();

    }

    private ApplicationLoadBalancer buildALB
            (IVpc vpc, SecurityGroup albSecurityGroup, Instance chatBotWebServer) {
        ApplicationLoadBalancer alb = ApplicationLoadBalancer.Builder.create(this, "ALB")
                .vpc(vpc)
                .loadBalancerName("CDKManagedALB")
                .securityGroup(albSecurityGroup)
                .internetFacing(true)
                .build();

        //listener for gradio web
        ApplicationListener albListener = alb.addListener("alb-listener", BaseApplicationListenerProps.builder()
                .protocol(ApplicationProtocol.HTTP)
                .build());
        ApplicationTargetGroup webuiTargetGroup = albListener.addTargets("alb-tg", AddApplicationTargetsProps.builder()
                .protocol(ApplicationProtocol.HTTP)
                .healthCheck(HealthCheck.builder()
                        .healthyHttpCodes("302")
                        .build())
                .targetGroupName("WebUITargetGroup")
                .port(CHAT_BOT_PORT)
                .targets(Collections.singletonList(new InstanceTarget(chatBotWebServer)))
                .deregistrationDelay(Duration.seconds(10))
                .build());
        ApplicationTargetGroup managerTargetGroup = albListener.addTargets("alb-tg", AddApplicationTargetsProps.builder()
                .protocol(ApplicationProtocol.HTTP)
                .healthCheck(HealthCheck.builder()
                        .healthyHttpCodes("302")
                        .build())
                .targetGroupName("ManagerUITargetGroup")
                .port(CHAT_BOT_MANAGER_PORT)
                .targets(Collections.singletonList(new InstanceTarget(chatBotWebServer)))
                .deregistrationDelay(Duration.seconds(10))
                .build());
        ApplicationTargetGroup pgAdminTargetGroup = albListener.addTargets("alb-tg", AddApplicationTargetsProps.builder()
                .protocol(ApplicationProtocol.HTTP)
                .healthCheck(HealthCheck.builder()
                        .healthyHttpCodes("302")
                        .build())
                .targetGroupName("PgAdmin4TargetGroup")
                .port(PGADMIN_PORT)
                .targets(Collections.singletonList(new InstanceTarget(chatBotWebServer)))
                .deregistrationDelay(Duration.seconds(10))
                .build());
        albListener.addAction("webui-action", AddApplicationActionProps.builder().action(ListenerAction.forward(List.of(webuiTargetGroup)))
                .conditions(List.of(ListenerCondition.pathPatterns(List.of("/chat/*")))).build());
        albListener.addAction("manager-ui-action", AddApplicationActionProps.builder().action(ListenerAction.forward(List.of(managerTargetGroup)))
                .conditions(List.of(ListenerCondition.pathPatterns(List.of("/manage/*")))).build());
        albListener.addAction("pgadmin4-action", AddApplicationActionProps.builder().action(ListenerAction.forward(List.of(pgAdminTargetGroup)))
                .conditions(List.of(ListenerCondition.pathPatterns(List.of("/pgAdmin4/*")))).build());

//        //listener for postgresqlAdmin
//        ApplicationListener pgAdminALBListener = alb.addListener("alb-listener-pg", BaseApplicationListenerProps.builder()
//                .protocol(ApplicationProtocol.HTTP)
////                .port(8080)
//                .build());
//        ApplicationTargetGroup pgAlbTg = pgAdminALBListener.addTargets("pg-alb-tg", AddApplicationTargetsProps.builder()
//                .protocol(ApplicationProtocol.HTTP)
//                .healthCheck(HealthCheck.builder()
//                        .healthyHttpCodes("302")
//                        .build())
//                .targetGroupName("CDKManagedPGAlbTg")
//                .port(62315)
//                .targets(Collections.singletonList(new InstanceTarget(chatBotWebServer)))
//                .deregistrationDelay(Duration.seconds(10))
//                .build());
        return alb;
    }


    /**
     * 创建Aurora Serverless V2集群
     *
     * @param vpc
     * @param securityGroup
     * @param databasePassword
     * @param databaseName
     * @return CfnDBCluster
     */
    CfnDBCluster serverlessV2Cluster(IVpc vpc, SecurityGroup securityGroup, String databasePassword, String databaseName) {
        List<String> azs = vpc.getPrivateSubnets().stream().map(ISubnet::getAvailabilityZone).collect(Collectors.toList());
        CfnDBCluster.ServerlessV2ScalingConfigurationProperty serverlessV2ScalingConfigurationProperty = CfnDBCluster.ServerlessV2ScalingConfigurationProperty.builder().minCapacity(0.5).maxCapacity(1).build();

//        final Credentials credentials = Credentials.fromSecret(databaseSecret);

        CfnDBCluster cfnDBCluster = new CfnDBCluster(this, "DatabaseClusterForChatBot", CfnDBClusterProps.builder()
                .engine("aurora-postgresql")
                .engineVersion("15.3")
                .dbClusterIdentifier("chatbot-postgres-serverless-v2")
                .serverlessV2ScalingConfiguration(serverlessV2ScalingConfigurationProperty)
                .masterUsername("postgres")
                .port(DB_PORT)
//                .masterUserSecret(credentials)
                .masterUserPassword(databasePassword).databaseName(databaseName)
//                .allocatedStorage(100)
                .vpcSecurityGroupIds(Collections.singletonList(securityGroup.getSecurityGroupId())).availabilityZones(azs)

                .build());

        //
        CfnDBInstance dbInstance = new CfnDBInstance(this, "DatabaseInstanceWriter", CfnDBInstanceProps.builder()
//                .port("5432")
                .dbClusterIdentifier(cfnDBCluster.getDbClusterIdentifier()).dbInstanceClass("db.serverless").dbInstanceIdentifier("writer-instance").engine("aurora-postgresql").build());

        dbInstance.addDependency(cfnDBCluster);

        return cfnDBCluster;
    }
}
