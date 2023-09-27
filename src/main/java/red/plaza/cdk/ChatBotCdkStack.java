package red.plaza.cdk;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.iotsitewise.CfnAccessPolicy;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.rds.InstanceProps;
import software.constructs.Construct;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.IConstruct;

import javax.swing.plaf.synth.Region;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;


import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.ManagedPolicy;

/**
 * cdk synth --debug -vvv --parameters ec2KeyPair=us-east-1 --parameters vpcId="vpc-08cd91d07ef1cfbfa"
 */
public class ChatBotCdkStack extends Stack {

    private final static int DB_PORT = 5432;
    private final static int CAHT_BOT_PORT = 7860;

    private final static int PGADMIN_PORT = 62315;

    public ChatBotCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public ChatBotCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
//        CfnParameter vpcParam = CfnParameter.Builder.create(this, "VpcParam").type("AWS::EC2::VPC::Id").build();

        // 定义实例类型参数
        CfnParameter instanceTypeParameter = CfnParameter.Builder.create(this, "InstanceType")
                .type("String")
                .description("EC2 instance type (Graviton will be cost effective)")
                .defaultValue("t4g.micro").build();
        // The code that defines your stack goes here
        // Parameters
        CfnParameter ec2KeyPairName = CfnParameter.Builder.create(this, "ec2KeyPair").type("String").description("EC2 Key Pair name").build();

        CfnParameter databaseMasterPassword = CfnParameter.Builder.create(this, "databasePassword").type("String").description("Database master password").build();
//
        CfnParameter pgAdmin4UserName = CfnParameter.Builder.create(this, "pgAdmin4UserName").type("String").description("pgAdmin4 User name").build();

        CfnParameter pgAdmin4Password = CfnParameter.Builder.create(this, "pgAdmin4Password").type("String").description("pgAdmin4 Password").build();

        CfnParameter pgDatabaseName = CfnParameter.Builder.create(this, "pgDatabaseName").type("String").description("Database name").build();

        CfnParameter openaiKeyParam = CfnParameter.Builder.create(this, "openaiKeyParam").type("String").description("OpenAI key, can be changed after initialized").build();
        //Resources
        IVpc vpc = Vpc.fromLookup(this, "vpc", VpcLookupOptions.builder().isDefault(true).build());


        SecurityGroup chatBotServerSecurityGroup = SecurityGroup.Builder.create(this, "ChatBotServerSecurityGroup").vpc(vpc).description("Allow ssh access to ec2 instances").allowAllOutbound(true).build();
        chatBotServerSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(22), "allow ssh access from the world");
        chatBotServerSecurityGroup.addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(DB_PORT), "allow to access the database chatBotWebServer");
        chatBotServerSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(PGADMIN_PORT), "PGAdmin4 export");
        chatBotServerSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(443), "allow to access the database chatBotWebServer");
        chatBotServerSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(CAHT_BOT_PORT), "gradio export to be accessed");


        SecurityGroup chatBotRDSSecurityGroup = SecurityGroup.Builder.create(this, "ChatBotRDSSecurityGroup").vpc(vpc).description("Allow EC2 to connect pg").allowAllOutbound(true).build();
        chatBotRDSSecurityGroup.addIngressRule(Peer.securityGroupId(chatBotServerSecurityGroup.getSecurityGroupId()), Port.tcp(DB_PORT), "allow web service to access postgresql");

        CfnDBCluster dbCluster = serverlessV2Cluster(vpc, chatBotRDSSecurityGroup, databaseMasterPassword.getValueAsString(), pgDatabaseName.getValueAsString());
        dbCluster.applyRemovalPolicy(RemovalPolicy.RETAIN);
//

        //create sagemaker role
        final Object jsonPolicy = new ResourceAsJsonReader().readResourceAsJsonObjects("policy.json");
        Role sagemakerRole = Role.Builder.create(this, "SagemakerRole")
                .assumedBy(new ServicePrincipal("sagemaker.amazonaws.com"))
//                .managedPolicies(
//                        Arrays.asList(ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonSageMaker-ExecutionPolicy")))
                .inlinePolicies(Map.of("SageMakerAccessPolicy", PolicyDocument.fromJson(jsonPolicy)))
                .roleName("ChatBotCdkStack-SagemakerRole")
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


        Instance chatBotWebServer = Instance.Builder.create(this, "ChatBotWebServerV2-t4g")
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build()) //放在公有子网 需要访问openapi或者其他外部网页数据
                .machineImage(MachineImage.latestAmazonLinux2023(AmazonLinux2023ImageSsmParameterProps.builder()
                        .cpuType(AmazonLinuxCpuType.ARM_64).build()))
                .securityGroup(chatBotServerSecurityGroup)
                .keyName(ec2KeyPairName.getValueAsString())
                .userData(UserData.custom(finalUserData))
                .instanceType(new InstanceType(instanceTypeParameter.getValueAsString()))
                .build();

        chatBotWebServer.getNode().addDependency(dbCluster);
//        chatBotWebServer.
//        CfnInstance cfnInstance = new CfnInstance(this, "CfnIn")

//        //Outputs
//        CfnOutput.Builder.create(this, "database-arn").value(dbCluster.getAttrDbClusterArn()).build();
//
        CfnOutput.Builder.create(this, "database-endpoint").value(dbCluster.getAttrEndpointAddress()).build();

        CfnOutput.Builder.create(this, "database").value(pgDatabaseName.getValueAsString()).build();
        CfnOutput.Builder.create(this, "username").value("postgres¬").build();
        CfnOutput.Builder.create(this, "database password").value(databaseMasterPassword.getValueAsString()).build();


        CfnOutput.Builder.create(this, "pgAdmin4-url").value("http://" + chatBotWebServer.getInstancePublicIp() + ":" + PGADMIN_PORT).build();

        CfnOutput.Builder.create(this, "pg4Admin username and password").value(pgAdmin4UserName + " --- " + pgAdmin4Password).build();

        CfnOutput.Builder.create(this, "SageMaker Role ARN").value(sagemakerRole.getRoleArn()).build();


        CfnOutput.Builder.create(this, "chatbot-url").value("http://" + chatBotWebServer.getInstancePublicIp() + ":" + CAHT_BOT_PORT).description("ChatBot endpoint").build();
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
